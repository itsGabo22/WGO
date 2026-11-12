package com.wgo.navigator.voice

import android.os.SystemClock
import com.wgo.navigator.Language
import com.wgo.navigator.ai.AiCallBudget
import com.wgo.navigator.ai.PlaceAliases
import com.wgo.navigator.core.Outcome
import com.wgo.navigator.core.WgoFailure
import com.wgo.navigator.metrics.WgoMetrics
import com.wgo.navigator.net.HttpClient
import com.wgo.navigator.net.HttpResponse
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Cloud speech-to-text over an OpenAI-compatible `audio/transcriptions` endpoint (P9; default Groq, the same provider
 * and key as the language model). It sends ONE in-memory WAV and these fields only: model, language, temperature 0,
 * verbose_json and a vocabulary prompt of public landmark names ([PlaceAliases.landmarkNames]); never coordinates,
 * identifiers, my places or history. Connect 3 s, total 6 s per attempt; one retry only when the connection itself
 * failed early; 429 starts the [budget]'s 60 s cool-down; the [budget]'s daily cap (300) is checked before sending.
 * The result goes through [TranscriptGuard]. Logged under `WgoVoice`: sizes, codes, times and lengths only.
 */
class TranscriptionClient(
    private val apiKey: String,
    baseUrl: String,
    val model: String,
    private val http: HttpClient,
    private val budget: AiCallBudget,
    private val connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
    private val totalTimeoutMs: Long = TOTAL_TIMEOUT_MS,
    private val retryWindowMs: Long = RETRY_WINDOW_MS,
    private val now: () -> Long = ::elapsed
) : Transcriber {
    private val endpoint = baseUrl.trimEnd('/') + "/audio/transcriptions"
    private val warmUrl = baseUrl.trimEnd('/') + "/models"

    /** A keyless GET to the same host (it answers 401): DNS, TLS setup and session resumption are ready for the upload. */
    override suspend fun prewarm() {
        if (apiKey.isBlank()) return
        val t0 = now()
        val r = http.request(WgoMetrics.Net.STT, warmUrl, connectTimeoutMs, PREWARM_READ_MS)
        VoiceLog.event("stt_prewarm code=${(r as? Outcome.Ok)?.value?.code ?: (r as Outcome.Err).failure} ms=${now() - t0}")
    }

    override suspend fun transcribe(wav: ByteArray, language: Language): Transcription {
        if (apiKey.isBlank()) return Transcription.Failed(WgoFailure.Unavailable, "no_key")
        if (!budget.tryAcquire()) {
            val why = if (budget.coolingDown()) "cooldown" else "daily_cap"
            VoiceLog.event("stt_skip reason=$why")
            return Transcription.Failed(WgoFailure.Unavailable, why)
        }
        val prompt = promptFor(language)
        val boundary = "wgo" + UUID.randomUUID().toString().replace("-", "")
        val body = multipart(boundary, wav, language, prompt)
        try {
            val started = now()
            var attempt = 0
            while (true) {
                attempt++
                val t0 = now()
                VoiceLog.event("stt_request attempt=$attempt bytes=${body.size} audio_bytes=${wav.size} model=$model")
                val outcome = withTimeoutOrNull(totalTimeoutMs) {
                    http.request(
                        kind = WgoMetrics.Net.STT,
                        url = endpoint,
                        connectTimeoutMs = connectTimeoutMs,
                        readTimeoutMs = totalTimeoutMs.toInt(),
                        method = "POST",
                        headers = mapOf(
                            "Authorization" to "Bearer $apiKey",
                            "Content-Type" to "multipart/form-data; boundary=$boundary"
                        ),
                        bodyBytes = body
                    )
                }
                val ms = now() - t0
                when (outcome) {
                    null -> {
                        VoiceLog.event("stt_response code=timeout ms=$ms")
                        return Transcription.Failed(WgoFailure.Timeout, "total_timeout")
                    }
                    is Outcome.Err -> {
                        VoiceLog.event("stt_response code=${outcome.failure} ms=$ms")
                        // One retry, only when the connection itself failed early (never after a slow answer).
                        if (outcome.failure == WgoFailure.Offline && attempt == 1 && now() - started < retryWindowMs) {
                            VoiceLog.event("stt_retry")
                            continue
                        }
                        return Transcription.Failed(outcome.failure, "network")
                    }
                    is Outcome.Ok -> return answer(outcome.value, ms, prompt)
                }
            }
        } finally {
            body.fill(0) // the audio copy inside the request body
        }
    }

    private fun answer(response: HttpResponse, ms: Long, prompt: String): Transcription {
        val code = response.code
        if (code == 429 || (code >= 400 && response.body.contains("rate_limit", ignoreCase = true))) {
            budget.onRateLimited()
            VoiceLog.event("stt_response code=$code ms=$ms rate_limited=true cooldown_ms=${AiCallBudget.COOLDOWN_MS}")
            return Transcription.Failed(WgoFailure.Server(429), "rate_limited")
        }
        if (code !in 200..299) {
            VoiceLog.event("stt_response code=$code ms=$ms")
            return Transcription.Failed(if (code == 401 || code == 403) WgoFailure.Unavailable else WgoFailure.Server(code), "http")
        }
        val parsed = parse(response.body)
        if (parsed == null) {
            VoiceLog.event("stt_response code=$code ms=$ms parse=failed down=${response.body.length}")
            return Transcription.Failed(WgoFailure.Server(code), "bad_json")
        }
        val (text, segments) = parsed
        val nsp = segments.maxOfOrNull { it.noSpeechProb } ?: -1.0
        val alp = if (segments.isEmpty()) 0.0 else segments.map { it.avgLogprob }.average()
        VoiceLog.event(
            "stt_response code=$code ms=$ms len=${text.length} segs=${segments.size} " +
                "nsp_max=${"%.2f".format(java.util.Locale.ROOT, nsp)} alp_mean=${"%.2f".format(java.util.Locale.ROOT, alp)}"
        )
        TranscriptGuard.reject(text, segments, prompt)?.let { reason ->
            VoiceLog.event("stt_guard rejected=$reason len=${text.length}")
            return Transcription.Failed(WgoFailure.NotFound, "guard_$reason")
        }
        return Transcription.Text(clean(text))
    }

    private fun multipart(boundary: String, wav: ByteArray, language: Language, prompt: String): ByteArray {
        val out = ByteArrayOutputStream(wav.size + 1024)
        fun text(s: String) = out.write(s.toByteArray(Charsets.UTF_8))
        fun field(name: String, value: String) =
            text("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n")
        field("model", model)
        field("language", language.name.lowercase())
        field("temperature", "0")
        field("response_format", "verbose_json")
        field("prompt", prompt)
        text("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"utterance.wav\"\r\nContent-Type: audio/wav\r\n\r\n")
        out.write(wav)
        text("\r\n--$boundary--\r\n")
        return out.toByteArray()
    }

    companion object {
        const val DEFAULT_MODEL = "whisper-large-v3"
        const val CONNECT_TIMEOUT_MS = 3_000
        const val TOTAL_TIMEOUT_MS = 6_000L
        const val RETRY_WINDOW_MS = 3_000L
        const val DAILY_CAP = 300
        private const val PREWARM_READ_MS = 3_000
        private const val MAX_TEXT_CHARS = 200

        private fun elapsed(): Long = try {
            SystemClock.elapsedRealtime()
        } catch (e: RuntimeException) {
            System.nanoTime() / 1_000_000 // JVM unit tests
        }

        /** The vocabulary hint: the region and the verified public landmark names, nothing else. */
        fun promptFor(language: Language): String {
            val region = if (language == Language.ES) "Pasto, Nariño." else "Pasto, Nariño, Colombia."
            return region + " " + PlaceAliases.landmarkNames.joinToString(", ") + "."
        }

        /** The text and the segments of a verbose_json answer, or null when it is not one. */
        fun parse(body: String): Pair<String, List<TranscriptGuard.Segment>>? = try {
            val root = JSONObject(body)
            val text = root.opt("text") as? String ?: return null
            val array = root.optJSONArray("segments")
            val segments = (0 until (array?.length() ?: 0)).mapNotNull { i ->
                val s = array!!.optJSONObject(i) ?: return@mapNotNull null
                TranscriptGuard.Segment(
                    start = s.optDouble("start", 0.0),
                    end = s.optDouble("end", 0.0),
                    noSpeechProb = s.optDouble("no_speech_prob", 0.0).takeUnless { it.isNaN() } ?: 0.0,
                    avgLogprob = s.optDouble("avg_logprob", 0.0).takeUnless { it.isNaN() } ?: 0.0,
                    compressionRatio = s.optDouble("compression_ratio", 0.0).takeUnless { it.isNaN() } ?: 0.0
                )
            }
            text to segments
        } catch (e: JSONException) {
            null
        }

        /** Like a recognizer's final text: one line, no surrounding punctuation, capped. */
        fun clean(text: String): String =
            text.replace(Regex("\\s+"), " ").trim().trim('.', ',', '!', '?', '¡', '¿', ';', ':', '"', ' ').take(MAX_TEXT_CHARS).trim()
    }
}

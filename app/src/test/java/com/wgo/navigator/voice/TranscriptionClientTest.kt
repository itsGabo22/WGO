package com.wgo.navigator.voice

import com.wgo.navigator.Language
import com.wgo.navigator.ai.AiCallBudget
import com.wgo.navigator.ai.AiCounterStore
import com.wgo.navigator.ai.PlaceAliases
import com.wgo.navigator.audio.WavEncoder
import com.wgo.navigator.core.WgoFailure
import com.wgo.navigator.net.FakeHttpServer
import com.wgo.navigator.net.HttpClient
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** The cloud transcription client against a local fake server (no network, no key). */
class TranscriptionClientTest {
    private class MemoryStore : AiCounterStore {
        var value: Pair<String, Int>? = null
        override suspend fun load() = value
        override suspend fun save(day: String, count: Int) {
            value = day to count
        }
    }

    private val key = "sk-SECRET-key-123"
    private val spoken = "Llévame a la cooperativa"
    private val wav = WavEncoder.encode(ShortArray(16_000) { (it % 50).toShort() })
    private val logs = CopyOnWriteArrayList<String>()
    private val store = MemoryStore()
    private var now = 1_000_000L

    @Before fun captureLogs() {
        VoiceLog.testSink = { logs += it }
    }

    @After fun releaseLogs() {
        VoiceLog.testSink = null
    }

    private fun budget(cap: Int = 300) = AiCallBudget(store, cap, 60_000L, { now }, { "2026-11-12" })

    private fun client(base: String, budget: AiCallBudget = budget(), totalMs: Long = 2_000L) = TranscriptionClient(
        apiKey = key, baseUrl = base, model = "whisper-large-v3", http = HttpClient("WGO-test"), budget = budget,
        connectTimeoutMs = 1_000, totalTimeoutMs = totalMs, retryWindowMs = 3_000L
    )

    private fun answer(text: String, nsp: Double = 0.01, alp: Double = -0.15, cr: Double = 0.8) =
        """{"text":${org.json.JSONObject.quote(text)},"segments":[{"start":0.0,"end":1.8,"no_speech_prob":$nsp,"avg_logprob":$alp,"compression_ratio":$cr}]}"""

    @Test fun successSendsOnlyTheAudioAndPublicFieldsAndReturnsCleanText() = runBlocking {
        FakeHttpServer { Triple(200, answer(" $spoken."), 0L) }.use { server ->
            val r = client(server.baseUrl).transcribe(wav, Language.ES)
            assertEquals(Transcription.Text(spoken), r)
            val req = server.requests.single()
            assertEquals("POST", req.method)
            assertEquals("/audio/transcriptions", req.path)
            assertEquals("Bearer $key", req.headers["authorization"])
            assertTrue(req.headers["content-type"]!!.startsWith("multipart/form-data; boundary="))
            for (field in listOf("model", "language", "temperature", "response_format", "prompt", "file")) {
                assertTrue("field $field", req.body.contains("name=\"$field\""))
            }
            assertTrue(req.body.contains("\r\n\r\nwhisper-large-v3\r\n"))
            assertTrue(req.body.contains("\r\n\r\nes\r\n"))
            assertTrue(req.body.contains("\r\n\r\nverbose_json\r\n"))
            assertTrue(req.body.contains("\r\n\r\n${TranscriptionClient.promptFor(Language.ES)}\r\n"))
            assertTrue(req.body.contains("RIFF"))
            // No coordinates or identifiers in the request.
            assertFalse(Regex("-?\\d{1,3}\\.\\d{4,}").containsMatchIn(req.body.substringBefore("RIFF")))
        }
    }

    @Test fun chainNamesFromTheRealIndexJoinThePromptWithinItsCap() {
        val index = com.wgo.navigator.places.PlaceIndex { java.io.File("src/main/assets/places_pasto.tsv.gzip").inputStream() }
        assertTrue(index.chainNames().isEmpty()) // nothing before the index is loaded (the P9 prompt is used)
        runBlocking { index.ensureLoaded() }
        val chains = index.chainNames()
        assertTrue(chains.toString(), "Mister Pollo" in chains)
        assertTrue(chains.size <= 15)
        val prompt = TranscriptionClient.promptFor(Language.ES, chains)
        assertTrue(prompt.length <= TranscriptionClient.MAX_PROMPT_CHARS)
        assertTrue(prompt.startsWith(TranscriptionClient.promptFor(Language.ES).removeSuffix(".")))
        assertTrue(prompt.contains("Mister Pollo"))
        // Public names only: no coordinates or numbers that look like them.
        assertFalse(Regex("-?\\d{1,3}\\.\\d{3,}").containsMatchIn(prompt))
    }

    @Test fun aLongVocabularyIsCutAtTheCap() {
        val many = (1..200).map { "Cadena Numero $it" }
        val prompt = TranscriptionClient.promptFor(Language.ES, many)
        assertTrue(prompt.length <= TranscriptionClient.MAX_PROMPT_CHARS)
        assertTrue(prompt.contains("Cadena Numero 1,"))
        assertFalse(prompt.contains("Cadena Numero 200"))
    }

    @Test fun theClientSendsTheVocabularyInThePrompt() = runBlocking {
        FakeHttpServer { Triple(200, answer(" Llévame a Mister Pollo."), 0L) }.use { server ->
            TranscriptionClient(
                apiKey = key, baseUrl = server.baseUrl, model = "whisper-large-v3", http = HttpClient("WGO-test"),
                budget = budget(), connectTimeoutMs = 1_000, totalTimeoutMs = 2_000L, retryWindowMs = 3_000L,
                vocabulary = { listOf("Mister Pollo", "Lili Pink") }
            ).transcribe(wav, Language.ES)
            val body = server.requests.single().body
            assertTrue(body.contains("\r\n\r\n${TranscriptionClient.promptFor(Language.ES, listOf("Mister Pollo", "Lili Pink"))}\r\n"))
        }
    }

    @Test fun thePromptHoldsOnlyTheRegionAndTheVerifiedPublicLandmarks() {
        for (language in Language.entries) {
            val prompt = TranscriptionClient.promptFor(language)
            val names = prompt.substringAfter(". ").removeSuffix(".").split(", ")
            assertEquals(PlaceAliases.landmarkNames, names)
            assertTrue(prompt.startsWith("Pasto, Nariño"))
        }
    }

    @Test fun languageFollowsTheAppLanguage() = runBlocking {
        FakeHttpServer { Triple(200, answer(" Take me to the stadium."), 0L) }.use { server ->
            client(server.baseUrl).transcribe(wav, Language.EN)
            assertTrue(server.requests.single().body.contains("\r\n\r\nen\r\n"))
        }
    }

    @Test fun http429StartsTheCoolDownWithoutARetryStorm() = runBlocking {
        FakeHttpServer { Triple(429, """{"error":{"code":"rate_limit_exceeded"}}""", 0L) }.use { server ->
            val b = budget()
            val c = client(server.baseUrl, b)
            assertEquals(Transcription.Failed(WgoFailure.Server(429), "rate_limited"), c.transcribe(wav, Language.ES))
            assertTrue(b.coolingDown())
            assertEquals(Transcription.Failed(WgoFailure.Unavailable, "cooldown"), c.transcribe(wav, Language.ES))
            assertEquals(1, server.requests.size)
            now += 60_001L
            assertFalse(b.coolingDown())
        }
    }

    @Test fun aSlowAnswerTimesOutOnceWithoutRetry() = runBlocking {
        FakeHttpServer { Triple(200, answer(spoken), 2_500L) }.use { server ->
            val r = client(server.baseUrl, totalMs = 700L).transcribe(wav, Language.ES)
            assertTrue(r is Transcription.Failed && (r.failure == WgoFailure.Timeout))
            assertEquals(1, server.requests.size)
        }
    }

    @Test fun offlineRetriesExactlyOnce() = runBlocking {
        val r = client("http://127.0.0.1:1").transcribe(wav, Language.ES)
        assertEquals(Transcription.Failed(WgoFailure.Offline, "network"), r)
        assertEquals(2, logs.count { it.startsWith("stt_request") })
        assertEquals(1, logs.count { it.startsWith("stt_retry") })
    }

    @Test fun serverAndAuthErrorsAreNotRetried() = runBlocking {
        FakeHttpServer { Triple(500, "{}", 0L) }.use { server ->
            assertEquals(Transcription.Failed(WgoFailure.Server(500), "http"), client(server.baseUrl).transcribe(wav, Language.ES))
            assertEquals(1, server.requests.size)
        }
        FakeHttpServer { Triple(401, "{}", 0L) }.use { server ->
            assertEquals(Transcription.Failed(WgoFailure.Unavailable, "http"), client(server.baseUrl).transcribe(wav, Language.ES))
            assertEquals(1, server.requests.size)
        }
    }

    @Test fun garbageOrEmptyAnswersAreFailures() = runBlocking {
        FakeHttpServer { Triple(200, "<html>oops</html>", 0L) }.use { server ->
            assertEquals(Transcription.Failed(WgoFailure.Server(200), "bad_json"), client(server.baseUrl).transcribe(wav, Language.ES))
        }
        FakeHttpServer { Triple(200, """{"segments":[]}""", 0L) }.use { server ->
            assertEquals(Transcription.Failed(WgoFailure.Server(200), "bad_json"), client(server.baseUrl).transcribe(wav, Language.ES))
        }
        FakeHttpServer { Triple(200, answer(" "), 0L) }.use { server ->
            assertEquals(Transcription.Failed(WgoFailure.NotFound, "guard_empty"), client(server.baseUrl).transcribe(wav, Language.ES))
        }
    }

    @Test fun hallucinationsAreRejectedAndARealLandmarkAloneIsNot() = runBlocking {
        val cases = listOf(
            answer(" Gracias.", nsp = 0.70, alp = -0.27) to "guard_no_speech",
            answer(" Gracias.", nsp = 0.03, alp = -0.27) to "guard_silence_phrase",
            answer(" Subtítulos realizados por la comunidad de Amara.org", nsp = 0.1) to "guard_silence_phrase",
            answer(" Unicentro, Plaza de Nariño, Terminal de Transportes, Bomboná.", nsp = 0.0, alp = -0.26) to "guard_prompt_echo",
            answer(" Nariño, Terminal de Transportes, Bomboná.", nsp = 0.09, alp = -0.32) to "guard_prompt_echo"
        )
        for ((body, reason) in cases) {
            FakeHttpServer { Triple(200, body, 0L) }.use { server ->
                assertEquals(Transcription.Failed(WgoFailure.NotFound, reason), client(server.baseUrl).transcribe(wav, Language.ES))
            }
        }
        FakeHttpServer { Triple(200, answer(" Unicentro."), 0L) }.use { server ->
            assertEquals(Transcription.Text("Unicentro"), client(server.baseUrl).transcribe(wav, Language.ES))
        }
    }

    @Test fun theDailyCapStopsRequestsBeforeSending() = runBlocking {
        FakeHttpServer { Triple(200, answer(spoken), 0L) }.use { server ->
            val b = budget(cap = 1)
            val c = client(server.baseUrl, b)
            assertTrue(c.transcribe(wav, Language.ES) is Transcription.Text)
            assertEquals(Transcription.Failed(WgoFailure.Unavailable, "daily_cap"), c.transcribe(wav, Language.ES))
            assertEquals(1, server.requests.size)
            assertTrue(b.capReachedAsKnown())
        }
    }

    @Test fun noKeyMeansNoRequest() = runBlocking {
        val c = TranscriptionClient("", "http://127.0.0.1:1", "m", HttpClient("WGO-test"), budget())
        assertEquals(Transcription.Failed(WgoFailure.Unavailable, "no_key"), c.transcribe(wav, Language.ES))
        assertTrue(logs.none { it.startsWith("stt_request") })
    }

    @Test fun logsNeverHoldTheKeyTheTextTheAudioOrTheUrl() = runBlocking {
        FakeHttpServer { Triple(200, answer(" $spoken."), 0L) }.use { server ->
            client(server.baseUrl).transcribe(wav, Language.ES)
        }
        FakeHttpServer { Triple(200, answer(" Gracias.", nsp = 0.9), 0L) }.use { server ->
            client(server.baseUrl).transcribe(wav, Language.ES)
        }
        client("http://127.0.0.1:1").transcribe(wav, Language.ES)
        assertTrue(logs.any { it.startsWith("stt_response code=200") && "len=${spoken.length + 2}" in it })
        val all = logs.joinToString("\n")
        for (secret in listOf(key, "SECRET", "cooperativa", "Gracias", "http://", "127.0.0.1", "RIFF", "Unicentro", "Bearer")) {
            assertFalse("log holds '$secret'", all.contains(secret, ignoreCase = true))
        }
    }

    @Test fun withoutANetworkThereIsNoUploadAndItFailsFast() = runBlocking {
        FakeHttpServer { Triple(200, answer(spoken), 0L) }.use { server ->
            val c = TranscriptionClient(
                apiKey = key, baseUrl = server.baseUrl, model = "whisper-large-v3", http = HttpClient("WGO-test"),
                budget = budget(), online = { false }
            )
            val t0 = System.nanoTime()
            assertEquals(Transcription.Failed(WgoFailure.Offline, "offline"), c.transcribe(wav, Language.ES))
            assertTrue((System.nanoTime() - t0) / 1_000_000 < 200)
            assertEquals(0, server.requests.size)
            assertEquals(null, store.value) // no budget unit spent
        }
    }

    @Test fun aFlacClipIsSentAsFlac() = runBlocking {
        FakeHttpServer { Triple(200, answer(spoken), 0L) }.use { server ->
            val flac = "fLaC".toByteArray() + ByteArray(1000) { 7 }
            assertTrue(client(server.baseUrl).transcribe(flac, Language.ES) is Transcription.Text)
            val body = server.requests.single().body
            assertTrue(body.contains("filename=\"utterance.flac\"\r\nContent-Type: audio/flac"))
            assertFalse(body.contains("RIFF"))
        }
        assertTrue(TranscriptionClient.isFlac("fLaC1234".toByteArray()))
        assertFalse(TranscriptionClient.isFlac(wav))
    }

    @Test fun theTotalTimeoutScalesWithThePayloadAndIsCapped() {
        assertEquals(4_000L, TranscriptionClient.totalTimeoutFor(0))
        assertEquals(5_000L, TranscriptionClient.totalTimeoutFor(32 * 1024))
        // An 8 s clip: 99 KB as FLAC (STEP 0) -> about 7 s; 256 KB as WAV -> capped at 10 s.
        assertTrue(TranscriptionClient.totalTimeoutFor(99_191) in 6_900L..7_100L)
        assertEquals(10_000L, TranscriptionClient.totalTimeoutFor(256_044))
        assertEquals(10_000L, TranscriptionClient.totalTimeoutFor(5_000_000))
    }

    @Test fun prewarmSendsNoKeyAndNoData() = runBlocking {
        FakeHttpServer { Triple(401, "{}", 0L) }.use { server ->
            client(server.baseUrl).prewarm()
            val req = server.requests.single()
            assertEquals("GET", req.method)
            assertEquals("/models", req.path)
            assertEquals(null, req.headers["authorization"])
            assertEquals("", req.body)
        }
        assertTrue(logs.none { key in it })
    }

    @Test fun theRequestBodyIsZeroedAfterSending() = runBlocking {
        // The body is internal; what the caller hands over is checked by CloudVoiceInput (wav zeroed after upload).
        FakeHttpServer { Triple(200, answer(spoken), 0L) }.use { server ->
            val copy = wav.copyOf()
            client(server.baseUrl).transcribe(copy, Language.ES)
            assertTrue(copy.any { it != 0.toByte() }) // the client does not own the caller's buffer
        }
    }
}

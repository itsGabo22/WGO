package com.wgo.navigator.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.wgo.navigator.Language
import com.wgo.navigator.core.VoiceEvent
import com.wgo.navigator.core.VoiceInput
import com.wgo.navigator.core.WgoFailure
import com.wgo.navigator.core.speechTag
import com.wgo.navigator.metrics.WgoMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Speech to text with the in-app [SpeechRecognizer] (strategy 1). The recognizer is created on demand when [listen]
 * is collected and destroyed on every end: final result, error, [cancel], or the collector going away (Back, onStop).
 * Caps: [MAX_LISTEN_MS] of listening in total and [SILENCE_AFTER_SPEECH_MS] of silence once speech started; after a
 * cap the recognizer is asked to stop and the best transcript so far is used. Offline recognition is preferred; a
 * missing offline language model triggers one automatic online retry within the same session.
 */
class InAppVoiceInput(
    context: Context,
    private val now: () -> Long = { SystemClock.elapsedRealtime() }
) : VoiceInput {
    private val app = context.applicationContext

    @Volatile
    private var session: ProducerScope<VoiceEvent>? = null

    /** Languages whose offline model turned out to be missing: later sessions go online at once (saves ~2 s). */
    private val noOfflineModel = java.util.Collections.synchronizedSet(HashSet<Language>())

    override fun cancel() {
        session?.close()
    }

    override fun listen(language: Language): Flow<VoiceEvent> = callbackFlow {
        session = this
        val started = now()
        val smoother = LevelSmoother()
        var recognizer: SpeechRecognizer? = null
        var online = language in noOfflineModel
        var instances = 0
        WgoMetrics.inc(WgoMetrics.Counter.RECOGNIZER_SESSION)
        var ready = false
        var speechStarted = false
        var lastVoice = started
        var lastPartial: String? = null
        var stopAt = 0L
        var finished = false

        fun finish(event: VoiceEvent) {
            if (finished) return
            finished = true
            trySend(event)
            close()
        }

        fun begin() {
            recognizer?.destroy()
            val r = SpeechRecognizer.createSpeechRecognizer(app)
            instances++
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (!ready) trySend(VoiceEvent.Ready)
                    ready = true
                }

                override fun onBeginningOfSpeech() {
                    speechStarted = true
                    lastVoice = now()
                }

                override fun onRmsChanged(rmsdB: Float) {
                    val level = smoother.push(rmsdB)
                    if (level >= VOICE_LEVEL) lastVoice = now()
                    trySend(VoiceEvent.Level(level))
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val text = best(partialResults) ?: return
                    if (text != lastPartial) lastVoice = now()
                    lastPartial = text
                    trySend(VoiceEvent.Partial(text))
                }

                override fun onResults(results: Bundle?) {
                    val text = best(results) ?: lastPartial
                    finish(if (text.isNullOrBlank()) VoiceEvent.Failed(WgoFailure.NotFound) else VoiceEvent.Final(text))
                }

                override fun onError(error: Int) {
                    WgoMetrics.event("voice_error code=$error ready=$ready online=$online")
                    when {
                        !online && error in LANGUAGE_MODEL_ERRORS -> {
                            online = true
                            noOfflineModel += language
                            begin()
                        }
                        error in NO_SPEECH_ERRORS && !lastPartial.isNullOrBlank() -> finish(VoiceEvent.Final(lastPartial!!))
                        else -> finish(VoiceEvent.Failed(failureFor(error)))
                    }
                }

                override fun onEndOfSpeech() = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            r.startListening(recognizerIntent(language, preferOffline = !online))
            recognizer = r
        }

        begin()

        // Caps: total listening time and trailing silence. After stopListening() the recognizer normally answers with
        // onResults within a few hundred ms; if it does not, the best partial (or "not understood") ends the session.
        val watchdog = launch {
            while (isActive && !finished) {
                delay(WATCHDOG_TICK_MS)
                val t = now()
                if (stopAt == 0L) {
                    val overTime = t - started >= MAX_LISTEN_MS
                    val silent = speechStarted && t - lastVoice >= SILENCE_AFTER_SPEECH_MS
                    if (overTime || silent) {
                        WgoMetrics.event("voice_stop reason=${if (overTime) "cap" else "silence"} after_ms=${t - started}")
                        stopAt = t
                        recognizer?.stopListening()
                    }
                } else if (t - stopAt >= STOP_GRACE_MS) {
                    val partial = lastPartial
                    finish(if (partial.isNullOrBlank()) VoiceEvent.Failed(WgoFailure.NotFound) else VoiceEvent.Final(partial))
                }
            }
        }

        awaitClose {
            watchdog.cancel()
            val t0 = now()
            recognizer?.cancel()
            recognizer?.destroy()
            recognizer = null
            if (session === this) session = null
            WgoMetrics.event(
                "voice_destroyed session_ms=${t0 - started} destroy_ms=${now() - t0} instances=$instances online=$online"
            )
        }
    }.flowOn(Dispatchers.Main.immediate) // SpeechRecognizer must be created and driven on the main thread

    private fun recognizerIntent(language: Language, preferOffline: Boolean) =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.speechTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language.speechTag)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_AFTER_SPEECH_MS)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, app.packageName)
        }

    companion object {
        const val MAX_LISTEN_MS = 8_000L
        const val SILENCE_AFTER_SPEECH_MS = 2_000L
        private const val STOP_GRACE_MS = 1_200L
        private const val WATCHDOG_TICK_MS = 100L
        private const val VOICE_LEVEL = 0.35f

        // Literal values (API 31+ constants) so minSdk 30 code needs no version guard: LANGUAGE_NOT_SUPPORTED,
        // LANGUAGE_UNAVAILABLE.
        private val LANGUAGE_MODEL_ERRORS = setOf(12, 13)
        private val NO_SPEECH_ERRORS = setOf(SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT)

        /** Maps a [SpeechRecognizer] error code to the app's failure types. */
        fun failureFor(error: Int): WgoFailure = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> WgoFailure.NotFound
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> WgoFailure.Offline
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> WgoFailure.NoPermission
            SpeechRecognizer.ERROR_SERVER -> WgoFailure.Server(0)
            10 -> WgoFailure.Server(429) // TOO_MANY_REQUESTS (API 31)
            else -> WgoFailure.Unavailable // client, busy, audio, server disconnected, language not supported
        }

        private fun best(bundle: Bundle?): String? =
            bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull { it.isNotBlank() }?.trim()

        /**
         * Strategy 1 is usable: a recognition service exists AND one is selected (Galaxy Watch4/5 report a service but
         * none selected, which makes the recognizer fail with "no selected voice recognition service").
         */
        fun isAvailable(context: Context): Boolean {
            val available = SpeechRecognizer.isRecognitionAvailable(context)
            val selected = Settings.Secure.getString(context.contentResolver, "voice_recognition_service")
            WgoMetrics.event("voice_inapp available=$available selected=${!selected.isNullOrEmpty()}")
            return available && !selected.isNullOrEmpty()
        }
    }
}

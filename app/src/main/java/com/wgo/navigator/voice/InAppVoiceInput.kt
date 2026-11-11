package com.wgo.navigator.voice

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.speech.SpeechRecognizer
import com.wgo.navigator.Language
import com.wgo.navigator.core.VoiceEvent
import com.wgo.navigator.core.VoiceInput
import com.wgo.navigator.core.WgoFailure
import com.wgo.navigator.metrics.WgoMetrics
import com.wgo.navigator.voice.RecognizerErrors.Action
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Speech to text with the in-app recognizer (strategy 1). A recognizer instance is created on demand when [listen] is
 * collected and destroyed on every end: final result, error, [cancel], or the collector going away (Back, onStop).
 * Never two instances at once: an instance is destroyed before the next one is created.
 *
 * Errors follow [RecognizerErrors]: only network codes mean "no internet" (after one silent retry); a missing language
 * model moves the session online and through the language tags (the tag that heard speech is remembered for the
 * process); a busy, disconnected or unbinding service is recreated once, [RecognizerErrors.RESTART_DELAY_MS] after the
 * old instance was destroyed. Nothing restarts once speech was heard. Caps: [START_TIMEOUT_MS] to get ready (a cold recognizer process takes about 4 s), then [MAX_LISTEN_MS] of
 * listening and [SILENCE_AFTER_SPEECH_MS] of silence once speech started; after a listening cap the recognizer is
 * asked to stop and the best transcript so far is used.
 */
class InAppVoiceInput(
    private val newRecognizer: () -> Recognizer,
    private val now: () -> Long = { SystemClock.elapsedRealtime() },
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate, // the recognizer lives on the main thread
    private val pause: suspend (Long) -> Unit = { delay(it) },
    /** False in tests, so every instance starts with an empty memory. */
    processWideMemory: Boolean = true
) : VoiceInput {
    constructor(context: Context) : this({ AndroidRecognizer(context.applicationContext) })

    @Volatile
    private var session: ProducerScope<VoiceEvent>? = null

    override fun cancel() {
        session?.close()
    }

    override fun listen(language: Language): Flow<VoiceEvent> = callbackFlow {
        session = this
        val started = now()
        val smoother = LevelSmoother()
        val tags = RecognizerErrors.tags(language)
        var tagIndex = workingTag[language] ?: 0
        var online = language in noOfflineModel
        var freshRetryUsed = false
        var recognizer: Recognizer? = null
        var generation = 0
        var instances = 0
        var restartJob: Job? = null
        WgoMetrics.inc(WgoMetrics.Counter.RECOGNIZER_SESSION)
        var ready = false // the current instance is ready (reset on every restart)
        var readySent = false // the UI got VoiceEvent.Ready (once per session, even across restarts)
        var readyAt = 0L
        var speechStarted = false
        var lastVoice = started
        var lastPartial: String? = null
        var stopAt = 0L
        var finished = false

        fun release() {
            generation++ // callbacks of the released instance are ignored from now on
            recognizer?.cancel()
            recognizer?.destroy()
            recognizer = null
        }

        fun finish(event: VoiceEvent) {
            if (finished) return
            finished = true
            trySend(event)
            close()
        }

        fun begin() {
            val gen = ++generation
            val r = newRecognizer()
            instances++
            recognizer = r
            val request = RecognizerRequest(tags[tagIndex], preferOffline = !online)
            WgoMetrics.event("voice_attempt n=$instances tag=${request.languageTag} online=$online at_ms=${now() - started}")
            r.start(request, object : RecognizerListener {
                private fun stale() = gen != generation || finished

                override fun onReady() {
                    if (stale() || ready) return
                    ready = true
                    readyAt = now()
                    lastVoice = readyAt
                    WgoMetrics.event("voice_ready ms=${readyAt - started} instances=$instances tag=${tags[tagIndex]} online=$online")
                    if (!readySent) {
                        readySent = true
                        trySend(VoiceEvent.Ready)
                    }
                }

                override fun onBeginningOfSpeech() {
                    if (stale()) return
                    // Speech means this tag works (a missing model fails right after "ready", before any speech).
                    workingTag[language] = tagIndex
                    speechStarted = true
                    lastVoice = now()
                }

                override fun onRms(rmsDb: Float) {
                    if (stale()) return
                    val level = smoother.push(rmsDb)
                    if (level >= VOICE_LEVEL) lastVoice = now()
                    trySend(VoiceEvent.Level(level))
                }

                override fun onPartial(text: String) {
                    if (stale()) return
                    workingTag[language] = tagIndex
                    if (text != lastPartial) lastVoice = now()
                    lastPartial = text
                    trySend(VoiceEvent.Partial(text))
                }

                override fun onResults(text: String?) {
                    if (stale()) return
                    val best = text ?: lastPartial
                    finish(if (best.isNullOrBlank()) VoiceEvent.Failed(WgoFailure.NotFound) else VoiceEvent.Final(best))
                }

                override fun onError(code: Int) {
                    if (stale()) return
                    val heard = speechStarted || !lastPartial.isNullOrBlank()
                    val action = RecognizerErrors.decide(
                        code, RecognizerErrors.Attempt(heard, online, tagIndex, tags.size, freshRetryUsed, lastPartial)
                    )
                    WgoMetrics.event("voice_error code=$code ready=$ready online=$online tag=${tags[tagIndex]} action=$action")
                    when (action) {
                        is Action.Restart -> {
                            if (!online && action.online) noOfflineModel += language
                            online = action.online
                            tagIndex = action.tagIndex
                            if (action.consumesFreshRetry) freshRetryUsed = true
                            ready = false // the next instance has to report ready again (the UI keeps its state)
                            release()
                            restartJob = launch {
                                pause(RecognizerErrors.RESTART_DELAY_MS)
                                if (!finished) begin()
                            }
                        }
                        is Action.Final -> finish(VoiceEvent.Final(action.text))
                        is Action.Fail -> finish(VoiceEvent.Failed(action.failure))
                    }
                }
            })
        }

        begin()

        // Caps: time to get ready, total listening time and trailing silence. After stop() the recognizer normally
        // answers with onResults within a few hundred ms; if it does not, the best partial (or "not understood") ends
        // the session.
        val watchdog = launch {
            while (isActive && !finished) {
                delay(WATCHDOG_TICK_MS)
                val t = now()
                when {
                    !ready -> if (t - started >= START_TIMEOUT_MS) {
                        WgoMetrics.event("voice_stop reason=not_ready after_ms=${t - started}")
                        finish(VoiceEvent.Failed(WgoFailure.NotStarted))
                    }
                    stopAt == 0L -> {
                        val overTime = t - readyAt >= MAX_LISTEN_MS
                        val silent = speechStarted && t - lastVoice >= SILENCE_AFTER_SPEECH_MS
                        if (overTime || silent) {
                            WgoMetrics.event("voice_stop reason=${if (overTime) "cap" else "silence"} after_ms=${t - started}")
                            stopAt = t
                            recognizer?.stop()
                        }
                    }
                    t - stopAt >= STOP_GRACE_MS -> {
                        val partial = lastPartial
                        finish(if (partial.isNullOrBlank()) VoiceEvent.Failed(WgoFailure.NotFound) else VoiceEvent.Final(partial))
                    }
                }
            }
        }

        awaitClose {
            watchdog.cancel()
            restartJob?.cancel()
            val t0 = now()
            release()
            if (session === this) session = null
            WgoMetrics.event(
                "voice_destroyed session_ms=${t0 - started} destroy_ms=${now() - t0} instances=$instances online=$online"
            )
        }
    }.flowOn(dispatcher)

    /**
     * What the process learnt about the recognizer (kept for the process, so a reopened Activity with a new ViewModel
     * does not pay the failed offline attempt again).
     */
    private class Memory {
        /** Languages whose offline model turned out to be missing: later sessions go online at once. */
        val noOfflineModel: MutableSet<Language> = java.util.Collections.synchronizedSet(HashSet())

        /** Per language, the index of the tag that last heard speech (later sessions start with it). */
        val workingTag = java.util.concurrent.ConcurrentHashMap<Language, Int>()
    }

    private val memory = if (processWideMemory) PROCESS_MEMORY else Memory()
    private val noOfflineModel get() = memory.noOfflineModel
    private val workingTag get() = memory.workingTag

    companion object {
        private val PROCESS_MEMORY = Memory()
        const val MAX_LISTEN_MS = 8_000L
        const val SILENCE_AFTER_SPEECH_MS = 2_000L

        /**
         * Time to get ready. A cold recognizer process took 2.4-9.6 s on the Galaxy Watch5 Pro (9.6 s when the app
         * process itself was fresh), and a restart adds about 0.5-2.5 s.
         */
        const val START_TIMEOUT_MS = 20_000L
        private const val STOP_GRACE_MS = 1_200L
        private const val WATCHDOG_TICK_MS = 100L
        private const val VOICE_LEVEL = 0.35f

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

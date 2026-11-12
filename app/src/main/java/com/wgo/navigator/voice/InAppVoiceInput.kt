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
 * model moves the session online and through the language tags (both facts are kept in a [RecognizerMemory], across
 * app restarts in the app; when a restart follows a "ready", [VoiceEvent.Restarting] puts the UI back to preparing); a busy, disconnected or unbinding service is recreated once, [RecognizerErrors.RESTART_DELAY_MS] after the
 * old instance was destroyed. Nothing restarts once words were transcribed (they end the session). Caps:
 * [START_TIMEOUT_MS] per attempt to get ready (a cold recognizer process takes 2-18 s), then [MAX_LISTEN_MS] of
 * listening and [SILENCE_AFTER_SPEECH_MS] of silence once speech started; after a listening cap the recognizer is
 * asked to stop and the best transcript so far is used.
 */
class InAppVoiceInput(
    private val newRecognizer: () -> Recognizer,
    private val now: () -> Long = { SystemClock.elapsedRealtime() },
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate, // the recognizer lives on the main thread
    private val pause: suspend (Long) -> Unit = { delay(it) },
    memoryProvider: () -> RecognizerMemory = { InMemoryRecognizerMemory() }
) : VoiceInput {
    constructor(context: Context) : this(
        newRecognizer = { AndroidRecognizer(context.applicationContext) },
        memoryProvider = { PrefsRecognizerMemory.get(context) }
    )

    /** Read on the first session (after the preparing frame), never at ViewModel creation. */
    private val memory by lazy(memoryProvider)

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
        var tagIndex = memory.workingTag(language)?.coerceIn(0, tags.lastIndex) ?: 0
        var online = memory.noOfflineModel(language)
        var attemptStartedAt = started
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
            VoiceLog.event("outcome ${outcomeOf(event)} session_ms=${now() - started} instances=$instances")
            trySend(event)
            close()
        }

        fun begin() {
            val gen = ++generation
            val r = newRecognizer()
            instances++
            recognizer = r
            attemptStartedAt = now()
            val request = RecognizerRequest(tags[tagIndex], preferOffline = !online)
            VoiceLog.event("recognizer_created n=$instances tag=${request.languageTag} online=$online session_ms=${now() - started}")
            r.start(request, object : RecognizerListener {
                private fun stale() = gen != generation || finished

                override fun onReady() {
                    if (stale() || ready) return
                    ready = true
                    readyAt = now()
                    lastVoice = readyAt
                    VoiceLog.event(
                        "ready n=$instances attempt_ms=${readyAt - attemptStartedAt} session_ms=${readyAt - started} " +
                            "tag=${tags[tagIndex]} online=$online"
                    )
                    if (!readySent) {
                        readySent = true
                        trySend(VoiceEvent.Ready)
                    }
                }

                override fun onBeginningOfSpeech() {
                    if (stale()) return
                    // Speech means this tag works (a missing model fails right after "ready", before any speech).
                    memory.setWorkingTag(language, tagIndex)
                    if (!speechStarted) VoiceLog.event("speech n=$instances after_ready_ms=${now() - readyAt}")
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
                    memory.setWorkingTag(language, tagIndex)
                    if (text != lastPartial) lastVoice = now()
                    lastPartial = text
                    trySend(VoiceEvent.Partial(text))
                }

                override fun onResults(text: String?) {
                    if (stale()) return
                    VoiceLog.event("results n=$instances len=${text?.length ?: -1} partial_len=${lastPartial?.length ?: -1}")
                    val best = text ?: lastPartial
                    finish(if (best.isNullOrBlank()) VoiceEvent.Failed(WgoFailure.NotFound) else VoiceEvent.Final(best))
                }

                override fun onError(code: Int) {
                    if (stale()) return
                    val action = RecognizerErrors.decide(
                        code, RecognizerErrors.Attempt(online, tagIndex, tags.size, freshRetryUsed, lastPartial)
                    )
                    VoiceLog.event(
                        "error code=$code n=$instances ready=$ready speech=$speechStarted online=$online " +
                            "tag=${tags[tagIndex]} attempt_ms=${now() - attemptStartedAt} action=$action"
                    )
                    when (action) {
                        is Action.Restart -> {
                            if (!online && action.online) memory.markNoOfflineModel(language)
                            online = action.online
                            tagIndex = action.tagIndex
                            if (action.consumesFreshRetry) freshRetryUsed = true
                            ready = false // the next instance has to report ready again
                            speechStarted = false // the silence cap starts over with the new instance
                            if (readySent) {
                                // The UI said "speak now" but nothing is listening any more: back to preparing.
                                readySent = false
                                trySend(VoiceEvent.Restarting)
                            }
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
                    // Per attempt: a restart late in a slow start still gets its full time.
                    !ready -> if (t - attemptStartedAt >= START_TIMEOUT_MS) {
                        VoiceLog.event("stop reason=not_ready session_ms=${t - started} attempt_ms=${t - attemptStartedAt}")
                        finish(VoiceEvent.Failed(WgoFailure.NotStarted))
                    }
                    stopAt == 0L -> {
                        val overTime = t - readyAt >= MAX_LISTEN_MS
                        val silent = speechStarted && t - lastVoice >= SILENCE_AFTER_SPEECH_MS
                        if (overTime || silent) {
                            VoiceLog.event("stop reason=${if (overTime) "cap" else "silence"} session_ms=${t - started}")
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
            VoiceLog.event(
                "destroyed session_ms=${t0 - started} destroy_ms=${now() - t0} instances=$instances online=$online" +
                    if (finished) "" else " outcome=closed_by_app"
            )
        }
    }.flowOn(dispatcher)

    /** The outcome for the log: the event kind and the transcript LENGTH only (never its text). */
    private fun outcomeOf(event: VoiceEvent): String = when (event) {
        is VoiceEvent.Final -> "final len=${event.text.length}"
        is VoiceEvent.Failed -> "failed=${event.failure::class.simpleName ?: event.failure}"
        else -> event::class.simpleName ?: "other"
    }

    companion object {
        const val MAX_LISTEN_MS = 8_000L
        const val SILENCE_AFTER_SPEECH_MS = 2_000L

        /**
         * Time for one recognizer instance to get ready (counted per attempt). A cold recognizer process took 2.4-18 s
         * on the Galaxy Watch5 Pro (the slowest with a fresh app process and little free memory).
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
            VoiceLog.event("inapp available=$available selected=${!selected.isNullOrEmpty()}")
            return available && !selected.isNullOrEmpty()
        }
    }
}

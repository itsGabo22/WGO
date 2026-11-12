package com.wgo.navigator.voice

import com.wgo.navigator.Language
import com.wgo.navigator.core.VoiceEvent
import com.wgo.navigator.core.VoiceInput
import com.wgo.navigator.core.WgoFailure
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * The voice ladder (P9, docs/design/WGO_DESIGN_SYSTEM.md "Instant-capture voice input"). Rung 1 is the app's own
 * capture with cloud transcription ([CloudVoiceInput]); rung 2 is the P8 system-recognizer chain ([system], the
 * [VoiceInputChain], wrapped without any change: its quiet restarts and system dialog stay as they are).
 * [cloudBlocker] says, at the press, why rung 1 cannot be used (null: it can); then the press goes straight to rung 2.
 * When rung 1 fails for any reason except silence (nothing to recognise) or a cancel, the same session continues on
 * rung 2 with [VoiceEvent.SayAgain]; the user sees an error only if rung 2 fails too.
 */
class VoiceLadder(
    private val system: VoiceInput,
    private val cloud: CloudVoiceInput?,
    private val cloudBlocker: () -> String?,
    private val systemStrategy: () -> VoiceStrategy,
    private val io: CoroutineDispatcher = Dispatchers.IO
) : VoiceInput {
    /** True when the next press records with the app's own microphone (rung 1). */
    fun usesCloud(): Boolean = cloud != null && cloudBlocker() == null

    /** The system chain's strategy for the next press when it is used (rung 2 or the only rung). */
    fun systemStrategy(): VoiceStrategy = systemStrategy.invoke()

    /** Manual stop of a running capture (no-op otherwise). */
    fun requestStop() {
        cloud?.requestStop()
    }

    override fun cancel() {
        cloud?.cancel()
        system.cancel()
    }

    override fun listen(language: Language): Flow<VoiceEvent> = flow {
        val blocker = if (cloud == null) "no_transcriber" else cloudBlocker()
        if (cloud == null || blocker != null) {
            VoiceLog.event("rung=system reason=$blocker")
            emitAll(system.listen(language))
            return@flow
        }
        VoiceLog.event("rung=cloud")
        var failure: WgoFailure? = null
        cloud.listen(language).collect { event ->
            if (event is VoiceEvent.Failed && fallsBack(event.failure)) failure = event.failure else emit(event)
        }
        val failed = failure ?: return@flow
        VoiceLog.event("rung=system reason=cloud_failed failure=$failed")
        // The recognizer must not open the microphone while the capture still holds it.
        withContext(io) { cloud.awaitMicReleased() }
        emit(VoiceEvent.SayAgain)
        emitAll(system.listen(language))
    }

    companion object {
        /** Silence ("No te oí") and a cancel end the session; any other cloud failure moves on to rung 2. */
        fun fallsBack(failure: WgoFailure): Boolean = failure != WgoFailure.NoSpeech && failure != WgoFailure.Cancelled
    }
}

package com.wgo.navigator.voice

import com.wgo.navigator.Language
import com.wgo.navigator.core.VoiceEvent
import com.wgo.navigator.core.VoiceInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * The voice ladder (P9, docs/design/WGO_DESIGN_SYSTEM.md "Instant-capture voice input"). Rung 1 is the cloud capture
 * ([CloudVoiceInput]); rung 2 is the P8 system-recognizer chain ([VoiceInputChain]), which this class wraps without
 * changing it. [cloudBlocker] says, at the press, why the cloud rung cannot be used (null: it can).
 */
class VoiceLadder(
    val system: VoiceInputChain,
    private val cloud: CloudVoiceInput?,
    private val cloudBlocker: () -> String?
) : VoiceInput {
    /** True when the next press records with the app's own microphone (rung 1). */
    fun usesCloud(): Boolean = cloud != null && cloudBlocker() == null

    /** The system chain's strategy for the next press when it is used (rung 2 or the only rung). */
    fun systemStrategy(): VoiceStrategy = system.strategy()

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
        emitAll(cloud.listen(language))
    }
}

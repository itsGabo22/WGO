package com.wgo.navigator.core

import com.wgo.navigator.Language
import kotlinx.coroutines.flow.Flow

/** What a listening session reports, in order: Ready, then Level/Partial any number of times, then Final or Failed. */
sealed interface VoiceEvent {
    /** The recognizer is listening (the UI may start the listening visuals). */
    data object Ready : VoiceEvent

    /** Input level, 0..1, for the voice bars. */
    data class Level(val value: Float) : VoiceEvent

    /** Live transcript so far. */
    data class Partial(val text: String) : VoiceEvent

    /** Final transcript; the session is over. */
    data class Final(val text: String) : VoiceEvent

    /** The session ended without a transcript. */
    data class Failed(val failure: WgoFailure) : VoiceEvent
}

/** Speech to text. One session at a time; collecting [listen] starts it, cancelling the collection stops it. */
interface VoiceInput {
    fun listen(language: Language): Flow<VoiceEvent>

    /** Ends the current session early and releases the microphone. Safe to call when idle. */
    fun cancel()
}

package com.wgo.navigator.core

import com.wgo.navigator.Language
import kotlinx.coroutines.flow.Flow

/** What a listening session reports, in order: Ready, then Level/Partial any number of times, then Final or Failed. */
sealed interface VoiceEvent {
    /** The recognizer is listening (the UI may start the listening visuals). */
    data object Ready : VoiceEvent

    /**
     * The recognizer reported ready but failed before hearing anything and is being recreated (an offline engine
     * without the language pack reports ready, then fails): the UI goes back to "preparing"; [Ready] follows again.
     */
    data object Restarting : VoiceEvent

    /** Input level, 0..1, for the voice bars. */
    data class Level(val value: Float) : VoiceEvent

    /** Live transcript so far. */
    data class Partial(val text: String) : VoiceEvent

    /** Final transcript; the session is over. */
    data class Final(val text: String) : VoiceEvent

    /** The session ended without a transcript. */
    data class Failed(val failure: WgoFailure) : VoiceEvent

    /** The system speech dialog is showing (fallback strategy); its result arrives as [Final] or [Failed]. */
    data object AwaitingSystem : VoiceEvent

    /** P9: the captured speech is being transcribed (the UI shows "understanding"); [Final] or [Failed] follows. */
    data object Transcribing : VoiceEvent

    /**
     * P9: the cloud transcription failed and the system recognizer takes over: the UI goes back to "preparing" and,
     * at the next [Ready], asks the user to say it again.
     */
    data object SayAgain : VoiceEvent
}

/** Speech to text. One session at a time; collecting [listen] starts it, cancelling the collection stops it. */
interface VoiceInput {
    fun listen(language: Language): Flow<VoiceEvent>

    /** Ends the current session early and releases the microphone. Safe to call when idle. */
    fun cancel()
}

/** BCP-47 tag used for speech recognition: Colombian Spanish or US English. */
val Language.speechTag: String
    get() = when (this) {
        Language.ES -> "es-CO"
        Language.EN -> "en-US"
    }

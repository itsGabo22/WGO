package com.wgo.navigator.voice

import com.wgo.navigator.Language
import com.wgo.navigator.core.WgoFailure

/**
 * What a voice session does with a [android.speech.SpeechRecognizer] error code. Pure, for unit tests.
 *
 * Only the network codes (1, 2) mean "no internet", and only when they come back after one silent retry (or after
 * speech was heard). A missing language model (12, 13) is not a user error: the session
 * goes online and walks the language tags. A recognizer that is busy, disconnected or unbinding (5, 8, 10, 11) is
 * recreated once, silently, before the user sees [WgoFailure.NotStarted]. Restarts happen only while no speech has been
 * heard. Codes above 9 are API 31+ constants, written
 * as literals so minSdk 30 code needs no version guard.
 */
object RecognizerErrors {
    const val NETWORK_TIMEOUT = 1
    const val NETWORK = 2
    const val AUDIO = 3
    const val SERVER = 4
    const val CLIENT = 5
    const val SPEECH_TIMEOUT = 6
    const val NO_MATCH = 7
    const val BUSY = 8
    const val INSUFFICIENT_PERMISSIONS = 9
    const val TOO_MANY_REQUESTS = 10
    const val SERVER_DISCONNECTED = 11
    const val LANGUAGE_NOT_SUPPORTED = 12
    const val LANGUAGE_UNAVAILABLE = 13

    /** A fresh instance is created this long after the old one was destroyed (the service needs time to unbind). */
    const val RESTART_DELAY_MS = 400L

    /** Language tags tried in order when the recognizer reports a missing language model. */
    fun tags(language: Language): List<String> = when (language) {
        Language.ES -> listOf("es-CO", "es-419", "es")
        Language.EN -> listOf("en-US", "en")
    }

    /** Where a session stands when an error arrives. */
    data class Attempt(
        /**
         * Speech was already detected (beginning of speech or a partial): a restart would drop what the user said.
         * "Ready" alone does not count: the offline recognizer reports ready (microphone opened) and THEN fails with
         * 12 when the language pack is missing (seen on the Galaxy Watch5 Pro).
         */
        val heardSpeech: Boolean,
        val online: Boolean,
        val tagIndex: Int,
        val tagCount: Int,
        val freshRetryUsed: Boolean,
        val partial: String?
    )

    sealed interface Action {
        /** Destroy, wait [RESTART_DELAY_MS], start a fresh instance with [online] and the tag at [tagIndex]. */
        data class Restart(val online: Boolean, val tagIndex: Int, val consumesFreshRetry: Boolean) : Action

        /** End the session with the transcript heard so far. */
        data class Final(val text: String) : Action

        /** End the session with [failure]. */
        data class Fail(val failure: WgoFailure) : Action
    }

    private val RESTARTABLE = setOf(
        NETWORK_TIMEOUT, NETWORK, LANGUAGE_NOT_SUPPORTED, LANGUAGE_UNAVAILABLE, SERVER_DISCONNECTED, TOO_MANY_REQUESTS, BUSY, CLIENT
    )

    fun decide(code: Int, a: Attempt): Action = when {
        // Once speech was heard, a restart would drop it: keep the partial transcript, or ask to retry.
        a.heardSpeech && code in RESTARTABLE -> when {
            !a.partial.isNullOrBlank() -> Action.Final(a.partial)
            code == NETWORK || code == NETWORK_TIMEOUT -> Action.Fail(WgoFailure.Offline)
            else -> Action.Fail(WgoFailure.NotStarted)
        }
        else -> decideBeforeReady(code, a)
    }

    private fun decideBeforeReady(code: Int, a: Attempt): Action = when (code) {
        // A cold recognizer process sometimes reports NETWORK on its first online session while the internet is fine
        // (seen on the Galaxy Watch5 Pro): one silent fresh retry first; a second network error is real.
        NETWORK_TIMEOUT, NETWORK ->
            if (!a.freshRetryUsed) Action.Restart(a.online, a.tagIndex, consumesFreshRetry = true)
            else Action.Fail(WgoFailure.Offline)
        LANGUAGE_NOT_SUPPORTED, LANGUAGE_UNAVAILABLE -> when {
            !a.online -> Action.Restart(online = true, tagIndex = a.tagIndex, consumesFreshRetry = false)
            a.tagIndex + 1 < a.tagCount -> Action.Restart(online = true, tagIndex = a.tagIndex + 1, consumesFreshRetry = false)
            else -> Action.Fail(WgoFailure.Unavailable) // no tag works: the chain falls back to the system dialog
        }
        SERVER_DISCONNECTED, TOO_MANY_REQUESTS, BUSY, CLIENT ->
            if (!a.freshRetryUsed) Action.Restart(a.online, a.tagIndex, consumesFreshRetry = true)
            else Action.Fail(WgoFailure.NotStarted)
        SPEECH_TIMEOUT, NO_MATCH ->
            if (!a.partial.isNullOrBlank()) Action.Final(a.partial) else Action.Fail(WgoFailure.NotFound)
        INSUFFICIENT_PERMISSIONS -> Action.Fail(WgoFailure.NoPermission)
        SERVER -> Action.Fail(WgoFailure.Server(0))
        else -> Action.Fail(WgoFailure.Unavailable) // audio and unknown codes
    }
}

package com.wgo.navigator.voice

import com.wgo.navigator.Language
import com.wgo.navigator.core.WgoFailure

/**
 * What a voice session does with a [android.speech.SpeechRecognizer] error code. Pure, for unit tests.
 *
 * - Only the network codes (1, 2) mean "no internet", and only when they come back after one silent retry.
 * - A missing language model (12, 13) is not a user error: the session goes online and walks the language tags.
 * - A recognizer that is busy, disconnected or unbinding (5, 8, 10, 11) is recreated once, silently, before the user
 *   sees [WgoFailure.NotStarted] (its own budget: a cold start can also hit a network code).
 * - Until a word is transcribed (P8): no match, speech timeout, server, audio and the listening cap restart quietly up
 *   to [MAX_QUIET_RESTARTS] times; the user only sees an error once that budget is spent.
 * - What decides between "restart" and "end" is the partial transcript: with text, the session ends with it (a restart
 *   would drop it); without text nothing is lost, so the session restarts (the UI goes back to preparing and cues
 *   "speak now" again). Neither "ready" nor detected speech counts, both measured on the Galaxy Watch5 Pro: the
 *   offline engine reports ready and THEN fails with 12 when the language pack is missing, and a cold online engine
 *   reports ready, then fails with 2 (its "online no progress") about 5 s later even when the user spoke.
 *
 * Codes above 9 are API 31+ constants, written as literals so minSdk 30 code needs no version guard.
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

    /** Quiet restarts per session for a failure before any speech (spec section 7.1, P8). */
    const val MAX_QUIET_RESTARTS = 2

    /** Which per-session budget a restart uses. */
    enum class Budget {
        /** Language steps: bounded by the tag list, no budget. */
        None,

        /** Network codes 1, 2: one restart. */
        Network,

        /** Busy / disconnected / unbinding / client: one restart. */
        Service,

        /** No match, speech timeout, server, audio or the listening cap before any speech: [MAX_QUIET_RESTARTS]. */
        Quiet
    }

    /**
     * Where a session stands when an error arrives. [partial] is the best transcript so far (null or blank: none); the
     * other fields are the budgets already used.
     */
    data class Attempt(
        val online: Boolean,
        val tagIndex: Int,
        val tagCount: Int,
        val networkRetryUsed: Boolean = false,
        val partial: String? = null,
        val serviceRetryUsed: Boolean = false,
        val quietRestarts: Int = 0
    )

    sealed interface Action {
        /** Destroy, wait [RESTART_DELAY_MS], start a fresh instance with [online] and the tag at [tagIndex]. */
        data class Restart(val online: Boolean, val tagIndex: Int, val budget: Budget) : Action

        /** End the session with the transcript heard so far. */
        data class Final(val text: String) : Action

        /** End the session with [failure]. */
        data class Fail(val failure: WgoFailure) : Action
    }

    fun decide(code: Int, a: Attempt): Action {
        val partial = a.partial?.takeIf { it.isNotBlank() }
        // A restart would drop the words already transcribed: keep them instead.
        if (partial != null && (code in RESTARTABLE || code == SPEECH_TIMEOUT || code == NO_MATCH)) return Action.Final(partial)
        return when (code) {
            in RESTARTABLE -> restart(code, a)
            SPEECH_TIMEOUT, NO_MATCH -> quietly(a) ?: Action.Fail(WgoFailure.NotFound)
            INSUFFICIENT_PERMISSIONS -> Action.Fail(WgoFailure.NoPermission)
            SERVER -> quietly(a) ?: Action.Fail(WgoFailure.Server(0))
            AUDIO -> quietly(a) ?: Action.Fail(WgoFailure.Unavailable)
            else -> Action.Fail(WgoFailure.Unavailable) // unknown codes
        }
    }

    /**
     * The listening cap was reached: with no word yet and quiet restarts left the session starts over silently (null
     * means it ends with what it has).
     */
    fun onListeningCap(a: Attempt): Action? = quietly(a)

    /**
     * A quiet restart while no word was transcribed and the budget lasts; null otherwise. Detected speech does not
     * count: a cold recognizer can hear the user and still answer no match (measured on the watch, P8).
     */
    private fun quietly(a: Attempt): Action? =
        if (a.partial.isNullOrBlank() && a.quietRestarts < MAX_QUIET_RESTARTS) {
            Action.Restart(a.online, a.tagIndex, Budget.Quiet)
        } else null

    private val RESTARTABLE = setOf(
        NETWORK_TIMEOUT, NETWORK, LANGUAGE_NOT_SUPPORTED, LANGUAGE_UNAVAILABLE, SERVER_DISCONNECTED, TOO_MANY_REQUESTS, BUSY, CLIENT
    )

    private fun restart(code: Int, a: Attempt): Action = when (code) {
        LANGUAGE_NOT_SUPPORTED, LANGUAGE_UNAVAILABLE -> when {
            !a.online -> Action.Restart(online = true, tagIndex = a.tagIndex, budget = Budget.None)
            a.tagIndex + 1 < a.tagCount -> Action.Restart(online = true, tagIndex = a.tagIndex + 1, budget = Budget.None)
            else -> Action.Fail(WgoFailure.Unavailable) // no tag works: the chain falls back to the system dialog
        }
        // Network: a cold engine's first online session fails like this while the internet is fine; a second one is real.
        NETWORK_TIMEOUT, NETWORK ->
            if (!a.networkRetryUsed) Action.Restart(a.online, a.tagIndex, Budget.Network)
            else Action.Fail(WgoFailure.Offline)
        else -> // busy, disconnected, unbinding, client
            if (!a.serviceRetryUsed) Action.Restart(a.online, a.tagIndex, Budget.Service)
            else Action.Fail(WgoFailure.NotStarted)
    }
}

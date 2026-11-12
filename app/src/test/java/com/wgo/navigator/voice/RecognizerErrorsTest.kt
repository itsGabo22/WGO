package com.wgo.navigator.voice

import com.wgo.navigator.Language
import com.wgo.navigator.core.WgoFailure
import com.wgo.navigator.voice.RecognizerErrors.Action
import com.wgo.navigator.voice.RecognizerErrors.Attempt
import com.wgo.navigator.voice.RecognizerErrors.Budget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecognizerErrorsTest {
    private fun attempt(
        online: Boolean = false,
        tagIndex: Int = 0,
        tagCount: Int = 3,
        networkRetryUsed: Boolean = false,
        partial: String? = null,
        serviceRetryUsed: Boolean = false,
        speechHeard: Boolean = false,
        quietRestarts: Int = 0
    ) = Attempt(online, tagIndex, tagCount, networkRetryUsed, partial, serviceRetryUsed, speechHeard, quietRestarts)

    /** Every budget used: what the user finally sees. */
    private fun spent(online: Boolean = false, partial: String? = null) = attempt(
        online = online, networkRetryUsed = true, partial = partial, serviceRetryUsed = true,
        quietRestarts = RecognizerErrors.MAX_QUIET_RESTARTS
    )

    @Test fun onlyNetworkCodesEverMeanNoInternet() {
        val states = listOf(
            attempt(), attempt(online = true), spent(), spent(online = true), attempt(online = true, tagIndex = 2, networkRetryUsed = true),
            attempt(partial = "calle"), attempt(partial = " ", networkRetryUsed = true), attempt(speechHeard = true)
        )
        for (code in -1..20) for (state in states) {
            val action = RecognizerErrors.decide(code, state)
            if (code != RecognizerErrors.NETWORK && code != RecognizerErrors.NETWORK_TIMEOUT) {
                assertNotEquals("code $code in $state", Action.Fail(WgoFailure.Offline), action)
            }
        }
    }

    @Test fun networkErrorsRetryOnceSilentlyThenMeanNoInternet() {
        for (code in listOf(RecognizerErrors.NETWORK, RecognizerErrors.NETWORK_TIMEOUT)) {
            assertEquals(Action.Restart(true, 0, Budget.Network), RecognizerErrors.decide(code, attempt(online = true)))
            assertEquals(Action.Fail(WgoFailure.Offline), RecognizerErrors.decide(code, attempt(online = true, networkRetryUsed = true)))
        }
    }

    @Test fun missingLanguageModelGoesOnlineThenWalksTheTags() {
        val code = RecognizerErrors.LANGUAGE_NOT_SUPPORTED
        assertEquals(Action.Restart(true, 0, Budget.None), RecognizerErrors.decide(code, attempt(online = false)))
        assertEquals(Action.Restart(true, 1, Budget.None), RecognizerErrors.decide(code, attempt(online = true, tagIndex = 0)))
        assertEquals(Action.Restart(true, 2, Budget.None), RecognizerErrors.decide(code, attempt(online = true, tagIndex = 1)))
        assertEquals(Action.Fail(WgoFailure.Unavailable), RecognizerErrors.decide(code, attempt(online = true, tagIndex = 2)))
        // 13 behaves the same, and the language steps never use up a budget.
        assertEquals(
            Action.Restart(true, 1, Budget.None),
            RecognizerErrors.decide(RecognizerErrors.LANGUAGE_UNAVAILABLE, spent(online = true))
        )
    }

    @Test fun busyDisconnectedOrClientErrorsRecreateOnceThenCannotStart() {
        val codes = listOf(
            RecognizerErrors.SERVER_DISCONNECTED, RecognizerErrors.TOO_MANY_REQUESTS, RecognizerErrors.BUSY, RecognizerErrors.CLIENT
        )
        for (code in codes) {
            assertEquals("code $code", Action.Restart(false, 0, Budget.Service), RecognizerErrors.decide(code, attempt()))
            assertEquals("code $code", Action.Fail(WgoFailure.NotStarted), RecognizerErrors.decide(code, attempt(serviceRetryUsed = true)))
        }
    }

    @Test fun aNetworkRetryDoesNotUseUpTheServiceRetry() {
        // A cold start can hit error 2 and then 8 (P8): each has its own budget.
        val afterNetwork = attempt(online = true, networkRetryUsed = true)
        assertEquals(Action.Restart(true, 0, Budget.Service), RecognizerErrors.decide(RecognizerErrors.BUSY, afterNetwork))
        val afterService = attempt(online = true, serviceRetryUsed = true)
        assertEquals(Action.Restart(true, 0, Budget.Network), RecognizerErrors.decide(RecognizerErrors.NETWORK, afterService))
    }

    @Test fun noMatchBeforeAnySpeechRestartsQuietlyTwiceThenIsNotUnderstood() {
        // Measured on the watch (P8): ready in 0.67 s, then no match at 6 s with no speech detected showed an error.
        for (code in listOf(RecognizerErrors.SPEECH_TIMEOUT, RecognizerErrors.NO_MATCH)) {
            assertEquals(Action.Restart(false, 0, Budget.Quiet), RecognizerErrors.decide(code, attempt()))
            assertEquals(Action.Restart(false, 0, Budget.Quiet), RecognizerErrors.decide(code, attempt(quietRestarts = 1)))
            assertEquals(Action.Fail(WgoFailure.NotFound), RecognizerErrors.decide(code, attempt(quietRestarts = 2)))
        }
    }

    @Test fun noMatchAfterSpeechIsNotUnderstoodAtOnce() {
        for (code in listOf(RecognizerErrors.SPEECH_TIMEOUT, RecognizerErrors.NO_MATCH)) {
            assertEquals(Action.Fail(WgoFailure.NotFound), RecognizerErrors.decide(code, attempt(speechHeard = true)))
            assertEquals(Action.Final("unicentro"), RecognizerErrors.decide(code, attempt(partial = "unicentro")))
        }
    }

    @Test fun serverAndAudioRestartQuietlyBeforeSpeechThenKeepTheirSurfaces() {
        assertEquals(Action.Restart(false, 0, Budget.Quiet), RecognizerErrors.decide(RecognizerErrors.SERVER, attempt()))
        assertEquals(Action.Restart(false, 0, Budget.Quiet), RecognizerErrors.decide(RecognizerErrors.AUDIO, attempt()))
        assertEquals(Action.Fail(WgoFailure.Server(0)), RecognizerErrors.decide(RecognizerErrors.SERVER, spent()))
        assertEquals(Action.Fail(WgoFailure.Unavailable), RecognizerErrors.decide(RecognizerErrors.AUDIO, spent()))
        assertEquals(Action.Fail(WgoFailure.Server(0)), RecognizerErrors.decide(RecognizerErrors.SERVER, attempt(speechHeard = true)))
    }

    @Test fun otherCodesKeepTheirSurfaces() {
        assertEquals(Action.Fail(WgoFailure.NoPermission), RecognizerErrors.decide(RecognizerErrors.INSUFFICIENT_PERMISSIONS, attempt()))
        assertEquals(Action.Fail(WgoFailure.Unavailable), RecognizerErrors.decide(99, attempt()))
    }

    @Test fun theListeningCapRestartsQuietlyOnlyWhenNothingWasHeard() {
        assertEquals(Action.Restart(true, 1, Budget.Quiet), RecognizerErrors.onListeningCap(attempt(online = true, tagIndex = 1)))
        assertNull(RecognizerErrors.onListeningCap(attempt(speechHeard = true)))
        assertNull(RecognizerErrors.onListeningCap(attempt(partial = "plaza")))
        assertNull(RecognizerErrors.onListeningCap(attempt(quietRestarts = RecognizerErrors.MAX_QUIET_RESTARTS)))
    }

    @Test fun transcribedWordsEndTheSessionInsteadOfARestart() {
        val codes = listOf(
            RecognizerErrors.NETWORK, RecognizerErrors.NETWORK_TIMEOUT, RecognizerErrors.SERVER_DISCONNECTED,
            RecognizerErrors.BUSY, RecognizerErrors.CLIENT, RecognizerErrors.TOO_MANY_REQUESTS, RecognizerErrors.LANGUAGE_NOT_SUPPORTED,
            RecognizerErrors.NO_MATCH, RecognizerErrors.SPEECH_TIMEOUT
        )
        for (code in codes) {
            assertEquals("code $code", Action.Final("calle 18"), RecognizerErrors.decide(code, attempt(partial = "calle 18")))
            assertEquals("code $code", Action.Final("calle 18"), RecognizerErrors.decide(code, spent(online = true, partial = "calle 18")))
        }
        // A blank partial is no text: the restart rules apply.
        assertEquals(Action.Restart(false, 0, Budget.Network), RecognizerErrors.decide(RecognizerErrors.NETWORK, attempt(partial = "  ")))
    }

    @Test fun languageTagsInOrder() {
        assertEquals(listOf("es-CO", "es-419", "es"), RecognizerErrors.tags(Language.ES))
        assertEquals(listOf("en-US", "en"), RecognizerErrors.tags(Language.EN))
    }
}

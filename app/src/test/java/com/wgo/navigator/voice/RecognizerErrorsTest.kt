package com.wgo.navigator.voice

import com.wgo.navigator.Language
import com.wgo.navigator.core.WgoFailure
import com.wgo.navigator.voice.RecognizerErrors.Action
import com.wgo.navigator.voice.RecognizerErrors.Attempt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RecognizerErrorsTest {
    private fun attempt(
        online: Boolean = false,
        tagIndex: Int = 0,
        tagCount: Int = 3,
        freshRetryUsed: Boolean = false,
        partial: String? = null
    ) = Attempt(online, tagIndex, tagCount, freshRetryUsed, partial)

    @Test fun onlyNetworkCodesEverMeanNoInternet() {
        val states = listOf(
            attempt(), attempt(online = true), attempt(freshRetryUsed = true), attempt(online = true, tagIndex = 2, freshRetryUsed = true),
            attempt(partial = "calle"), attempt(partial = " ", freshRetryUsed = true)
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
            assertEquals(Action.Restart(true, 0, consumesFreshRetry = true), RecognizerErrors.decide(code, attempt(online = true)))
            assertEquals(Action.Fail(WgoFailure.Offline), RecognizerErrors.decide(code, attempt(online = true, freshRetryUsed = true)))
        }
    }

    @Test fun missingLanguageModelGoesOnlineThenWalksTheTags() {
        val code = RecognizerErrors.LANGUAGE_NOT_SUPPORTED
        assertEquals(Action.Restart(true, 0, false), RecognizerErrors.decide(code, attempt(online = false)))
        assertEquals(Action.Restart(true, 1, false), RecognizerErrors.decide(code, attempt(online = true, tagIndex = 0)))
        assertEquals(Action.Restart(true, 2, false), RecognizerErrors.decide(code, attempt(online = true, tagIndex = 1)))
        assertEquals(Action.Fail(WgoFailure.Unavailable), RecognizerErrors.decide(code, attempt(online = true, tagIndex = 2)))
        // 13 behaves the same, and the language steps never use up the fresh retry.
        assertEquals(
            Action.Restart(true, 1, false),
            RecognizerErrors.decide(RecognizerErrors.LANGUAGE_UNAVAILABLE, attempt(online = true, freshRetryUsed = true))
        )
    }

    @Test fun busyDisconnectedOrClientErrorsRecreateOnceThenCannotStart() {
        val codes = listOf(
            RecognizerErrors.SERVER_DISCONNECTED, RecognizerErrors.TOO_MANY_REQUESTS, RecognizerErrors.BUSY, RecognizerErrors.CLIENT
        )
        for (code in codes) {
            assertEquals("code $code", Action.Restart(false, 0, true), RecognizerErrors.decide(code, attempt()))
            assertEquals("code $code", Action.Fail(WgoFailure.NotStarted), RecognizerErrors.decide(code, attempt(freshRetryUsed = true)))
        }
    }

    @Test fun noSpeechKeepsThePartialOrIsNotUnderstood() {
        for (code in listOf(RecognizerErrors.SPEECH_TIMEOUT, RecognizerErrors.NO_MATCH)) {
            assertEquals(Action.Fail(WgoFailure.NotFound), RecognizerErrors.decide(code, attempt()))
            assertEquals(Action.Final("unicentro"), RecognizerErrors.decide(code, attempt(partial = "unicentro")))
        }
    }

    @Test fun otherCodesKeepTheirSurfaces() {
        assertEquals(Action.Fail(WgoFailure.NoPermission), RecognizerErrors.decide(RecognizerErrors.INSUFFICIENT_PERMISSIONS, attempt()))
        assertEquals(Action.Fail(WgoFailure.Server(0)), RecognizerErrors.decide(RecognizerErrors.SERVER, attempt()))
        assertEquals(Action.Fail(WgoFailure.Unavailable), RecognizerErrors.decide(RecognizerErrors.AUDIO, attempt()))
    }

    @Test fun transcribedWordsEndTheSessionInsteadOfARestart() {
        val codes = listOf(
            RecognizerErrors.NETWORK, RecognizerErrors.NETWORK_TIMEOUT, RecognizerErrors.SERVER_DISCONNECTED,
            RecognizerErrors.BUSY, RecognizerErrors.CLIENT, RecognizerErrors.TOO_MANY_REQUESTS, RecognizerErrors.LANGUAGE_NOT_SUPPORTED
        )
        for (code in codes) {
            assertEquals("code $code", Action.Final("calle 18"), RecognizerErrors.decide(code, attempt(partial = "calle 18")))
            assertEquals("code $code", Action.Final("calle 18"), RecognizerErrors.decide(code, attempt(online = true, freshRetryUsed = true, partial = "calle 18")))
        }
        // A blank partial is no text: the restart rules apply.
        assertEquals(Action.Restart(false, 0, true), RecognizerErrors.decide(RecognizerErrors.NETWORK, attempt(partial = "  ")))
    }

    @Test fun languageTagsInOrder() {
        assertEquals(listOf("es-CO", "es-419", "es"), RecognizerErrors.tags(Language.ES))
        assertEquals(listOf("en-US", "en"), RecognizerErrors.tags(Language.EN))
    }
}

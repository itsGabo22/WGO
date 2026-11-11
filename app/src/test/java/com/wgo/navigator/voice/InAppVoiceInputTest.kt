package com.wgo.navigator.voice

import com.wgo.navigator.Language
import com.wgo.navigator.core.VoiceEvent
import com.wgo.navigator.core.WgoFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

/** Drives [InAppVoiceInput] with a scripted fake recognizer: one script per instance, run when it is started. */
class InAppVoiceInputTest {
    private class FakeRecognizers(vararg scripts: (RecognizerListener) -> Unit) {
        private val pending = ArrayDeque(scripts.toList())
        val requests = mutableListOf<RecognizerRequest>()
        var alive = 0
        var maxAlive = 0
        var pauses = mutableListOf<Long>()

        fun create(): Recognizer {
            alive++
            maxAlive = maxOf(maxAlive, alive)
            return object : Recognizer {
                var destroyed = false
                override fun start(request: RecognizerRequest, listener: RecognizerListener) {
                    requests += request
                    pending.removeFirstOrNull()?.invoke(listener)
                }
                override fun stop() = Unit
                override fun cancel() = Unit
                override fun destroy() {
                    if (!destroyed) alive--
                    destroyed = true
                }
            }
        }
    }

    @Volatile
    private var clock = 0L

    private fun input(fake: FakeRecognizers) = InAppVoiceInput(
        newRecognizer = fake::create,
        now = { clock },
        dispatcher = Dispatchers.Unconfined,
        pause = { fake.pauses += it }
    )

    private fun InAppVoiceInput.run(language: Language = Language.ES) = runBlocking { listen(language).toList() }

    @Test fun missingOfflineModelThenColdNetworkErrorStillEndsWithTheTranscript() {
        // The sequence measured on the Galaxy Watch5 Pro with a fresh app process: ready + 12 offline, then the cold
        // online session answers 2, then the retry works.
        val fake = FakeRecognizers(
            { it.onReady(); it.onError(RecognizerErrors.LANGUAGE_NOT_SUPPORTED) },
            { it.onReady(); it.onError(RecognizerErrors.NETWORK) },
            { it.onReady(); it.onBeginningOfSpeech(); it.onPartial("uni"); it.onResults("unicentro") }
        )
        val events = input(fake).run()
        // Each false "ready" is taken back (Restarting) so the UI never says "speak now" while nothing listens.
        assertEquals(
            listOf(
                VoiceEvent.Ready, VoiceEvent.Restarting, VoiceEvent.Ready, VoiceEvent.Restarting, VoiceEvent.Ready,
                VoiceEvent.Partial("uni"), VoiceEvent.Final("unicentro")
            ),
            events
        )
        assertEquals(
            listOf(RecognizerRequest("es-CO", true), RecognizerRequest("es-CO", false), RecognizerRequest("es-CO", false)),
            fake.requests
        )
        assertEquals("never two instances at once", 1, fake.maxAlive)
        assertEquals("all released", 0, fake.alive)
        assertEquals(listOf(RecognizerErrors.RESTART_DELAY_MS, RecognizerErrors.RESTART_DELAY_MS), fake.pauses)
    }

    @Test fun disconnectedTwiceIsCannotStartNeverNoInternet() {
        val fake = FakeRecognizers(
            { it.onError(RecognizerErrors.SERVER_DISCONNECTED) },
            { it.onError(RecognizerErrors.SERVER_DISCONNECTED) }
        )
        assertEquals(listOf(VoiceEvent.Failed(WgoFailure.NotStarted)), input(fake).run())
        assertEquals(2, fake.requests.size)
        assertEquals(1, fake.maxAlive)
    }

    @Test fun busyOnceIsRetriedSilently() {
        val fake = FakeRecognizers(
            { it.onError(RecognizerErrors.BUSY) },
            { it.onReady(); it.onResults("parque nariño") }
        )
        assertEquals(listOf(VoiceEvent.Ready, VoiceEvent.Final("parque nariño")), input(fake).run())
    }

    @Test fun languageTagsAreWalkedOnlineThenTheChainGetsUnavailable() {
        val twelve: (RecognizerListener) -> Unit = { it.onError(RecognizerErrors.LANGUAGE_NOT_SUPPORTED) }
        val fake = FakeRecognizers(twelve, twelve, twelve, twelve)
        assertEquals(listOf(VoiceEvent.Failed(WgoFailure.Unavailable)), input(fake).run())
        assertEquals(
            listOf(
                RecognizerRequest("es-CO", true), RecognizerRequest("es-CO", false),
                RecognizerRequest("es-419", false), RecognizerRequest("es", false)
            ),
            fake.requests
        )
    }

    @Test fun theNextSessionRemembersOnlineAndTheWorkingTag() {
        val fake = FakeRecognizers(
            { it.onError(RecognizerErrors.LANGUAGE_NOT_SUPPORTED) },
            { it.onError(RecognizerErrors.LANGUAGE_NOT_SUPPORTED) },
            { it.onReady(); it.onBeginningOfSpeech(); it.onResults("centro") },
            { it.onReady(); it.onResults("unicentro") }
        )
        val voice = input(fake)
        voice.run()
        voice.run()
        assertEquals(RecognizerRequest("es-419", false), fake.requests.last())
    }

    @Test fun aRestartBeforeReadySendsNoRestarting() {
        val fake = FakeRecognizers({ it.onError(RecognizerErrors.BUSY) }, { it.onReady(); it.onResults("centro") })
        assertEquals(listOf(VoiceEvent.Ready, VoiceEvent.Final("centro")), input(fake).run())
    }

    @Test fun theReadyTimeoutCountsPerAttempt() = runBlocking {
        // First attempt fails at 19 s; the second one must get its own 20 s, not the 1 s left of the first.
        val fake = FakeRecognizers({ clock = 19_000L; it.onError(RecognizerErrors.SERVER_DISCONNECTED) }, { })
        val voice = input(fake)
        val events: MutableList<VoiceEvent> = java.util.Collections.synchronizedList(mutableListOf())
        val job = launch(Dispatchers.Default) { voice.listen(Language.ES).toList(events) }
        clock = 25_000L
        Thread.sleep(400)
        assertEquals("still preparing 6 s into the second attempt", emptyList<VoiceEvent>(), events.toList())
        clock = 39_100L
        withTimeout(3_000) { job.join() }
        assertEquals(listOf(VoiceEvent.Failed(WgoFailure.NotStarted)), events)
    }

    @Test fun speechWithoutWordsBeforeAColdNetworkErrorRestartsAndCuesAgain() {
        // Measured: the cold online engine fails with 2 ("online no progress") even when the user spoke.
        val fake = FakeRecognizers(
            { it.onReady(); it.onBeginningOfSpeech(); it.onError(RecognizerErrors.NETWORK) },
            { it.onReady(); it.onPartial("uni"); it.onResults("udenar") }
        )
        assertEquals(
            listOf(VoiceEvent.Ready, VoiceEvent.Restarting, VoiceEvent.Ready, VoiceEvent.Partial("uni"), VoiceEvent.Final("udenar")),
            input(fake).run()
        )
    }

    @Test fun networkErrorWithWordsKeepsTheWords() {
        val fake = FakeRecognizers({ it.onReady(); it.onPartial("parque"); it.onError(RecognizerErrors.NETWORK) })
        assertEquals(listOf(VoiceEvent.Ready, VoiceEvent.Partial("parque"), VoiceEvent.Final("parque")), input(fake).run())
        assertEquals(1, fake.requests.size)
    }

    @Test fun networkErrorTwiceIsNoInternet() {
        val fake = FakeRecognizers({ it.onError(RecognizerErrors.NETWORK) }, { it.onError(RecognizerErrors.NETWORK) })
        assertEquals(listOf(VoiceEvent.Failed(WgoFailure.Offline)), input(fake).run())
    }

    @Test fun silenceKeepsThePartial() {
        val fake = FakeRecognizers({ it.onReady(); it.onPartial("terminal"); it.onError(RecognizerErrors.NO_MATCH) })
        assertEquals(listOf(VoiceEvent.Ready, VoiceEvent.Partial("terminal"), VoiceEvent.Final("terminal")), input(fake).run())
    }

    @Test fun englishUsesItsOwnTags() {
        val fake = FakeRecognizers({ it.onError(RecognizerErrors.LANGUAGE_UNAVAILABLE) }, { it.onReady(); it.onResults("downtown") })
        input(fake).run(Language.EN)
        assertEquals(listOf(RecognizerRequest("en-US", true), RecognizerRequest("en-US", false)), fake.requests)
    }
}

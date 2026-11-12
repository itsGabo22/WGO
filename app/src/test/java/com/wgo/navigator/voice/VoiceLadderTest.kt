package com.wgo.navigator.voice

import com.wgo.navigator.Language
import com.wgo.navigator.audio.AudioCapture
import com.wgo.navigator.audio.PcmSource
import com.wgo.navigator.audio.SyntheticPcm.concat
import com.wgo.navigator.audio.SyntheticPcm.noise
import com.wgo.navigator.audio.SyntheticPcm.speech
import com.wgo.navigator.audio.SyntheticPcm.zeros
import com.wgo.navigator.core.VoiceEvent
import com.wgo.navigator.core.VoiceInput
import com.wgo.navigator.core.WgoFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** The P9 ladder: cloud first, the (unchanged) system chain as the fallback, an error only after the last rung. */
class VoiceLadderTest {
    private class Mic(private val pcm: ShortArray, private val startOk: Boolean = true) : PcmSource {
        val released = AtomicInteger()
        private var pos = 0
        override fun start() = startOk
        override fun read(buffer: ShortArray, length: Int): Int {
            for (i in 0 until length) buffer[i] = if (pos + i < pcm.size) pcm[pos + i] else 0
            pos += length
            return length
        }
        override fun release() {
            released.incrementAndGet()
        }
    }

    private class System(private vararg val events: VoiceEvent) : VoiceInput {
        var sessions = 0
        override fun listen(language: Language): Flow<VoiceEvent> {
            sessions++
            return flowOf(*events)
        }
        override fun cancel() = Unit
    }

    private val spokenPcm = concat(zeros(200), noise(500, 20), speech(1200, 3000), noise(1500, 20))
    private val silentPcm = noise(7000, 20)

    private fun ladder(
        mic: Mic,
        system: System,
        transcriber: Transcriber,
        blocker: String? = null
    ): VoiceLadder {
        val cloud = CloudVoiceInput(
            AudioCapture({ mic }, dispatcher = Dispatchers.Unconfined), transcriber, CoroutineScope(Dispatchers.Unconfined)
        )
        return VoiceLadder(system, cloud, { blocker }, { VoiceStrategy.InApp }, io = Dispatchers.Unconfined)
    }

    private fun run(l: VoiceLadder) = runBlocking { l.listen(Language.ES).toList().filterNot { it is VoiceEvent.Level } }

    private val systemOk = System(VoiceEvent.Ready, VoiceEvent.Final("texto del sistema"))

    @Test fun cloudSuccessNeverTouchesTheSystemRecognizer() {
        val mic = Mic(spokenPcm)
        var uploads = 0
        val events = run(ladder(mic, systemOk, { wav, _ -> uploads++; assertEquals("RIFF", String(wav, 0, 4)); Transcription.Text("ir a la plaza") }))
        assertEquals(listOf(VoiceEvent.Ready, VoiceEvent.Transcribing, VoiceEvent.Final("ir a la plaza")), events)
        assertEquals(1, uploads)
        assertEquals(0, systemOk.sessions)
    }

    @Test fun theUploadedAudioIsZeroedAfterTheUpload() {
        var seen: ByteArray? = null
        run(ladder(Mic(spokenPcm), systemOk, { wav, _ -> seen = wav; Transcription.Text("x") }))
        assertTrue(seen!!.all { it == 0.toByte() })
    }

    @Test fun everyCloudFailureFallsBackToTheSystemChainOnce() {
        val failures = listOf(
            WgoFailure.Server(429), WgoFailure.Timeout, WgoFailure.Offline, WgoFailure.Server(200),
            WgoFailure.NotFound, WgoFailure.Unavailable
        )
        for (failure in failures) {
            val system = System(VoiceEvent.Ready, VoiceEvent.Final("texto del sistema"))
            val mic = Mic(spokenPcm)
            val events = run(ladder(mic, system, { _, _ -> Transcription.Failed(failure, "x") }))
            assertEquals(
                failure.toString(),
                listOf(VoiceEvent.Ready, VoiceEvent.Transcribing, VoiceEvent.SayAgain, VoiceEvent.Ready, VoiceEvent.Final("texto del sistema")),
                events
            )
            assertEquals(1, system.sessions)
            assertEquals("the capture released the mic before the recognizer", 1, mic.released.get())
        }
    }

    @Test fun anErrorIsShownOnlyAfterTheLastRung() {
        val system = System(VoiceEvent.Ready, VoiceEvent.Failed(WgoFailure.NotFound))
        val events = run(ladder(Mic(spokenPcm), system, { _, _ -> Transcription.Failed(WgoFailure.Timeout, "x") }))
        assertEquals(1, events.count { it is VoiceEvent.Failed })
        assertEquals(VoiceEvent.Failed(WgoFailure.NotFound), events.last())
    }

    @Test fun silenceEndsWithNotHeardWithoutNetworkOrFallback() {
        var uploads = 0
        val events = run(ladder(Mic(silentPcm), systemOk, { _, _ -> uploads++; Transcription.Text("x") }))
        assertEquals(listOf(VoiceEvent.Ready, VoiceEvent.Failed(WgoFailure.NoSpeech)), events)
        assertEquals(0, uploads)
        assertEquals(0, systemOk.sessions)
    }

    @Test fun aMicrophoneThatCannotOpenFallsBack() {
        val events = run(ladder(Mic(spokenPcm, startOk = false), systemOk, { _, _ -> Transcription.Text("x") }))
        assertEquals(listOf(VoiceEvent.Ready, VoiceEvent.SayAgain, VoiceEvent.Ready, VoiceEvent.Final("texto del sistema")), events)
    }

    @Test fun aBlockedCloudRungGoesStraightToTheSystemChain() {
        var uploads = 0
        val l = ladder(Mic(spokenPcm), systemOk, { _, _ -> uploads++; Transcription.Text("x") }, blocker = "offline")
        assertFalse(l.usesCloud())
        assertEquals(listOf(VoiceEvent.Ready, VoiceEvent.Final("texto del sistema")), run(l))
        assertEquals(0, uploads)
    }

    @Test fun withoutATranscriberOnlyTheSystemChainExists() {
        val l = VoiceLadder(systemOk, null, { null }, { VoiceStrategy.InApp })
        assertFalse(l.usesCloud())
        assertEquals(listOf(VoiceEvent.Ready, VoiceEvent.Final("texto del sistema")), run(l))
    }

    /** Counts prewarms; transcribes with [answer]. */
    private class Counting(private val answer: Transcription) : Transcriber {
        var prewarms = 0
        var uploads = 0
        override suspend fun transcribe(wav: ByteArray, language: Language): Transcription {
            uploads++
            return answer
        }
        override suspend fun prewarm() {
            prewarms++
        }
    }

    @Test fun theConnectionIsWarmedOnlyOnceSpeechIsHeard() {
        val silent = Counting(Transcription.Text("x"))
        run(ladder(Mic(silentPcm), systemOk, silent))
        assertEquals("a silent press makes no request at all", 0, silent.prewarms + silent.uploads)
        val spoken = Counting(Transcription.Text("x"))
        run(ladder(Mic(spokenPcm), systemOk, spoken))
        assertEquals(1, spoken.prewarms)
        assertEquals(1, spoken.uploads)
    }

    @Test fun onlySilenceAndCancelDoNotFallBack() {
        assertFalse(VoiceLadder.fallsBack(WgoFailure.NoSpeech))
        assertFalse(VoiceLadder.fallsBack(WgoFailure.Cancelled))
        assertTrue(VoiceLadder.fallsBack(WgoFailure.NotStarted))
        assertTrue(VoiceLadder.fallsBack(WgoFailure.Server(429)))
    }
}

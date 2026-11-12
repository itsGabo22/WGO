package com.wgo.navigator.audio

import com.wgo.navigator.audio.SyntheticPcm.concat
import com.wgo.navigator.audio.SyntheticPcm.noise
import com.wgo.navigator.audio.SyntheticPcm.tone
import com.wgo.navigator.audio.SyntheticPcm.zeros
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AudioCaptureTest {
    /** Plays [pcm] (then silence forever) and records how it was used. */
    private class FakeSource(private val pcm: ShortArray, private val startOk: Boolean = true, private val failAt: Int = -1) : PcmSource {
        var started = 0
        val released = AtomicInteger()
        val reads = AtomicInteger()
        private var pos = 0

        override fun start(): Boolean {
            started++
            return startOk
        }

        override fun read(buffer: ShortArray, length: Int): Int {
            if (reads.incrementAndGet() == failAt) return -3
            for (i in 0 until length) buffer[i] = if (pos + i < pcm.size) pcm[pos + i] else 0
            pos += length
            return length
        }

        override fun release() {
            released.incrementAndGet()
        }
    }

    private fun capture(src: FakeSource) = AudioCapture({ src }, dispatcher = Dispatchers.Unconfined)

    /** Records with [c] and waits for the background release. */
    private suspend fun AudioCapture.recordAndRelease(
        onLevel: (Float) -> Unit = {},
        onSpeechStart: () -> Unit = {}
    ): AudioCapture.Result = record(onLevel, onSpeechStart).also { awaitReleased() }

    @Test fun speechGivesAWavClipAndReleasesTheMicrophone() = runBlocking {
        val src = FakeSource(concat(zeros(200), noise(600, 20), tone(1000, 3000), noise(1500, 20)))
        var speechStarts = 0
        val levels = mutableListOf<Float>()
        val r = capture(src).recordAndRelease(onLevel = { levels += it }, onSpeechStart = { speechStarts++ })
        assertTrue(r is AudioCapture.Result.Speech)
        r as AudioCapture.Result.Speech
        assertEquals("RIFF", String(r.wav, 0, 4))
        assertEquals(1800L, r.clipMs) // 500 ms pre-roll + 1000 ms of speech + 300 ms tail
        assertEquals(1, speechStarts)
        assertTrue(levels.max() > 0.5f)
        assertEquals(1, src.released.get())
    }

    @Test fun silenceSendsNothingAndReleases() = runBlocking {
        val src = FakeSource(noise(500, 20))
        val r = capture(src).recordAndRelease()
        assertEquals(AudioCapture.Result.NoSpeech, r)
        assertEquals(1, src.released.get())
    }

    @Test fun aMicrophoneThatDoesNotStartIsAFailureAndIsReleased() = runBlocking {
        val src = FakeSource(ShortArray(0), startOk = false)
        assertEquals(AudioCapture.Result.Failed("start"), capture(src).recordAndRelease())
        assertEquals(1, src.released.get())
    }

    @Test fun aReadErrorIsAFailureAndIsReleased() = runBlocking {
        val src = FakeSource(noise(2000, 20), failAt = 10)
        assertEquals(AudioCapture.Result.Failed("read"), capture(src).recordAndRelease())
        assertEquals(1, src.released.get())
    }

    @Test fun cancellingTheCollectorReleasesTheMicrophone() = runBlocking {
        val src = FakeSource(noise(60_000, 20))
        val c = AudioCapture({ src }, dispatcher = Dispatchers.Default)
        val job = launch(Dispatchers.Default) { c.record() }
        while (src.reads.get() < 20) yield()
        job.cancelAndJoin()
        c.awaitReleased()
        assertEquals(1, src.released.get())
    }

    @Test fun manualStopEndsTheCaptureWithWhatWasHeard() = runBlocking {
        val src = FakeSource(concat(noise(400, 20), tone(30_000, 3000)))
        val c = AudioCapture({ src }, dispatcher = Dispatchers.Default)
        var r: AudioCapture.Result? = null
        val job = launch(Dispatchers.Default) { r = c.record(onSpeechStart = { c.requestStop() }) }
        job.join()
        c.awaitReleased()
        assertTrue(r is AudioCapture.Result.Speech)
        assertEquals(SpeechEndpointer.EndReason.Manual, (r as AudioCapture.Result.Speech).reason)
        assertEquals(1, src.released.get())
    }
}

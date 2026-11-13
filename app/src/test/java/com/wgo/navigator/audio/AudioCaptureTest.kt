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
        assertEquals("RIFF", String(r.audio, 0, 4))
        assertEquals(1800L, r.clipMs) // 500 ms pre-roll + 1000 ms of speech + 300 ms tail
        assertEquals(1, speechStarts)
        assertTrue(levels.max() > 0.5f)
        assertEquals(1, src.released.get())
    }

    /** Records what it was fed; answers "fLaC" + the sample count, or fails where asked. */
    private class FakeEncoder(val startOk: Boolean = true, val feedOk: Boolean = true, val finishOk: Boolean = true) : StreamEncoder {
        val ranges = ArrayList<IntRange>()
        var released = 0
        override fun start() = startOk
        override fun feed(pcm: ShortArray, from: Int, to: Int): Boolean {
            ranges += from until to
            return feedOk
        }
        override fun finish(): ByteArray? = if (finishOk) "fLaC".toByteArray() + ByteArray(ranges.sumOf { it.count() }) else null
        override fun release() {
            released++
        }
    }

    private val spoken = concat(zeros(200), noise(600, 20), tone(1000, 3000), noise(1500, 20))

    private fun encoded(enc: FakeEncoder) = runBlocking {
        AudioCapture({ FakeSource(spoken) }, dispatcher = Dispatchers.Unconfined, newEncoder = { enc }).recordAndRelease()
    }

    @Test fun theCompressedClipCoversThePreRollToTheEndAndIsUsed() {
        val enc = FakeEncoder()
        val r = encoded(enc) as AudioCapture.Result.Speech
        assertEquals("fLaC", String(r.audio, 0, 4))
        val first = enc.ranges.first().first
        val last = enc.ranges.last().last + 1
        assertEquals("starts at the pre-roll (speech at 800 ms - 500 ms)", SyntheticPcm.samples(300), first)
        assertTrue("reaches the clip end (1800 ms + 300 ms tail)", last >= SyntheticPcm.samples(2100))
        assertTrue("never past the clip end + 300 ms", last <= SyntheticPcm.samples(2400))
        // Contiguous, in order: no sample twice, none skipped.
        enc.ranges.zipWithNext().forEach { (a, b) -> assertEquals(a.last + 1, b.first) }
        assertEquals(1, enc.released)
    }

    @Test fun anyEncoderFailureFallsBackToTheWav() {
        for (enc in listOf(FakeEncoder(startOk = false), FakeEncoder(feedOk = false), FakeEncoder(finishOk = false))) {
            val r = encoded(enc) as AudioCapture.Result.Speech
            assertEquals("RIFF", String(r.audio, 0, 4))
            assertTrue("released", enc.released >= 1)
        }
    }

    @Test fun silenceNeverStartsAnEncoder() {
        var created = 0
        val r = runBlocking {
            AudioCapture({ FakeSource(noise(500, 20)) }, dispatcher = Dispatchers.Unconfined, newEncoder = { created++; FakeEncoder() }).recordAndRelease()
        }
        assertEquals(AudioCapture.Result.NoSpeech, r)
        assertEquals(0, created)
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

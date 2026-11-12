package com.wgo.navigator.audio

import com.wgo.navigator.audio.SpeechEndpointer.EndReason
import com.wgo.navigator.audio.SpeechEndpointer.Step
import com.wgo.navigator.audio.SyntheticPcm.concat
import com.wgo.navigator.audio.SyntheticPcm.noise
import com.wgo.navigator.audio.SyntheticPcm.run
import com.wgo.navigator.audio.SyntheticPcm.samples
import com.wgo.navigator.audio.SyntheticPcm.speech
import com.wgo.navigator.audio.SyntheticPcm.tone
import com.wgo.navigator.audio.SyntheticPcm.zeros
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechEndpointerTest {
    /** A quiet room on the Galaxy Watch5 Pro measured rms 8-15 (about -68 dBFS). */
    private val room = 20

    /** Speech at arm's length: about -24 dBFS. */
    private val voice = 3000

    private fun ended(steps: List<Step>) = (steps.last() as? Step.Ended)?.reason

    @Test fun silenceEndsAsNoSpeechAfterSixSecondsWithNothingToSend() {
        val e = SpeechEndpointer()
        val steps = run(e, concat(zeros(200), noise(9000, room)))
        assertEquals(EndReason.NoSpeech, ended(steps))
        assertEquals(samples(6000).toLong(), e.totalSamples)
        assertFalse(e.hasSpeech)
        assertNull(e.clip())
    }

    @Test fun leadingZeroFramesAreTheMicrophoneRampNotAudio() {
        val e = SpeechEndpointer()
        run(e, concat(zeros(200), noise(1000, room)))
        assertEquals(samples(200).toLong(), e.firstAudioSample)
        assertTrue("floor calibrated from real audio", e.floorDb < -60f)
    }

    @Test fun speechStartsEndsAndClipKeepsPreRollAndTail() {
        val e = SpeechEndpointer()
        val pcm = concat(zeros(200), noise(800, room), tone(1200, voice), noise(2000, room))
        val steps = run(e, pcm)
        assertEquals(EndReason.SpeechEnded, ended(steps))
        val start = steps.filterIsInstance<Step.SpeechStarted>().single().startSample
        assertEquals(samples(1000).toLong(), start)
        // Ends 900 ms after the last loud frame (speech stops at 2200 ms).
        assertEquals(samples(2200 + 900).toLong(), e.totalSamples)
        val clip = e.clip()!!
        assertEquals(samples(500).toLong(), clip.first) // 500 ms pre-roll before 1000 ms
        assertEquals(samples(2200 + 300).toLong(), clip.last + 1) // 300 ms tail
    }

    @Test fun preRollNeverReachesIntoTheMicrophoneRamp() {
        val e = SpeechEndpointer()
        run(e, concat(zeros(200), noise(300, room), tone(1000, voice), noise(1500, room)))
        assertEquals(samples(200).toLong(), e.clip()!!.first)
    }

    @Test fun aClickShorterThan150msIsNotSpeech() {
        val e = SpeechEndpointer()
        val steps = run(e, concat(noise(500, room), tone(100, voice), noise(1000, room)))
        assertTrue(steps.none { it is Step.SpeechStarted })
    }

    @Test fun oneQuietFrameInsideTheStartRunIsTolerated() {
        val e = SpeechEndpointer()
        val steps = run(e, concat(noise(500, room), tone(80, voice), noise(20, room), tone(100, voice), noise(1500, room)))
        val start = steps.filterIsInstance<Step.SpeechStarted>().single().startSample
        assertEquals(samples(500).toLong(), start)
    }

    @Test fun talkingOverTheCalibrationIsStillDetected() {
        val e = SpeechEndpointer()
        val steps = run(e, concat(tone(1500, voice), noise(1500, room)))
        assertTrue(e.floorDb <= -55f)
        assertTrue(steps.any { it is Step.SpeechStarted })
        assertEquals(EndReason.SpeechEnded, ended(steps))
        assertEquals(0L, e.clip()!!.first)
    }

    @Test fun aSteadyLoudBackgroundIsFollowedAndSpeechOverItStillEnds() {
        val tv = 600 // about -39 dBFS of steady background, louder than the -55 dBFS calibration cap
        val e = SpeechEndpointer()
        val steps = run(e, concat(noise(2000, tv), speech(1500, 12000), noise(3000, tv)))
        assertTrue(steps.any { it is Step.SpeechStarted })
        assertEquals(EndReason.SpeechEnded, ended(steps))
        assertTrue("floor follows the background", e.floorDb > -46f)
        // Ends within the background's first 1.5 s + 900 ms after the speech, long before the 10 s cap.
        assertTrue(e.totalSamples <= samples(2000 + 1500 + 2500))
    }

    @Test fun speechWithGapsBetweenSyllablesKeepsTheFloorBelowIt() {
        val e = SpeechEndpointer()
        val steps = run(e, concat(noise(500, room), speech(4000, voice), noise(1500, room)))
        assertEquals(EndReason.SpeechEnded, ended(steps))
        assertTrue("floor stays well below the speech level (-24 dBFS)", e.floorDb < -40f)
        assertTrue(e.lastSpeechSample >= samples(4300))
    }

    @Test fun continuousSpeechStopsAtTheTenSecondCap() {
        val e = SpeechEndpointer()
        val steps = run(e, concat(noise(400, room), speech(12_000, voice)))
        assertEquals(EndReason.MaxLength, ended(steps))
        assertEquals(samples(10_000).toLong(), e.totalSamples)
        assertEquals(samples(10_000).toLong(), e.clip()!!.last + 1)
    }

    @Test fun manualStopKeepsWhatWasHeard() {
        val e = SpeechEndpointer()
        run(e, concat(noise(400, room), tone(700, voice)))
        assertEquals(EndReason.Manual, e.stop())
        assertNotNull(e.clip())
        val quiet = SpeechEndpointer()
        run(quiet, noise(1000, room))
        assertEquals(EndReason.Manual, quiet.stop())
        assertNull(quiet.clip())
    }

    @Test fun pushAfterTheEndKeepsReportingTheEnd() {
        val e = SpeechEndpointer()
        run(e, noise(7000, room))
        assertEquals(Step.Ended(EndReason.NoSpeech), e.push(tone(20, voice)))
    }

    @Test fun deterministicForTheSameAudio() {
        val pcm = concat(zeros(200), noise(800, room, seed = 3), tone(900, voice), noise(1200, room, seed = 5))
        val a = SpeechEndpointer().also { run(it, pcm) }
        val b = SpeechEndpointer().also { run(it, pcm) }
        assertEquals(a.clip(), b.clip())
        assertEquals(a.totalSamples, b.totalSamples)
        assertEquals(a.floorDb, b.floorDb, 0f)
    }

    @Test fun levelIsZeroInSilenceAndHighForSpeech() {
        val e = SpeechEndpointer()
        run(e, noise(500, room))
        assertTrue(e.level01() < 0.1f)
        e.push(tone(20, voice))
        assertTrue(e.level01() > 0.8f)
    }

    @Test fun dbfsOfFullScaleAndSilence() {
        assertEquals(0f, SpeechEndpointer.dbfs(32768.0), 1e-4f)
        assertEquals(SpeechEndpointer.SILENCE_DB, SpeechEndpointer.dbfs(0.0), 0f)
    }
}

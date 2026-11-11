package com.wgo.navigator.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeakNowCueTest {
    private var clock = 1_000L
    private val cue = SpeakNowCue { clock }

    @Test fun readyWithin600MsAfterThePressPlaysNoCue() {
        cue.onPress()
        clock += 600
        assertFalse(cue.onReady())
    }

    @Test fun readyAfterMoreThan600MsPlaysTheCue() {
        cue.onPress()
        clock += 601
        assertTrue(cue.onReady())
    }

    @Test fun slowColdStartsCue() {
        for (ms in listOf(886L, 2_400L, 9_154L, 18_818L)) { // preparing durations measured on the Galaxy Watch5 Pro
            cue.onPress()
            clock += ms
            assertTrue("$ms ms", cue.onReady())
        }
    }

    @Test fun atMostOneCuePerPress() {
        cue.onPress()
        clock += 5_000
        assertTrue(cue.onReady())
        clock += 5_000
        assertFalse(cue.onReady())
    }

    @Test fun noPressNoCue() {
        clock += 10_000
        assertFalse(cue.onReady())
        assertEquals(-1L, cue.preparingMs())
    }

    @Test fun aRestartArmsTheCueAgainFromTheRestart() {
        // Back to preparing after a false ready: the next ready is measured from the restart.
        cue.onPress()
        clock += 3_000
        assertTrue(cue.onReady())
        cue.onPress() // VoiceEvent.Restarting
        clock += 400
        assertEquals(400L, cue.preparingMs())
        assertFalse(cue.onReady())
    }
}

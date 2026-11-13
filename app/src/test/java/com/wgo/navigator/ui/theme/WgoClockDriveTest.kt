package com.wgo.navigator.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class WgoClockDriveTest {
    @Test
    fun noConsumersMeansNoFrameLoop() {
        assertEquals(ClockDrive.Stopped, clockDrive(reduceMotion = false, resumed = true, fullUsers = 0, ambienceUsers = 0))
    }

    @Test
    fun ambienceOnlyRunsAtTheReducedRate() {
        assertEquals(ClockDrive.Ambience, clockDrive(false, true, fullUsers = 0, ambienceUsers = 2))
    }

    @Test
    fun anyFullRateConsumerWins() {
        assertEquals(ClockDrive.Full, clockDrive(false, true, fullUsers = 1, ambienceUsers = 3))
    }

    @Test
    fun pausedBelowResumed() {
        assertEquals(ClockDrive.Stopped, clockDrive(false, resumed = false, fullUsers = 1, ambienceUsers = 1))
    }

    @Test
    fun reduceMotionStopsEverything() {
        assertEquals(ClockDrive.Stopped, clockDrive(reduceMotion = true, resumed = true, fullUsers = 1, ambienceUsers = 1))
    }
}

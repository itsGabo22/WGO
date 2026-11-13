package com.wgo.navigator.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapPickerMathTest {
    @Test fun activeIndexRoundsAndWraps() {
        assertEquals(0, MapPickerMath.activeIndex(0f))
        assertEquals(3, MapPickerMath.activeIndex(2.6f))
        assertEquals(0, MapPickerMath.activeIndex(8f))
        assertEquals(7, MapPickerMath.activeIndex(-1f))
        assertEquals(1, MapPickerMath.activeIndex(-7.4f))
        assertEquals(2, MapPickerMath.activeIndex(18f))
    }

    @Test fun ringRotatesMinus45DegreesPerStyle() {
        assertEquals(0f, MapPickerMath.ringRotation(0f), 0.001f)
        assertEquals(-45f, MapPickerMath.ringRotation(1f), 0.001f)
        assertEquals(-112.5f, MapPickerMath.ringRotation(2.5f), 0.001f)
    }

    @Test fun neighboursWrapAround() {
        assertEquals(7, MapPickerMath.neighbour(0, -1))
        assertEquals(0, MapPickerMath.neighbour(7, 1))
        assertEquals(3, MapPickerMath.neighbour(2, 1))
    }

    @Test fun slowReleaseSnapsToTheNearestStyle() {
        assertEquals(3f, MapPickerMath.snapTarget(2.6f, 100f), 0.001f)
        assertEquals(2f, MapPickerMath.snapTarget(2.4f, -100f), 0.001f)
    }

    @Test fun flickMovesOneStepInItsDirection() {
        // finger moves right (positive) -> previous style (k decreases)
        assertEquals(2f, MapPickerMath.snapTarget(3f, 1500f), 0.001f)
        assertEquals(3f, MapPickerMath.snapTarget(3.3f, 1500f), 0.001f)
        // finger moves left (negative) -> next style (k increases)
        assertEquals(4f, MapPickerMath.snapTarget(3f, -1500f), 0.001f)
        assertEquals(4f, MapPickerMath.snapTarget(3.3f, -1500f), 0.001f)
    }

    @Test fun draggingRightGoesToThePreviousStyle() {
        assertEquals(-0.5f, MapPickerMath.indexDelta(40f, 80f), 0.001f)
        assertEquals(1f, MapPickerMath.indexDelta(-80f, 80f), 0.001f)
    }

    @Test fun irisRadiusClampsProgress() {
        assertEquals(0f, MapPickerMath.irisRadius(52f, -0.3f), 0.001f)
        assertEquals(26f, MapPickerMath.irisRadius(52f, 0.5f), 0.001f)
        assertEquals(52f, MapPickerMath.irisRadius(52f, 1.7f), 0.001f)
    }

    @Test fun stepsCloserThan150MsAreIgnored() {
        val throttle = StepThrottle()
        assertTrue(throttle.accept(1000))
        assertFalse(throttle.accept(1100))
        assertTrue(throttle.accept(1150))
        assertFalse(throttle.accept(1200))
    }

    @Test fun rotaryAccumulatesFortyPixelsPerStep() {
        val rotary = RotaryAccumulator()
        assertEquals(0, rotary.add(15f))
        assertEquals(0, rotary.add(15f))
        assertEquals(1, rotary.add(15f))
        assertEquals(-1, rotary.add(-45f))
        assertEquals(0, rotary.add(-10f))
    }
}

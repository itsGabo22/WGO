package com.wgo.navigator.heading

import com.wgo.navigator.Language
import com.wgo.navigator.ui.StringKey
import com.wgo.navigator.ui.stringFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/** P11 heading fusion (docs/ARCHITECTURE.md "Heading fusion"): pure rules, no sensor. */
class HeadingFusionTest {
    // ---- quaternions (x, y, z, w) as the rotation vector sensor gives them ----

    private fun about(axis: DoubleArray, deg: Double): DoubleArray {
        val h = Math.toRadians(deg) / 2
        return doubleArrayOf(axis[0] * sin(h), axis[1] * sin(h), axis[2] * sin(h), cos(h))
    }

    /** a * b (apply b, then a). */
    private fun mul(a: DoubleArray, b: DoubleArray) = doubleArrayOf(
        a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
        a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
        a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
        a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]
    )

    private val zAxis = doubleArrayOf(0.0, 0.0, 1.0)
    private val xAxis = doubleArrayOf(1.0, 0.0, 0.0)

    /** A watch facing [heading] (its 12 o'clock), its top edge raised by [tilt] degrees toward the user. */
    private fun watch(heading: Double, tilt: Double): FloatArray {
        val q = mul(about(zAxis, -heading), about(xAxis, tilt))
        return floatArrayOf(q[0].toFloat(), q[1].toFloat(), q[2].toFloat(), q[3].toFloat())
    }

    @Test fun azimuthFollowsTheWatchFlatOrRaisedWithoutFlipping() {
        for (heading in listOf(0.0, 45.0, 90.0, 180.0, 270.0, 359.0)) {
            for (tilt in listOf(0.0, 30.0, 60.0, 80.0, 90.0, 100.0)) {
                val az = HeadingMath.azimuthFromRotationVector(watch(heading, tilt))!!
                assertEquals("heading $heading tilt $tilt", 0.0, HeadingMath.diff(heading, az), 0.5)
            }
        }
        // Without the scalar part (older sensors): reconstructed.
        val v = watch(90.0, 20.0)
        assertEquals(90.0, HeadingMath.azimuthFromRotationVector(floatArrayOf(v[0], v[1], v[2]))!!, 0.5)
    }

    @Test fun anglesWrapAroundNorth() {
        assertEquals(20.0, HeadingMath.diff(350.0, 10.0), 1e-9)
        assertEquals(-20.0, HeadingMath.diff(10.0, 350.0), 1e-9)
        assertEquals(0.0, HeadingMath.blend(350.0, 10.0, 0.5), 1e-9)
        assertEquals(355.0, HeadingMath.blend(350.0, 10.0, 0.25), 1e-9)
        assertEquals(10.0, HeadingMath.normalize(-350.0), 1e-9)
    }

    @Test fun theLowPassCrossesNorthTheShortWay() {
        val f = HeadingFusion()
        f.onCompass(350.0, 3, 0.0, 0L)
        repeat(30) { f.onCompass(10.0, 3, 0.0, it * 60L) }
        val c = f.compassDeg!!
        assertTrue("$c", c < 11.0 || c > 359.0)
        assertTrue(kotlin.math.abs(HeadingMath.diff(10.0, c)) < 1.0)
    }

    @Test fun declinationTurnsMagneticIntoTrueNorth() {
        val f = HeadingFusion(alpha = 1.0)
        f.onCompass(100.0, 3, -6.5, 0L) // Pasto: about 6.5 degrees west
        assertEquals(93.5, f.compassDeg!!, 1e-9)
    }

    @Test fun gpsWhenFastCompassWhenSlowBlendBetween() {
        val f = HeadingFusion(alpha = 1.0)
        f.onCompass(0.0, 3, 0.0, 0L)
        assertEquals(0.0, f.fused(0.5, 90.0)!!, 1e-9)
        assertEquals(0.0, f.fused(0.0, Double.NaN)!!, 1e-9)
        assertEquals(90.0, f.fused(3.0, 90.0)!!, 1e-9)
        assertEquals(90.0, f.fused(15.0, 90.0)!!, 1e-9)
        assertEquals(45.0, f.fused(2.0, 90.0)!!, 1e-9) // halfway between 1 and 3 m/s
        assertEquals(0.0, f.fused(5.0, Double.NaN)!!, 1e-9) // no course: the compass
        val noCompass = HeadingFusion()
        assertEquals(90.0, noCompass.fused(0.0, 90.0)!!, 1e-9)
        assertNull(noCompass.fused(0.0, Double.NaN))
    }

    @Test fun unreliableSamplesAreDroppedAndLowAccuracyAsksForAFigureEight() {
        val f = HeadingFusion(alpha = 1.0)
        f.onCompass(50.0, 3, 0.0, 0L)
        f.onCompass(200.0, 0, 0.0, 100L) // unreliable: ignored
        assertEquals(50.0, f.compassDeg!!, 1e-9)
        f.onCompass(55.0, 3, 0.0, 500L) // good again: the low-accuracy clock restarts
        assertFalse(f.needsCalibration)
        f.onCompass(60.0, 1, 0.0, 1_000L) // low: still used
        assertEquals(60.0, f.compassDeg!!, 1e-9)
        f.onCompass(60.0, 1, 0.0, 5_999L)
        assertFalse(f.needsCalibration)
        f.onCompass(60.0, 0, 0.0, 6_000L)
        assertTrue(f.needsCalibration) // 5 s at LOW or worse
        f.onCompass(61.0, 2, 0.0, 6_100L)
        assertFalse(f.needsCalibration)
        f.reset()
        assertNull(f.compassDeg)
    }

    @Test fun theMapGetsAtMostTenHeadingsASecondAndNoNoise() {
        val t = HeadingThrottle()
        assertNotNull(t.next(10.0, 0L))
        assertNull(t.next(30.0, 50L)) // too soon
        assertNull(t.next(11.0, 200L)) // under 2 degrees
        assertNotNull(t.next(13.0, 300L))
        var emitted = 0
        for (ms in 1_000L until 2_000L step 16) if (t.next((ms / 16 % 360).toDouble() * 5, ms) != null) emitted++
        assertTrue("$emitted", emitted <= 10)
    }

    @Test fun turnToFaceOnlyWhileSlowAndUntilTheUserHasMoved() {
        val r = FaceRule()
        assertNull(r.face(0.0, 0.0, 0.0, 90.0)) // closed before the trip opens it
        r.open()
        assertEquals(90.0, r.face(10.0, 0.0, 0.0, 90.0)!!, 1e-9)
        assertEquals(-90.0, r.face(12.0, 1.0, 180.0, 90.0)!!, 1e-9)
        assertNull(r.face(15.0, 2.5, 0.0, 90.0)) // too fast to show, still open
        assertNull(r.face(15.0, 0.0, null, 90.0)) // no heading
        assertNotNull(r.face(39.0, 0.0, 0.0, 90.0))
        assertNull(r.face(40.0, 0.0, 0.0, 90.0)) // 30 m of progress: closed
        assertNull(r.face(40.0, 0.0, 0.0, 90.0))
        r.open() // a new route
        assertNotNull(r.face(500.0, 0.0, 0.0, 90.0))
        assertNull(r.face(501.0, 3.5, 0.0, 90.0)) // driving away: closed
    }

    @Test fun theCompassListensOnlyDuringAnInteractiveTrip() {
        assertTrue(CompassPolicy.shouldListen(tripActive = true, visible = true, ambient = false))
        assertFalse(CompassPolicy.shouldListen(tripActive = false, visible = true, ambient = false)) // Home
        assertFalse(CompassPolicy.shouldListen(tripActive = true, visible = true, ambient = true)) // ambient
        assertFalse(CompassPolicy.shouldListen(tripActive = true, visible = false, ambient = false)) // screen off
    }

    @Test fun compassTextsExistInSpanishAndEnglish() {
        assertEquals("MUEVE EL RELOJ EN 8", stringFor(StringKey.COMPASS_CALIBRATE, Language.ES))
        assertEquals("MOVE THE WATCH IN A FIGURE 8", stringFor(StringKey.COMPASS_CALIBRATE, Language.EN))
        assertEquals("GIRA HACIA LA RUTA", stringFor(StringKey.FACE_ROUTE, Language.ES))
        assertEquals("TURN TOWARD THE ROUTE", stringFor(StringKey.FACE_ROUTE, Language.EN))
    }
}

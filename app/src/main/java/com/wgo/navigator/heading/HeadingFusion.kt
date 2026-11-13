package com.wgo.navigator.heading

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** Angles in degrees clockwise from north, kept in 0..360 (P11, docs/ARCHITECTURE.md "Heading fusion"). */
object HeadingMath {
    fun normalize(deg: Double): Double = ((deg % 360.0) + 360.0) % 360.0

    /** The signed shortest turn from [from] to [to], in -180..180. */
    fun diff(from: Double, to: Double): Double {
        var d = normalize(to) - normalize(from)
        if (d > 180) d -= 360
        if (d < -180) d += 360
        return d
    }

    /** [a] moved toward [b] by [w] (0..1) along the circle (never through the long way round). */
    fun blend(a: Double, b: Double, w: Double): Double = normalize(a + diff(a, b) * w.coerceIn(0.0, 1.0))

    /**
     * The magnetic azimuth of a watch from a rotation vector sensor sample ([v]: x, y, z and optionally the scalar w, as
     * Android gives them): the rotation matrix from the quaternion (as `SensorManager.getRotationMatrixFromVector`),
     * then the horizontal direction of the watch's 12 o'clock axis (y) plus its into-the-screen axis (-z). In the
     * reading posture the 12 o'clock points away from the user; raised until the screen is near vertical, the
     * into-the-screen axis takes over, so the sum never flips. Null when both are vertical (no horizontal direction).
     */
    fun azimuthFromRotationVector(v: FloatArray): Double? {
        if (v.size < 3) return null
        val x = v[0].toDouble()
        val y = v[1].toDouble()
        val z = v[2].toDouble()
        val w = if (v.size >= 4) v[3].toDouble() else sqrt((1.0 - x * x - y * y - z * z).coerceAtLeast(0.0))
        // Rows of R (world East, North, Up) x columns (device x, y, z).
        val r1 = 2 * x * y - 2 * z * w          // East component of device y
        val r2 = 2 * x * z + 2 * y * w          // East component of device z
        val r4 = 1 - 2 * x * x - 2 * z * z      // North component of device y
        val r5 = 2 * y * z - 2 * x * w          // North component of device z
        val east = r1 - r2
        val north = r4 - r5
        if (hypot(east, north) < 1e-3) return null
        return normalize(Math.toDegrees(atan2(east, north)))
    }

    /** Initial bearing from (lat1, lon1) to (lat2, lon2), degrees clockwise from north. */
    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return normalize(Math.toDegrees(atan2(y, x)))
    }
}

/**
 * The heading the map uses (P11): the watch compass when slow, the GPS course when fast, blended in between, low-pass
 * filtered on the circle. Pure: time and samples come from the caller ([CompassSensor] and the fixes).
 */
class HeadingFusion(private val alpha: Double = ALPHA) {
    /** The low-passed TRUE compass heading (null until a reliable sample). */
    var compassDeg: Double? = null
        private set

    private var lowSinceMs: Long? = null

    /** True while the compass accuracy has stayed LOW or worse for [CALIBRATION_AFTER_MS]: show the figure-8 hint. */
    var needsCalibration: Boolean = false
        private set

    /**
     * One compass sample: [magneticDeg] (from [HeadingMath.azimuthFromRotationVector]), the sensor [accuracy]
     * (0 unreliable .. 3 high, Android's `SENSOR_STATUS_*`), the [declinationDeg] at the trip origin, at [nowMs].
     * Unreliable samples are dropped.
     */
    fun onCompass(magneticDeg: Double?, accuracy: Int, declinationDeg: Double, nowMs: Long) {
        if (accuracy <= ACCURACY_LOW) {
            val since = lowSinceMs ?: nowMs.also { lowSinceMs = it }
            needsCalibration = nowMs - since >= CALIBRATION_AFTER_MS
        } else {
            lowSinceMs = null
            needsCalibration = false
        }
        if (magneticDeg == null || accuracy <= ACCURACY_UNRELIABLE) return
        val trueDeg = HeadingMath.normalize(magneticDeg + declinationDeg)
        val prev = compassDeg
        compassDeg = if (prev == null) trueDeg else HeadingMath.blend(prev, trueDeg, alpha)
    }

    /**
     * The fused heading for a user moving at [speedMps] with GPS [courseDeg] (NaN when unknown): the course at or above
     * [GPS_ONLY_MPS], the compass below [COMPASS_ONLY_MPS], a blend in between; whichever exists when one is missing.
     */
    fun fused(speedMps: Double, courseDeg: Double): Double? {
        val compass = compassDeg
        val course = courseDeg.takeUnless { it.isNaN() }?.let(HeadingMath::normalize)
        if (compass == null) return course
        if (course == null || speedMps.isNaN() || speedMps < COMPASS_ONLY_MPS) return compass
        if (speedMps >= GPS_ONLY_MPS) return course
        val w = (speedMps - COMPASS_ONLY_MPS) / (GPS_ONLY_MPS - COMPASS_ONLY_MPS)
        return HeadingMath.blend(compass, course, w)
    }

    /** Forgets the samples (the listener stopped): a stale heading is never reused. */
    fun reset() {
        compassDeg = null
        lowSinceMs = null
        needsCalibration = false
    }

    companion object {
        const val ALPHA = 0.25
        const val GPS_ONLY_MPS = 3.0
        const val COMPASS_ONLY_MPS = 1.0
        const val CALIBRATION_AFTER_MS = 5_000L

        /** Android's SENSOR_STATUS_UNRELIABLE and SENSOR_STATUS_ACCURACY_LOW. */
        const val ACCURACY_UNRELIABLE = 0
        const val ACCURACY_LOW = 1
    }
}

/**
 * The map gets a new heading at most every [MIN_INTERVAL_MS] and only for a change of [MIN_CHANGE_DEG] or more (P11:
 * <= 10 Hz, no re-projection for sensor noise).
 */
class HeadingThrottle {
    private var lastDeg: Double? = null
    private var lastMs = Long.MIN_VALUE / 2

    /** The heading to show now, or null when nothing should change. */
    fun next(deg: Double?, nowMs: Long): Double? {
        if (deg == null || nowMs - lastMs < MIN_INTERVAL_MS) return null
        val prev = lastDeg
        if (prev != null && kotlin.math.abs(HeadingMath.diff(prev, deg)) < MIN_CHANGE_DEG) return null
        lastDeg = deg
        lastMs = nowMs
        return deg
    }

    fun reset() {
        lastDeg = null
        lastMs = Long.MIN_VALUE / 2
    }

    companion object {
        const val MIN_INTERVAL_MS = 100L
        const val MIN_CHANGE_DEG = 2.0
    }
}

package com.wgo.navigator.location

import com.wgo.navigator.core.LocationProfile

/** What one continuous location request asks for. */
data class RequestSpec(
    /** High accuracy (GPS) when true, balanced (network/Wi-Fi first) when false. */
    val highAccuracy: Boolean,
    val intervalMs: Long,
    val minDistanceM: Float
)

/**
 * Power profiles -> continuous request parameters. IDLE and ORIGIN never keep a request open (ORIGIN is a single
 * one-shot). NAVIGATING (screen on) adapts its interval: 1 s while a maneuver is closer than 80 m, 3 s above 20 km/h
 * on a long straight, 2 s otherwise; no minimum distance (see [NAV_MIN_DISTANCE_M]). AMBIENT (navigating with the
 * screen off or in ambient) keeps high accuracy but derives the interval from the distance to the next maneuver and
 * the speed ([adaptiveIntervalMs]): 1 s when the maneuver is <= 8 s away, else 2-10 s.
 */
object LocationProfiles {
    const val NAV_INTERVAL_MS = 2_000L
    const val NAV_NEAR_MANEUVER_INTERVAL_MS = 1_000L
    const val NAV_STRAIGHT_FAST_INTERVAL_MS = 3_000L
    const val NAV_NEAR_MANEUVER_M = 80.0
    /**
     * 0: a user standing still (red light, waiting to cross) must keep getting fixes, or the navigation engine's
     * 10 s GPS-lost rule would fire. The GPS runs anyway while navigating; the interval bounds the wakeups.
     */
    const val NAV_MIN_DISTANCE_M = 0f
    /**
     * Screen-off policy: `interval = clamp((distanceToNext - LEAD) / speed / 2, MIN, MAX)`, i.e. at least two fixes
     * before the user is [ADAPTIVE_LEAD_M] from the maneuver (so the 200 m cue is never late), rounded DOWN to one of
     * [ADAPTIVE_STEPS_MS] so the request is only replaced when the step changes, not on every fix.
     */
    const val ADAPTIVE_LEAD_M = 250.0
    const val ADAPTIVE_MIN_MS = 2_000L
    const val ADAPTIVE_MAX_MS = 10_000L
    val ADAPTIVE_STEPS_MS = longArrayOf(2_000L, 3_000L, 4_000L, 6_000L, 8_000L, 10_000L)

    /** Screen off: 1 s fixes only when the next maneuver is this close in time (at the current speed). */
    const val NEAR_MANEUVER_S = 8.0

    /** Below this speed the user counts as standing still: the longest interval. */
    const val STILL_MPS = 0.3f

    /** 20 km/h; the "fast" band is left below [SLOW_MPS] so it does not flap around the threshold. */
    const val FAST_MPS = 20f / 3.6f
    const val SLOW_MPS = 18f / 3.6f

    /** ORIGIN accepts a cached fix up to this age. */
    const val ORIGIN_MAX_AGE_MS = 60_000L

    fun requestSpec(
        profile: LocationProfile,
        distanceToManeuverM: Double? = null,
        onLongStraight: Boolean = false,
        fast: Boolean = false,
        speedMps: Float = Float.NaN
    ): RequestSpec? = when (profile) {
        LocationProfile.IDLE, LocationProfile.ORIGIN -> null
        LocationProfile.NAVIGATING -> RequestSpec(
            highAccuracy = true,
            intervalMs = when {
                distanceToManeuverM != null && distanceToManeuverM < NAV_NEAR_MANEUVER_M -> NAV_NEAR_MANEUVER_INTERVAL_MS
                fast && onLongStraight -> NAV_STRAIGHT_FAST_INTERVAL_MS
                else -> NAV_INTERVAL_MS
            },
            minDistanceM = NAV_MIN_DISTANCE_M
        )
        LocationProfile.AMBIENT -> RequestSpec(
            highAccuracy = true,
            intervalMs = adaptiveIntervalMs(distanceToManeuverM, speedMps),
            minDistanceM = NAV_MIN_DISTANCE_M
        )
    }

    /**
     * The screen-off interval (see [ADAPTIVE_LEAD_M]). 1 s only when the maneuver is at most [NEAR_MANEUVER_S] away in
     * TIME (distance / speed), so a walker is not polled every second for a minute before each turn; the normal 2 s
     * when the distance is unknown; with an unknown speed, the distance rule of the screen-on profile (1 s inside
     * 80 m, else 2 s); the longest step when standing still.
     */
    fun adaptiveIntervalMs(distanceToManeuverM: Double?, speedMps: Float): Long {
        if (distanceToManeuverM == null || distanceToManeuverM.isNaN()) return NAV_INTERVAL_MS
        if (speedMps.isNaN()) {
            return if (distanceToManeuverM < NAV_NEAR_MANEUVER_M) NAV_NEAR_MANEUVER_INTERVAL_MS else NAV_INTERVAL_MS
        }
        if (speedMps < STILL_MPS) return ADAPTIVE_MAX_MS
        if (distanceToManeuverM / speedMps <= NEAR_MANEUVER_S) return NAV_NEAR_MANEUVER_INTERVAL_MS
        val raw = (distanceToManeuverM - ADAPTIVE_LEAD_M) / speedMps / 2.0 * 1000.0
        val clamped = raw.coerceIn(ADAPTIVE_MIN_MS.toDouble(), ADAPTIVE_MAX_MS.toDouble())
        return ADAPTIVE_STEPS_MS.last { it <= clamped }
    }

    /** Speed band with hysteresis: becomes fast above 20 km/h, slow again below 18 km/h; unknown speed keeps it. */
    fun nextFast(wasFast: Boolean, speedMps: Float): Boolean = when {
        speedMps.isNaN() -> wasFast
        speedMps > FAST_MPS -> true
        speedMps < SLOW_MPS -> false
        else -> wasFast
    }
}

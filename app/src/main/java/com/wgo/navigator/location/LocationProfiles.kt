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
 * one-shot). NAVIGATING adapts its interval: 1 s while a maneuver is closer than 80 m, 3 s above 20 km/h on a long
 * straight, 2 s otherwise; always about 5 m minimum distance. AMBIENT is balanced accuracy every 10 s.
 */
object LocationProfiles {
    const val NAV_INTERVAL_MS = 2_000L
    const val NAV_NEAR_MANEUVER_INTERVAL_MS = 1_000L
    const val NAV_STRAIGHT_FAST_INTERVAL_MS = 3_000L
    const val NAV_NEAR_MANEUVER_M = 80.0
    const val NAV_MIN_DISTANCE_M = 5f
    const val AMBIENT_INTERVAL_MS = 10_000L
    const val AMBIENT_MIN_DISTANCE_M = 5f

    /** 20 km/h; the "fast" band is left below [SLOW_MPS] so it does not flap around the threshold. */
    const val FAST_MPS = 20f / 3.6f
    const val SLOW_MPS = 18f / 3.6f

    /** ORIGIN accepts a cached fix up to this age. */
    const val ORIGIN_MAX_AGE_MS = 60_000L

    fun requestSpec(
        profile: LocationProfile,
        distanceToManeuverM: Double? = null,
        onLongStraight: Boolean = false,
        fast: Boolean = false
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
        LocationProfile.AMBIENT -> RequestSpec(false, AMBIENT_INTERVAL_MS, AMBIENT_MIN_DISTANCE_M)
    }

    /** Speed band with hysteresis: becomes fast above 20 km/h, slow again below 18 km/h; unknown speed keeps it. */
    fun nextFast(wasFast: Boolean, speedMps: Float): Boolean = when {
        speedMps.isNaN() -> wasFast
        speedMps > FAST_MPS -> true
        speedMps < SLOW_MPS -> false
        else -> wasFast
    }
}

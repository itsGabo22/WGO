package com.wgo.navigator.location

import com.wgo.navigator.core.LocationProfile

/** What one continuous location request asks for. */
data class RequestSpec(
    /** High accuracy (GPS) when true, balanced (network/Wi-Fi first) when false. */
    val highAccuracy: Boolean,
    val intervalMs: Long,
    val minDistanceM: Float
)

/** Power profiles -> continuous request parameters. IDLE and ORIGIN never keep a request open. */
object LocationProfiles {
    const val NAV_INTERVAL_MS = 2_000L
    const val NAV_MIN_DISTANCE_M = 5f
    const val AMBIENT_INTERVAL_MS = 10_000L
    const val AMBIENT_MIN_DISTANCE_M = 5f

    /** ORIGIN accepts a cached fix up to this age. */
    const val ORIGIN_MAX_AGE_MS = 60_000L

    fun requestSpec(profile: LocationProfile): RequestSpec? = when (profile) {
        LocationProfile.IDLE, LocationProfile.ORIGIN -> null
        LocationProfile.NAVIGATING -> RequestSpec(true, NAV_INTERVAL_MS, NAV_MIN_DISTANCE_M)
        LocationProfile.AMBIENT -> RequestSpec(false, AMBIENT_INTERVAL_MS, AMBIENT_MIN_DISTANCE_M)
    }
}

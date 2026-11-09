package com.wgo.navigator.core

import kotlinx.coroutines.flow.Flow

/** One position fix. [timeMillis] is wall-clock time (epoch ms); [elapsedNanos] is the monotonic fix time. */
data class Fix(
    val lat: Double,
    val lon: Double,
    /** Horizontal accuracy radius in meters (68 %); [Float.NaN] when unknown. */
    val accuracyM: Float,
    /** Ground speed in m/s; [Float.NaN] when unknown. */
    val speedMps: Float,
    /** Bearing in degrees clockwise from north; [Float.NaN] when unknown. */
    val bearingDeg: Float,
    val timeMillis: Long,
    val elapsedNanos: Long
)

/** Power profiles: how hard the source works. The numbers live in the implementation (see LocationProfiles). */
enum class LocationProfile {
    /** No updates at all (screen 01 idle, settings, picker). */
    IDLE,

    /** No continuous updates: the origin is taken with a single [LocationSource.once] when a destination is chosen. */
    ORIGIN,

    /** High accuracy, adaptive 1-3 s interval, about 5 m minimum distance. */
    NAVIGATING,

    /** Ambient / low power while navigating: balanced accuracy, 10 s. */
    AMBIENT
}

/**
 * Device position. Implementations must not request updates while the profile is [LocationProfile.IDLE] or while
 * nobody collects [fixes], and must never throw: a missing permission or provider shows up as a [WgoFailure].
 */
interface LocationSource {
    /** Continuous fixes for the current profile (cold: requesting starts on collection and stops on cancel). */
    val fixes: Flow<Fix>

    /** The newest fix the platform already has, without turning anything on; null when there is none. */
    suspend fun lastKnown(): Fix?

    /** One fresh fix within [timeoutMs] (a cached fix younger than 60 s is accepted). */
    suspend fun once(timeoutMs: Long): Outcome<Fix>

    fun setProfile(profile: LocationProfile)

    /**
     * Navigation context for the adaptive NAVIGATING interval: distance to the next maneuver (null when unknown, e.g.
     * before routing exists) and whether the current segment is a long straight. Default: ignored.
     */
    fun setNavigationHint(distanceToManeuverM: Double?, onLongStraight: Boolean) {}
}

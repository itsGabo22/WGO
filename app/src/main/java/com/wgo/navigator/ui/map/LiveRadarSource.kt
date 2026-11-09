package com.wgo.navigator.ui.map

import android.os.SystemClock
import com.wgo.navigator.TravelMode
import com.wgo.navigator.core.Fix
import com.wgo.navigator.core.LocationSource
import com.wgo.navigator.location.FixFilter
import com.wgo.navigator.location.FixQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The Pasto centre, shown (as "searching") until the first usable fix. */
val PastoCentre = GeoCoord(1.2136, -77.2811)

/**
 * Radar data from the real [LocationSource]: the user's position, heading and accuracy. No simulated blips and, until
 * routing exists, no route. Good fixes move the user; inaccurate ones only flag [RadarState.searching]; stale ones
 * (older than 10 s) are ignored. The heading comes from GPS while moving at >= 1.5 m/s (bearing of the fix, or the
 * direction from the previous good fix) and is otherwise kept, so the map does not spin while standing still.
 *
 * [track] collects the source; call it from `repeatOnLifecycle(STARTED)` so updates stop with the app/screen.
 */
class LiveRadarSource(
    private val source: LocationSource,
    private val mode: TravelMode?,
    initial: GeoCoord?,
    private val nowNanos: () -> Long = { SystemClock.elapsedRealtimeNanos() }
) : RadarSource {
    private val _state = MutableStateFlow(
        RadarState(initial ?: PastoCentre, 0f, emptyList(), emptyList(), null, searching = true)
    )
    override val state: StateFlow<RadarState> = _state.asStateFlow()

    private var lastGood: Fix? = null

    suspend fun track() {
        source.fixes.collect { onFix(it) }
    }

    /** A one-shot fix (the ORIGIN) to start from; it goes through the same filters. */
    fun seed(fix: Fix) = onFix(fix)

    internal fun onFix(fix: Fix) {
        when (FixFilter.classify(fix, mode, nowNanos())) {
            FixQuality.Stale -> Unit
            FixQuality.Inaccurate -> _state.update { it.copy(searching = true, accuracyM = fix.accuracyM) }
            FixQuality.Good -> {
                val bearing = nextBearing(_state.value.bearing, lastGood, fix)
                lastGood = fix
                _state.value = RadarState(
                    location = GeoCoord(fix.lat, fix.lon),
                    bearing = bearing,
                    route = emptyList(),
                    blips = emptyList(),
                    maneuver = null,
                    accuracyM = fix.accuracyM,
                    searching = false
                )
            }
        }
    }

    companion object {
        const val MIN_HEADING_SPEED_MPS = 1.5f
        private const val MIN_MOVE_FOR_HEADING_M = 2.0

        /** Heading for [fix]: GPS-derived while moving fast enough, else [previous]. */
        fun nextBearing(previous: Float, last: Fix?, fix: Fix): Float {
            val moved = last?.let { RadarProjection.distanceMeters(GeoCoord(it.lat, it.lon), GeoCoord(fix.lat, fix.lon)) }
            val speed = when {
                !fix.speedMps.isNaN() -> fix.speedMps
                last != null && moved != null && fix.elapsedNanos > last.elapsedNanos ->
                    (moved / ((fix.elapsedNanos - last.elapsedNanos) / 1e9)).toFloat()
                else -> 0f
            }
            if (speed < MIN_HEADING_SPEED_MPS) return previous
            if (!fix.bearingDeg.isNaN()) return fix.bearingDeg
            if (last != null && moved != null && moved >= MIN_MOVE_FOR_HEADING_M) {
                return RadarProjection.bearingBetween(GeoCoord(last.lat, last.lon), GeoCoord(fix.lat, fix.lon)).toFloat()
            }
            return previous
        }
    }
}

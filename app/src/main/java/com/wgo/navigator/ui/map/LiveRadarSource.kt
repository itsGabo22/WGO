package com.wgo.navigator.ui.map

import android.os.SystemClock
import com.wgo.navigator.TravelMode
import com.wgo.navigator.core.Fix
import com.wgo.navigator.location.FixFilter
import com.wgo.navigator.location.FixQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The Pasto centre, shown (as "searching") until the first usable fix. */
val PastoCentre = GeoCoord(1.2136, -77.2811)

/**
 * Radar data from real fixes: the user's position, heading and accuracy, plus the trip's [route] polyline and the
 * next [maneuver] point. No simulated blips. Good fixes move the user; inaccurate ones only flag
 * [RadarState.searching]; stale ones (older than 10 s) are ignored. The heading comes from GPS while moving at
 * >= 1.5 m/s (bearing of the fix, or the direction from the previous good fix) and is otherwise kept, so the map does
 * not spin while standing still.
 *
 * The owner feeds it with [onFix] from the one location request of the trip (the radar never opens its own).
 */
class LiveRadarSource(
    private val mode: TravelMode?,
    initial: GeoCoord?,
    private var route: List<GeoCoord> = emptyList(),
    private var maneuver: GeoCoord? = null,
    private val nowNanos: () -> Long = { SystemClock.elapsedRealtimeNanos() }
) : RadarSource {
    private val _state = MutableStateFlow(
        RadarState(
            initial ?: route.firstOrNull() ?: PastoCentre,
            initialBearing(route), route, emptyList(), maneuver, searching = true
        )
    )
    override val state: StateFlow<RadarState> = _state.asStateFlow()

    private var lastGood: Fix? = null

    fun onFix(fix: Fix) {
        when (FixFilter.classify(fix, mode, nowNanos())) {
            FixQuality.Stale -> Unit
            FixQuality.Inaccurate -> _state.update { it.copy(searching = true, accuracyM = fix.accuracyM) }
            FixQuality.Good -> {
                val prev = _state.value
                val bearing = nextBearing(prev.bearing, lastGood, fix)
                lastGood = fix
                val location = GeoCoord(fix.lat, fix.lon)
                // Standing still (GPS jitter under 2 m, same heading): keep the state, so nothing is re-projected.
                if (!prev.searching && bearing == prev.bearing &&
                    RadarProjection.distanceMeters(prev.location, location) < MIN_REDRAW_MOVE_M
                ) return
                _state.value = RadarState(
                    location = location,
                    bearing = bearing,
                    route = route,
                    blips = emptyList(),
                    maneuver = maneuver,
                    accuracyM = fix.accuracyM,
                    searching = false
                )
            }
        }
    }

    /**
     * The route still ahead of the user (the navigation session cuts it as progress grows) and the next maneuver point.
     * The radar then draws only what is left; nothing changes while the user stands still.
     */
    fun setRoute(remaining: List<GeoCoord>, nextManeuver: GeoCoord?) {
        if (remaining === route && nextManeuver == maneuver) return
        route = remaining
        maneuver = nextManeuver
        _state.update { it.copy(route = remaining, maneuver = nextManeuver) }
    }

    companion object {
        const val MIN_HEADING_SPEED_MPS = 1.5f

        /** Smaller moves (with the same heading) are jitter: no new radar state, so no re-projection. */
        const val MIN_REDRAW_MOVE_M = 2.0
        private const val MIN_MOVE_FOR_HEADING_M = 2.0

        /** Before any movement the map faces along the route's first segment (north without a route). */
        fun initialBearing(route: List<GeoCoord>): Float =
            if (route.size >= 2) RadarProjection.bearingBetween(route[0], route[1]).toFloat() else 0f

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

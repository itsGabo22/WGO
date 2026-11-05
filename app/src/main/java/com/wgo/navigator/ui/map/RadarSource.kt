package com.wgo.navigator.ui.map

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class BlipKind { Incident, Poi }

@Immutable
data class RadarBlip(val position: GeoCoord, val kind: BlipKind)

/** Everything the radar needs to draw one frame of data. Updated at most once per second. */
@Immutable
data class RadarState(
    val location: GeoCoord,
    /** Heading in degrees clockwise from north; the map rotates so this points up. */
    val bearing: Float,
    /** Remaining route polyline, starting at the user's position. */
    val route: List<GeoCoord>,
    val blips: List<RadarBlip>,
    /** Where the next maneuver happens, if any. */
    val maneuver: GeoCoord?
)

/** The radar's data input. P5 plugs the real location + routing source in here. */
interface RadarSource {
    val state: StateFlow<RadarState>
}

/**
 * Simulated provider (there is no real location or routing yet): walks a point along a hard-coded polyline near
 * Pasto (1.2136, -77.2811) once per [tickMs], looping, so the radar animates. Replace with a real [RadarSource] in P5.
 */
class SimulatedRadarSource(
    scope: CoroutineScope,
    private val speedMps: Double = 14.0,
    private val tickMs: Long = 1000L
) : RadarSource {
    private val _state = MutableStateFlow(stateAt(0.0))
    override val state: StateFlow<RadarState> = _state.asStateFlow()

    init {
        scope.launch {
            var travelled = 0.0
            while (true) {
                delay(tickMs)
                travelled += speedMps * tickMs / 1000.0
                _state.value = stateAt(travelled)
            }
        }
    }

    companion object {
        /** Hard-coded demo route: four blocks with 90-degree turns near Plaza de Nariño, Pasto. */
        val DemoRoute = listOf(
            GeoCoord(1.2120, -77.2830),
            GeoCoord(1.2120, -77.2808),
            GeoCoord(1.2142, -77.2808),
            GeoCoord(1.2142, -77.2786),
            GeoCoord(1.2164, -77.2786),
            GeoCoord(1.2164, -77.2764)
        )

        private val DemoBlips = listOf(
            RadarBlip(GeoCoord(1.2131, -77.2806), BlipKind.Incident),
            RadarBlip(GeoCoord(1.2156, -77.2790), BlipKind.Poi)
        )

        fun stateAt(distanceM: Double): RadarState = stateAlong(DemoRoute, distanceM, DemoBlips)
    }
}

/** Total length of [polyline] in meters. */
fun polylineLength(polyline: List<GeoCoord>): Double =
    polyline.zipWithNext { a, b -> RadarProjection.distanceMeters(a, b) }.sum()

/**
 * Radar state for a point that has travelled [distanceM] along [polyline] (wrapping around at the end): position,
 * heading of the current segment, the remaining route and the next vertex as the maneuver.
 */
fun stateAlong(polyline: List<GeoCoord>, distanceM: Double, blips: List<RadarBlip>): RadarState {
    require(polyline.size >= 2)
    val total = polylineLength(polyline)
    var remaining = distanceM % total
    for (i in 0 until polyline.size - 1) {
        val a = polyline[i]
        val b = polyline[i + 1]
        val segment = RadarProjection.distanceMeters(a, b)
        if (remaining <= segment) {
            val bearing = RadarProjection.bearingBetween(a, b)
            val position = RadarProjection.destination(a, bearing, remaining)
            val ahead = polyline.subList(i + 1, polyline.size)
            return RadarState(
                location = position,
                bearing = bearing.toFloat(),
                route = listOf(position) + ahead,
                blips = blips,
                maneuver = if (i + 2 < polyline.size) b else null
            )
        }
        remaining -= segment
    }
    val last = polyline.last()
    return RadarState(last, 0f, listOf(last), blips, null)
}

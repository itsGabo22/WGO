package com.wgo.navigator.core

import com.wgo.navigator.TravelMode
import com.wgo.navigator.ui.map.GeoCoord

enum class Maneuver { DEPART, STRAIGHT, SLIGHT_LEFT, LEFT, SHARP_LEFT, SLIGHT_RIGHT, RIGHT, SHARP_RIGHT, U_TURN, ROUNDABOUT, ARRIVE }

/** One maneuver of a route: what to do at [location], and the distance until the next step. */
data class RouteStep(
    val maneuver: Maneuver,
    /** Street name after the maneuver; may be empty. Turn text is built from this in the UI language. */
    val street: String,
    val location: GeoCoord,
    val distanceM: Double,
    /** Roundabout exit number (1-based) for [Maneuver.ROUNDABOUT]; null otherwise or when unknown. */
    val exit: Int? = null
)

data class Route(
    val polyline: List<GeoCoord>,
    val distanceM: Double,
    val durationS: Double,
    val steps: List<RouteStep>,
    val mode: TravelMode
)

/** Origin + destination + mode to a route. Never throws; no route between the points is [WgoFailure.NotFound]. */
interface Router {
    suspend fun route(from: GeoCoord, to: GeoCoord, mode: TravelMode): Outcome<Route>

    /**
     * P11: up to 3 routes, fastest first, each with its own steps (switching needs no network). [bearingDeg] is the
     * user's course at [from] (a hint so the route does not start with a pointless U-turn); null when unknown.
     */
    suspend fun routes(from: GeoCoord, to: GeoCoord, mode: TravelMode, bearingDeg: Double? = null): Outcome<List<Route>> =
        route(from, to, mode).map { listOf(it) }
}

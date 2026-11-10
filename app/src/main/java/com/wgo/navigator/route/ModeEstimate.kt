package com.wgo.navigator.route

import com.wgo.navigator.TravelMode
import com.wgo.navigator.ui.map.GeoCoord
import com.wgo.navigator.ui.map.RadarProjection
import kotlin.math.roundToInt

/**
 * Minutes shown on the mode-selection pads, from the straight-line distance only (screen 02 makes no route request).
 * The constants were fitted to real OSRM routes from the Pasto centre (2026-11-10): Universidad de Nariño on foot
 * 2903 m / 38.8 min and by car 3573 m / 6.5 min, Terminal de Transportes by car 3083 m / 4.6 min and on foot
 * 2335 m / 31.3 min. Route length / straight line was 1.09-1.16 on foot and 1.42-1.44 by car (one-way streets and
 * the ring roads). Speeds are the routes' own average speeds: 1.25 m/s (4.5 km/h) walking, about 9.5 m/s
 * (34 km/h) driving in town.
 */
object ModeEstimate {
    const val WALK_DETOUR = 1.15
    const val WALK_SPEED_MPS = 1.25
    const val DRIVE_DETOUR = 1.43
    const val DRIVE_SPEED_MPS = 9.5

    fun seconds(straightLineM: Double, mode: TravelMode): Double = when (mode) {
        TravelMode.Walk -> straightLineM * WALK_DETOUR / WALK_SPEED_MPS
        TravelMode.Drive -> straightLineM * DRIVE_DETOUR / DRIVE_SPEED_MPS
    }

    /** Rounded minutes, at least 1. */
    fun minutes(from: GeoCoord, to: GeoCoord, mode: TravelMode): Int =
        (seconds(RadarProjection.distanceMeters(from, to), mode) / 60).roundToInt().coerceAtLeast(1)
}

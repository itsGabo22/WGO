package com.wgo.navigator.ui.map

import kotlin.math.cos

/**
 * The explore-mode pan clamp (spec section 7): the bounding box of the route (plus the user's position) grown by
 * [marginM] metres on every side. Keeping the map centre inside it bounds the tiles a pan can request. Pure.
 */
data class ExploreBounds(val south: Double, val west: Double, val north: Double, val east: Double) {
    fun clamp(p: GeoCoord): GeoCoord = GeoCoord(p.lat.coerceIn(south, north), p.lon.coerceIn(west, east))

    operator fun contains(p: GeoCoord): Boolean = p.lat in south..north && p.lon in west..east

    companion object {
        const val MARGIN_M = 400.0
        private const val METERS_PER_DEGREE_LAT = 111_320.0

        /** Null without any point. */
        fun around(points: List<GeoCoord>, marginM: Double = MARGIN_M): ExploreBounds? {
            if (points.isEmpty()) return null
            val south = points.minOf { it.lat }
            val north = points.maxOf { it.lat }
            val west = points.minOf { it.lon }
            val east = points.maxOf { it.lon }
            val dLat = marginM / METERS_PER_DEGREE_LAT
            // Longitude degrees shrink with latitude; use the box edge farthest from the equator (the larger margin).
            val lat = maxOf(kotlin.math.abs(south), kotlin.math.abs(north))
            val dLon = marginM / (METERS_PER_DEGREE_LAT * cos(Math.toRadians(lat)))
            return ExploreBounds(south - dLat, west - dLon, north + dLat, east + dLon)
        }
    }
}

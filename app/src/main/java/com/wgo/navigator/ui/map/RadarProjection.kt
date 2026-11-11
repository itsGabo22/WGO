package com.wgo.navigator.ui.map

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** A WGS84 coordinate. Kept free of Android/osmdroid types so the math below is plain-JVM testable. */
data class GeoCoord(val lat: Double, val lon: Double)

data class ScreenPoint(val x: Float, val y: Float)

/**
 * Pure projection math for the radar. It mirrors what osmdroid draws (Web Mercator tiles, map rotated by
 * `mapOrientation = -bearing` around the user's anchor point), so Compose overlays line up with the tiles
 * without touching the `MapView` per frame.
 */
object RadarProjection {
    const val DEFAULT_TILE_SIZE_PX = 256.0
    private const val EARTH_RADIUS_M = 6_371_000.0
    private const val EQUATOR_M_PER_PX_Z0 = 156_543.03392

    /** World pixel X of [lon] at [zoom] (tile size [tileSize]). */
    fun worldX(lon: Double, zoom: Double, tileSize: Double = DEFAULT_TILE_SIZE_PX): Double =
        (lon + 180.0) / 360.0 * tileSize * 2.0.pow(zoom)

    /** World pixel Y of [lat] at [zoom] (Web Mercator, y grows southward). */
    fun worldY(lat: Double, zoom: Double, tileSize: Double = DEFAULT_TILE_SIZE_PX): Double {
        val rad = Math.toRadians(lat)
        return (1.0 - ln(tan(rad) + 1.0 / cos(rad)) / PI) / 2.0 * tileSize * 2.0.pow(zoom)
    }

    /** Ground meters covered by one map pixel at [lat]. */
    fun metersPerPixel(lat: Double, zoom: Double, tileSize: Double = DEFAULT_TILE_SIZE_PX): Double =
        EQUATOR_M_PER_PX_Z0 * cos(Math.toRadians(lat)) / 2.0.pow(zoom) * (256.0 / tileSize)

    /**
     * Screen position of [point] when the map is centered on [center], rotated so that [bearingDeg] (clockwise from
     * north) points up, and [center] sits at ([anchorX], [anchorY]).
     */
    fun toScreen(
        point: GeoCoord,
        center: GeoCoord,
        bearingDeg: Double,
        zoom: Double,
        anchorX: Float,
        anchorY: Float,
        tileSize: Double = DEFAULT_TILE_SIZE_PX
    ): ScreenPoint {
        val dx = worldX(point.lon, zoom, tileSize) - worldX(center.lon, zoom, tileSize)
        val dy = worldY(point.lat, zoom, tileSize) - worldY(center.lat, zoom, tileSize)
        // mapOrientation = -bearing: canvas.rotate(theta) with positive = clockwise on a y-down screen.
        val theta = Math.toRadians(-bearingDeg)
        val rx = dx * cos(theta) - dy * sin(theta)
        val ry = dx * sin(theta) + dy * cos(theta)
        return ScreenPoint((anchorX + rx).toFloat(), (anchorY + ry).toFloat())
    }

    /** Latitude of world pixel Y [wy] at [zoom] (inverse of [worldY]). */
    fun latOfWorldY(wy: Double, zoom: Double, tileSize: Double = DEFAULT_TILE_SIZE_PX): Double {
        val n = PI * (1.0 - 2.0 * wy / (tileSize * 2.0.pow(zoom)))
        return Math.toDegrees(kotlin.math.atan(kotlin.math.sinh(n)))
    }

    /** Longitude of world pixel X [wx] at [zoom] (inverse of [worldX]). */
    fun lonOfWorldX(wx: Double, zoom: Double, tileSize: Double = DEFAULT_TILE_SIZE_PX): Double =
        wx / (tileSize * 2.0.pow(zoom)) * 360.0 - 180.0

    /**
     * The map centre after a finger drag of ([dxPx], [dyPx]) screen pixels on a map centred on [center] and rotated so
     * [bearingDeg] points up: the content follows the finger, so the centre moves the opposite way. Inverse of the
     * rotation in [toScreen].
     */
    fun panCenter(
        center: GeoCoord,
        dxPx: Double,
        dyPx: Double,
        bearingDeg: Double,
        zoom: Double,
        tileSize: Double = DEFAULT_TILE_SIZE_PX
    ): GeoCoord {
        val theta = Math.toRadians(-bearingDeg)
        val wx = dxPx * cos(theta) + dyPx * sin(theta)
        val wy = -dxPx * sin(theta) + dyPx * cos(theta)
        val cx = worldX(center.lon, zoom, tileSize) - wx
        val cy = worldY(center.lat, zoom, tileSize) - wy
        return GeoCoord(latOfWorldY(cy, zoom, tileSize), lonOfWorldX(cx, zoom, tileSize))
    }

    /** Initial bearing from [from] to [to], degrees clockwise from north in 0..360. */
    fun bearingBetween(from: GeoCoord, to: GeoCoord): Double {
        val lat1 = Math.toRadians(from.lat)
        val lat2 = Math.toRadians(to.lat)
        val dLon = Math.toRadians(to.lon - from.lon)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /** Great-circle distance in meters (haversine). */
    fun distanceMeters(a: GeoCoord, b: GeoCoord): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(sqrt(h))
    }

    /** Ground distance in meters of a ring of [radiusPx] screen pixels at [lat] and [zoom]. */
    fun ringMeters(radiusPx: Double, lat: Double, zoom: Double, tileSize: Double = DEFAULT_TILE_SIZE_PX): Double =
        radiusPx * metersPerPixel(lat, zoom, tileSize)

    /** Point [distanceM] meters from [from] along [bearingDeg] (small distances; spherical destination formula). */
    fun destination(from: GeoCoord, bearingDeg: Double, distanceM: Double): GeoCoord {
        val d = distanceM / EARTH_RADIUS_M
        val brg = Math.toRadians(bearingDeg)
        val lat1 = Math.toRadians(from.lat)
        val lon1 = Math.toRadians(from.lon)
        val lat2 = asin(sin(lat1) * cos(d) + cos(lat1) * sin(d) * cos(brg))
        val lon2 = lon1 + atan2(sin(brg) * sin(d) * cos(lat1), cos(d) - sin(lat1) * sin(lat2))
        return GeoCoord(Math.toDegrees(lat2), Math.toDegrees(lon2))
    }
}

package com.wgo.navigator.route

import com.wgo.navigator.ui.map.GeoCoord
import kotlin.math.cos
import kotlin.math.hypot

/** Encoded polyline (Google / OSRM `geometries=polyline`) and geometry simplification, in plain Kotlin. */
object Polyline {
    /** Decodes an encoded polyline with [precision] decimal digits (OSRM: 5). Malformed tails are ignored. */
    fun decode(encoded: String, precision: Int = 5): List<GeoCoord> {
        val factor = Math.pow(10.0, precision.toDouble())
        val out = ArrayList<GeoCoord>(encoded.length / 4)
        var index = 0
        var lat = 0L
        var lon = 0L
        while (index < encoded.length) {
            val dLat = next(encoded, index) ?: break
            index = dLat.second
            val dLon = next(encoded, index) ?: break
            index = dLon.second
            lat += dLat.first
            lon += dLon.first
            out += GeoCoord(lat / factor, lon / factor)
        }
        return out
    }

    /** One zig-zag varint: (value, next index), or null when the string ends mid-value. */
    private fun next(s: String, start: Int): Pair<Long, Int>? {
        var result = 0L
        var shift = 0
        var i = start
        while (true) {
            if (i >= s.length) return null
            val b = s[i++].code - 63
            result = result or ((b and 0x1f).toLong() shl shift)
            shift += 5
            if (b < 0x20) break
        }
        val value = if (result and 1L != 0L) (result shr 1).inv() else result shr 1
        return value to i
    }

    /**
     * Douglas-Peucker on a local metric projection: keeps both endpoints and every point farther than [toleranceM]
     * from the simplified line. If more than [maxPoints] remain, the tolerance doubles until they fit.
     */
    fun simplify(points: List<GeoCoord>, toleranceM: Double = 4.0, maxPoints: Int = 300): List<GeoCoord> {
        if (points.size <= 2) return points
        val lat0 = points[0].lat
        val kx = cos(Math.toRadians(lat0)) * M_PER_DEG
        val xs = DoubleArray(points.size) { (points[it].lon - points[0].lon) * kx }
        val ys = DoubleArray(points.size) { (points[it].lat - lat0) * M_PER_DEG }
        var tolerance = toleranceM
        while (true) {
            val keep = BooleanArray(points.size)
            keep[0] = true
            keep[points.size - 1] = true
            val stack = ArrayDeque<Pair<Int, Int>>()
            stack.addLast(0 to points.size - 1)
            while (stack.isNotEmpty()) {
                val (a, b) = stack.removeLast()
                var farthest = -1
                var maxDist = tolerance
                for (i in a + 1 until b) {
                    val d = segmentDistance(xs[i], ys[i], xs[a], ys[a], xs[b], ys[b])
                    if (d > maxDist) {
                        maxDist = d
                        farthest = i
                    }
                }
                if (farthest >= 0) {
                    keep[farthest] = true
                    stack.addLast(a to farthest)
                    stack.addLast(farthest to b)
                }
            }
            val result = points.filterIndexed { i, _ -> keep[i] }
            if (result.size <= maxPoints) return result
            tolerance *= 2
        }
    }

    /** Distance in metres from point p to segment a-b (all in the local projection). */
    fun segmentDistance(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
        return hypot(px - (ax + t * dx), py - (ay + t * dy))
    }

    private const val M_PER_DEG = 111_320.0
}

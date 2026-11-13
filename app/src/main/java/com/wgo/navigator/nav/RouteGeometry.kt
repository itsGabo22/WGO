package com.wgo.navigator.nav

import com.wgo.navigator.core.Maneuver
import com.wgo.navigator.core.Route
import com.wgo.navigator.core.RouteStep
import com.wgo.navigator.ui.map.GeoCoord
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * A [Route] prepared once for snapping (pure Kotlin): the polyline in a local metric projection (x east, y north, metres,
 * centred on the first point), cumulative distances, and every maneuver after the departure placed on the polyline as
 * a distance along it.
 *
 * OSRM gives each maneuver as a coordinate plus the length of the step that follows it. A maneuver is placed on the
 * nearest segment within [MANEUVER_WINDOW_M] of where the step lengths say it should be (scaled to the simplified
 * polyline's length), searching forward from the previous maneuver, so a loop route that passes the same corner twice
 * never swaps them.
 */
class RouteGeometry(val route: Route) {
    val size: Int = route.polyline.size
    private val lat0 = route.polyline[0].lat
    private val lon0 = route.polyline[0].lon
    private val kx = cos(Math.toRadians(lat0)) * M_PER_DEG

    val xs = DoubleArray(size) { (route.polyline[it].lon - lon0) * kx }
    val ys = DoubleArray(size) { (route.polyline[it].lat - lat0) * M_PER_DEG }

    /** Distance along the polyline at each vertex. */
    val cum = DoubleArray(size)

    /** Length of the polyline in metres. */
    val total: Double

    /** The maneuvers after the departure, in route order, and where each sits along the polyline. */
    val maneuvers: List<RouteStep>
    val maneuverAlong: DoubleArray

    init {
        require(size >= 2) { "a route needs at least two points" }
        for (i in 1 until size) cum[i] = cum[i - 1] + hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
        total = cum[size - 1]

        val steps = route.steps
        val kept = ArrayList<RouteStep>(steps.size)
        val along = ArrayList<Double>(steps.size)
        val scale = if (route.distanceM > 0) total / route.distanceM else 1.0
        var expected = 0.0
        var fromSeg = 0
        steps.forEachIndexed { i, step ->
            if (i > 0) expected += steps[i - 1].distanceM * scale
            if (step.maneuver == Maneuver.DEPART) return@forEachIndexed
            val a = if (step.maneuver == Maneuver.ARRIVE) total else {
                val px = x(step.location.lon)
                val py = y(step.location.lat)
                var best = Double.MAX_VALUE
                var bestAlong = expected.coerceIn(along.lastOrNull() ?: 0.0, total)
                var bestSeg = fromSeg
                for (s in fromSeg until size - 1) {
                    if (cum[s] > expected + MANEUVER_WINDOW_M) break
                    if (cum[s + 1] < expected - MANEUVER_WINDOW_M) continue
                    val t = projectT(s, px, py)
                    val d = distanceTo(s, t, px, py)
                    if (d < best) {
                        best = d
                        bestAlong = cum[s] + t * (cum[s + 1] - cum[s])
                        bestSeg = s
                    }
                }
                fromSeg = bestSeg
                bestAlong
            }
            kept += step
            along += maxOf(a, along.lastOrNull() ?: 0.0)
        }
        maneuvers = kept
        maneuverAlong = DoubleArray(along.size) { along[it] }
    }

    fun x(lon: Double): Double = (lon - lon0) * kx
    fun y(lat: Double): Double = (lat - lat0) * M_PER_DEG

    /** Position of [p] along segment [s] (0..1, clamped). */
    fun projectT(s: Int, px: Double, py: Double): Double {
        val dx = xs[s + 1] - xs[s]
        val dy = ys[s + 1] - ys[s]
        val len2 = dx * dx + dy * dy
        return if (len2 == 0.0) 0.0 else (((px - xs[s]) * dx + (py - ys[s]) * dy) / len2).coerceIn(0.0, 1.0)
    }

    fun distanceTo(s: Int, t: Double, px: Double, py: Double): Double =
        hypot(px - (xs[s] + t * (xs[s + 1] - xs[s])), py - (ys[s] + t * (ys[s + 1] - ys[s])))

    /** P11: the distance in metres from ([lat], [lon]) to the whole polyline (every segment). */
    fun distanceTo(lat: Double, lon: Double): Double {
        val px = x(lon)
        val py = y(lat)
        var best = Double.MAX_VALUE
        for (s in 0 until size - 1) {
            val d = distanceTo(s, projectT(s, px, py), px, py)
            if (d < best) best = d
        }
        return best
    }

    /** P11: how far along the polyline the point nearest to ([lat], [lon]) lies. */
    fun alongOf(lat: Double, lon: Double): Double {
        val px = x(lon)
        val py = y(lat)
        var best = Double.MAX_VALUE
        var along = 0.0
        for (s in 0 until size - 1) {
            val t = projectT(s, px, py)
            val d = distanceTo(s, t, px, py)
            if (d < best) {
                best = d
                along = cum[s] + t * (cum[s + 1] - cum[s])
            }
        }
        return along
    }

    /** The segment that contains [along] (binary search). */
    fun segmentAt(along: Double): Int {
        if (along <= 0.0) return 0
        if (along >= total) return size - 2
        var lo = 0
        var hi = size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1
            if (cum[mid] <= along) lo = mid else hi = mid
        }
        return lo
    }

    /** The point [along] metres from the start, as a coordinate. */
    fun pointAt(along: Double): GeoCoord {
        val s = segmentAt(along)
        val len = cum[s + 1] - cum[s]
        val t = if (len == 0.0) 0.0 else ((along - cum[s]) / len).coerceIn(0.0, 1.0)
        val px = xs[s] + t * (xs[s + 1] - xs[s])
        val py = ys[s] + t * (ys[s + 1] - ys[s])
        return GeoCoord(lat0 + py / M_PER_DEG, lon0 + px / kx)
    }

    /**
     * The route still ahead of [along]: the point at [along] followed by every later vertex (what the radar draws).
     * One list per call; the navigation screen asks for it at most once per second.
     */
    fun remainingFrom(along: Double): List<GeoCoord> {
        val s = segmentAt(along)
        val out = ArrayList<GeoCoord>(size - s)
        out += pointAt(along)
        for (i in s + 1 until size) out += route.polyline[i]
        return out
    }

    /** True when the segment at [along] is part of a straight run of at least [minM] metres (bearing change < 15 deg). */
    fun onLongStraight(along: Double, minM: Double): Boolean {
        val s = segmentAt(along)
        var end = s
        while (end < size - 2 && turnDeg(end) < STRAIGHT_TURN_DEG) end++
        return cum[end + 1] - along >= minM
    }

    private fun turnDeg(s: Int): Double {
        val a1 = Math.atan2(xs[s + 1] - xs[s], ys[s + 1] - ys[s])
        val a2 = Math.atan2(xs[s + 2] - xs[s + 1], ys[s + 2] - ys[s + 1])
        var d = Math.toDegrees(abs(a2 - a1))
        if (d > 180) d = 360 - d
        return d
    }

    companion object {
        const val M_PER_DEG = 111_320.0
        const val MANEUVER_WINDOW_M = 60.0
        private const val STRAIGHT_TURN_DEG = 15.0

        fun hypot(dx: Double, dy: Double): Double = sqrt(dx * dx + dy * dy)
    }
}

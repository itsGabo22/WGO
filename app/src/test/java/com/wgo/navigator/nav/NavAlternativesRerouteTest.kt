package com.wgo.navigator.nav

import com.wgo.navigator.TravelMode
import com.wgo.navigator.core.Fix
import com.wgo.navigator.core.Maneuver
import com.wgo.navigator.core.Route
import com.wgo.navigator.core.RouteStep
import com.wgo.navigator.ui.map.GeoCoord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

/**
 * P11 alternatives and live reroute (docs/ARCHITECTURE.md "Routing and reroute"), driven by synthetic GPS traces in a
 * local metric frame (x east, y north, metres from the Pasto centre).
 */
class NavAlternativesRerouteTest {
    private class FakeClock(var ms: Long = 1_000_000L) : NavClock {
        override fun elapsedMs() = ms
    }

    private val lat0 = 1.2136
    private val lon0 = -77.2811
    private val kx = cos(Math.toRadians(lat0)) * 111_320.0
    private fun geo(x: Double, y: Double) = GeoCoord(lat0 + y / 111_320.0, lon0 + x / kx)

    private fun route(speedMps: Double, vararg corners: Pair<Double, Double>, mode: TravelMode = TravelMode.Drive): Route {
        val pts = corners.map { geo(it.first, it.second) }
        val lens = corners.toList().zipWithNext { a, b -> Math.hypot(b.first - a.first, b.second - a.second) }
        val steps = ArrayList<RouteStep>()
        steps += RouteStep(Maneuver.DEPART, "", pts.first(), lens[0])
        for (i in 1 until pts.size - 1) steps += RouteStep(Maneuver.RIGHT, "Calle $i", pts[i], lens[i])
        steps += RouteStep(Maneuver.ARRIVE, "", pts.last(), 0.0)
        val dense = ArrayList<GeoCoord>()
        for (i in 0 until corners.size - 1) {
            val (ax, ay) = corners[i]
            val (bx, by) = corners[i + 1]
            val n = maxOf(1, (Math.hypot(bx - ax, by - ay) / 10).toInt())
            for (k in 0 until n) dense += geo(ax + (bx - ax) * k / n, ay + (by - ay) * k / n)
        }
        dense += pts.last()
        return Route(dense, lens.sum(), lens.sum() / speedMps, steps, mode)
    }

    /** Straight north 600 m. */
    private val main = route(10.0, 0.0 to 0.0, 0.0 to 600.0)

    /** East at y = 100, north along x = 200, back west at y = 600: same start and end, 400 m longer. */
    private val detour = route(10.0, 0.0 to 0.0, 0.0 to 100.0, 200.0 to 100.0, 200.0 to 600.0, 0.0 to 600.0)

    private val clock = FakeClock()
    private val events = ArrayList<NavEvent>()
    private val reroutes = ArrayList<GeoCoord>()

    private fun engine(r: Route = main, alternatives: List<Route> = listOf(detour)) =
        NavigationEngine(r, clock, { events += it }, { reroutes += it }, alternatives)

    private fun NavigationEngine.at(x: Double, y: Double, dtMs: Long = 1_000, speed: Float = 10f, bearing: Float = Float.NaN) {
        clock.ms += dtMs
        tick()
        val g = geo(x, y)
        onFix(Fix(g.lat, g.lon, 5f, speed, bearing, 0L, clock.ms * 1_000_000L))
    }

    private fun NavigationEngine.nav() = state as NavState.Navigating

    @Test fun followingTheMainRouteNeverSwitchesOrReroutes() {
        val e = engine()
        for (y in 0..590 step 10) e.at(0.0, y.toDouble())
        assertTrue(reroutes.isEmpty())
        assertSame(main, e.geometry.route)
        assertTrue(events.none { it == NavEvent.Rerouted || it == NavEvent.OffRouteDetected })
    }

    @Test fun drivingOntoAKnownAlternativeSwitchesWithNoRequest() {
        val e = engine()
        e.at(0.0, 0.0)
        e.at(0.0, 50.0)
        e.at(0.0, 100.0)
        e.at(50.0, 100.0) // 50 m from the main route, on the detour
        e.at(100.0, 100.0)
        e.at(150.0, 100.0) // third fix in a row on it
        assertSame(detour, e.geometry.route)
        assertTrue(reroutes.isEmpty())
        assertEquals(1, events.count { it == NavEvent.Rerouted })
        assertTrue(events.none { it == NavEvent.OffRouteDetected })
        assertSame(main, e.alternatives.single()) // the old route stays known
        // Its own steps: the next maneuver is the detour's corner at (200, 100).
        val next = e.nav().next!!
        assertEquals(geo(200.0, 100.0).lat, next.location.lat, 1e-9)
        assertEquals(0.0, e.nav().distanceFromRouteM, 1.0)
    }

    @Test fun oneNoisyFixNearTheAlternativeIsNotASwitch() {
        val e = engine()
        e.at(0.0, 60.0)
        e.at(0.0, 80.0)
        e.at(55.0, 100.0) // GPS jump onto the detour
        e.at(0.0, 110.0)
        e.at(0.0, 120.0)
        assertSame(main, e.geometry.route)
        assertTrue(reroutes.isEmpty())
        assertTrue(events.none { it == NavEvent.Rerouted })
    }

    @Test fun theUserCanPickAnotherKnownRouteWithNoRequest() {
        val e = engine()
        e.at(0.0, 0.0)
        assertTrue(e.selectRoute(0))
        assertSame(detour, e.geometry.route)
        assertTrue(reroutes.isEmpty())
        assertTrue(e.selectRoute(0)) // and back
        assertSame(main, e.geometry.route)
        assertEquals(false, e.selectRoute(5))
    }
}

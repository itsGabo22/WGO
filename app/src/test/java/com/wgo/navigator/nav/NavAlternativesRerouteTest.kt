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
    private val bearings = ArrayList<Double?>()

    private fun engine(r: Route = main, alternatives: List<Route> = listOf(detour)) =
        NavigationEngine(r, clock, { events += it }, { from, bearing -> reroutes += from; bearings += bearing }, alternatives)

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

    // ---- reroute from the position (P11) ----

    /** Leaves the main route eastwards along y = 200 at [speed] m/s heading east; returns the request times. */
    private fun NavigationEngine.driveEast(speed: Float = 10f, seconds: Int = 3): Unit {
        at(0.0, 190.0, speed = speed, bearing = 0f)
        at(0.0, 200.0, speed = speed, bearing = 0f)
        for (i in 1..seconds) at(50.0 + 10 * i, 200.0, speed = speed, bearing = 90f)
    }

    @Test fun aNewRoadReroutesOnceFromThePositionWithTheCourse() {
        val e = engine(alternatives = emptyList())
        e.driveEast()
        assertEquals(1, reroutes.size)
        assertEquals(geo(80.0, 200.0).lat, reroutes[0].lat, 1e-9)
        assertEquals(90.0, bearings[0]!!, 1e-9)
        assertTrue(e.state is NavState.Rerouting)
    }

    @Test fun slowMovementSendsNoCourseHint() {
        val e = engine(r = route(1.25, 0.0 to 0.0, 0.0 to 600.0, mode = TravelMode.Walk), alternatives = emptyList())
        e.at(0.0, 190.0, speed = 1.2f, bearing = 0f)
        e.at(0.0, 200.0, speed = 1.2f, bearing = 0f)
        for (i in 1..3) e.at(40.0 + 5 * i, 200.0, speed = 1.2f, bearing = 90f)
        assertEquals(1, reroutes.size)
        assertEquals(null, bearings[0])
    }

    @Test fun requestsAreAtLeastEightSecondsApartAndBackOffAfterFailures() {
        val e = engine(alternatives = emptyList())
        e.driveEast()
        val times = arrayListOf(clock.ms)
        var x = 80.0
        repeat(120) {
            if (e.state is NavState.Rerouting) e.onRerouteResult(null) // the router failed
            x += 10
            val before = reroutes.size
            e.at(x, 200.0, speed = 10f, bearing = 90f)
            if (reroutes.size > before) times += clock.ms
        }
        val gaps = times.zipWithNext { a, b -> b - a }
        assertEquals(listOf(16_000L, 32_000L), gaps) // 8 s doubled after each failure
        assertTrue(e.state is NavState.LostRoute) // 3 requests in a row without a route: ask the user
        assertEquals(3, reroutes.size)
    }

    @Test fun aSuccessfulRerouteFollowsTheNewRouteAndItsAlternatives() {
        val e = engine(alternatives = emptyList())
        e.driveEast()
        val fresh = route(10.0, 80.0 to 200.0, 80.0 to 600.0, 0.0 to 600.0)
        val other = route(10.0, 80.0 to 200.0, 300.0 to 200.0, 300.0 to 600.0, 0.0 to 600.0)
        assertTrue(e.onRerouteResult(fresh, listOf(other)))
        assertSame(fresh, e.geometry.route)
        assertSame(other, e.alternatives.single())
        assertTrue(e.state is NavState.Navigating)
        assertEquals(1, events.count { it == NavEvent.Rerouted })
    }

    @Test fun drivingBackOnTheSameRoadIsAWrongWayReroute() {
        val e = engine(alternatives = emptyList())
        for (y in listOf(260.0, 270.0, 280.0, 290.0, 300.0)) e.at(0.0, y, speed = 8f, bearing = 0f)
        assertTrue(reroutes.isEmpty())
        e.at(0.0, 292.0, speed = 8f, bearing = 180f) // turned around: still ON the route
        e.at(0.0, 284.0, speed = 8f, bearing = 180f)
        assertTrue(reroutes.isEmpty()) // two fixes are not enough
        e.at(0.0, 276.0, speed = 8f, bearing = 180f)
        assertEquals(1, events.count { it == NavEvent.OffRouteDetected })
        assertEquals(1, reroutes.size)
        assertEquals(180.0, bearings[0]!!, 1e-9)
    }

    @Test fun aCourseAlongTheRouteAgainClearsTheWrongWay() {
        val e = engine(alternatives = emptyList())
        e.at(0.0, 300.0, speed = 8f, bearing = 0f)
        e.at(0.0, 292.0, speed = 8f, bearing = 180f)
        e.at(0.0, 290.0, speed = 8f, bearing = 0f) // GPS course noise, then along the route
        e.at(0.0, 298.0, speed = 8f, bearing = 0f)
        e.at(0.0, 306.0, speed = 8f, bearing = 0f)
        assertTrue(reroutes.isEmpty())
        assertTrue(e.state is NavState.Navigating)
    }

    @Test fun offlineAnswersKeepTheRouteAndNeverLeadToLostRoute() {
        val e = engine(alternatives = emptyList())
        e.driveEast()
        val times = arrayListOf(clock.ms)
        var x = 80.0
        repeat(60) {
            if (e.state is NavState.Rerouting) e.onRerouteResult(null, offline = true)
            x += 10
            val before = reroutes.size
            e.at(x, 200.0, speed = 10f, bearing = 90f)
            if (reroutes.size > before) times += clock.ms
        }
        assertSame(main, e.geometry.route)
        val s = e.state as NavState.OffRoute
        assertTrue(s.offline)
        assertTrue(times.zipWithNext { a, b -> b - a }.all { it == 8_000L }) // no back-off, no LOST ROUTE
    }

    @Test fun remainingAndEtaOnlyShrinkWhileFollowingTheRoute() {
        val e = engine()
        var lastRemaining = Double.MAX_VALUE
        var lastEta = Double.MAX_VALUE
        for (y in 0..560 step 10) {
            e.at(0.0, y.toDouble(), speed = 10f, bearing = 0f)
            val n = e.nav()
            assertTrue("remaining at $y", n.remainingM <= lastRemaining)
            assertTrue("eta at $y", n.etaS <= lastEta + 1e-6)
            assertEquals(600.0 - y, n.remainingM, 1.0)
            lastRemaining = n.remainingM
            lastEta = n.etaS
        }
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

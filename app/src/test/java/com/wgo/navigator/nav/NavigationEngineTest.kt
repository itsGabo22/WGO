package com.wgo.navigator.nav

import com.wgo.navigator.TravelMode
import com.wgo.navigator.core.Fix
import com.wgo.navigator.core.Maneuver
import com.wgo.navigator.core.Route
import com.wgo.navigator.core.RouteStep
import com.wgo.navigator.ui.map.GeoCoord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/** Every engine rule driven by simulated fixes on routes built in a local metric frame (x east, y north). */
class NavigationEngineTest {
    // ---- harness ----

    private class FakeClock(var ms: Long = 1_000_000L) : NavClock {
        override fun elapsedMs() = ms
    }

    private val lat0 = 1.2136
    private val lon0 = -77.2811
    private val kx = cos(Math.toRadians(lat0)) * 111_320.0

    private fun geo(x: Double, y: Double) = GeoCoord(lat0 + y / 111_320.0, lon0 + x / kx)

    /** A route through [corners] (metres) with a DEPART, one step per inner corner and an ARRIVE. */
    private fun route(mode: TravelMode, speedMps: Double, vararg corners: Pair<Double, Double>, turn: Maneuver = Maneuver.RIGHT): Route {
        val pts = corners.map { geo(it.first, it.second) }
        val lens = corners.toList().zipWithNext { a, b -> Math.hypot(b.first - a.first, b.second - a.second) }
        val total = lens.sum()
        val steps = ArrayList<RouteStep>()
        steps += RouteStep(Maneuver.DEPART, "", pts.first(), lens[0])
        for (i in 1 until pts.size - 1) steps += RouteStep(turn, "Calle $i", pts[i], lens[i])
        steps += RouteStep(Maneuver.ARRIVE, "", pts.last(), 0.0)
        return Route(densify(corners.toList()), total, total / speedMps, steps, mode)
    }

    /** Polyline points every 10 m (like a real decoded route), so window logic is exercised. */
    private fun densify(c: List<Pair<Double, Double>>): List<GeoCoord> {
        val out = ArrayList<GeoCoord>()
        for (i in 0 until c.size - 1) {
            val (ax, ay) = c[i]
            val (bx, by) = c[i + 1]
            val n = maxOf(1, (Math.hypot(bx - ax, by - ay) / 10).toInt())
            for (k in 0 until n) out += geo(ax + (bx - ax) * k / n, ay + (by - ay) * k / n)
        }
        out += geo(c.last().first, c.last().second)
        return out
    }

    private val clock = FakeClock()
    private val events = ArrayList<NavEvent>()
    private val reroutes = ArrayList<GeoCoord>()

    private fun engine(r: Route) = NavigationEngine(r, clock, { events += it }, { reroutes += it })

    private fun fix(x: Double, y: Double, accuracy: Float = 5f, speed: Float = Float.NaN) =
        geo(x, y).let { Fix(it.lat, it.lon, accuracy, speed, Float.NaN, 0L, clock.ms * 1_000_000L) }

    /** Advances the clock by [dtMs], ticks, and feeds one fix. */
    private fun NavigationEngine.at(x: Double, y: Double, dtMs: Long = 1_000, accuracy: Float = 5f, speed: Float = Float.NaN) {
        clock.ms += dtMs
        tick()
        onFix(fix(x, y, accuracy, speed))
    }

    private fun NavigationEngine.nav() = state as NavState.Navigating

    private inline fun <reified T> count() = events.count { it is T }

    private val straight get() = route(TravelMode.Walk, 1.25, 0.0 to 0.0, 0.0 to 500.0)
    private val lShape get() = route(TravelMode.Drive, 10.0, 0.0 to 0.0, 0.0 to 400.0, 300.0 to 400.0)

    // ---- tests ----

    @Test fun waitsForAUsableFix() {
        val e = engine(straight)
        assertEquals(NavState.WaitingForGps, e.state)
        e.at(0.0, 10.0, accuracy = 70f) // too coarse for walking
        e.at(0.0, 10.0, accuracy = Float.NaN)
        assertEquals(NavState.WaitingForGps, e.state)
        clock.ms += 30_000
        e.tick()
        assertEquals("no GPS-lost before the first usable fix", NavState.WaitingForGps, e.state)
        e.at(0.0, 10.0, accuracy = 20f)
        assertEquals(10.0, e.nav().progressM, 0.5)
    }

    @Test fun staleFixesAreIgnored() {
        val e = engine(straight)
        e.onFix(fix(0.0, 50.0).copy(elapsedNanos = (clock.ms - 11_000) * 1_000_000L))
        assertEquals(NavState.WaitingForGps, e.state)
    }

    @Test fun straightRouteTracksProgressAndRemaining() {
        val e = engine(straight)
        for (y in 0..400 step 20) e.at(1.5, y.toDouble())
        val n = e.nav()
        assertEquals(400.0, n.progressM, 0.5)
        assertEquals(100.0, n.remainingM, 0.5)
        assertEquals(Maneuver.ARRIVE, n.next?.maneuver)
        assertEquals(100.0, n.distanceToNextM, 0.5)
        assertEquals(1.5, n.distanceFromRouteM, 0.1)
        assertEquals(400.0, n.snapped.let { (it.lat - lat0) * 111_320.0 }, 0.5)
    }

    @Test fun lShapedRouteAnnouncesTheTurnOnceAtEachSpecThreshold() {
        val e = engine(lShape)
        var y = 0.0
        while (y < 400) { e.at(0.0, y, speed = 10f); y += 10 }
        val approach = events.filterIsInstance<NavEvent.ApproachingTurn>()
        val at = events.filterIsInstance<NavEvent.AtTurn>()
        assertEquals(1, approach.size)
        assertEquals(1, at.size)
        assertTrue(approach[0].distanceM in 190.0..200.0)
        assertTrue(at[0].distanceM in 40.0..50.0)
        assertTrue(events.indexOf(approach[0]) < events.indexOf(at[0]))
        // After the corner the next maneuver is the arrival, 300 m ahead at the corner.
        e.at(20.0, 400.0)
        assertEquals(Maneuver.ARRIVE, e.nav().next?.maneuver)
        assertEquals(280.0, e.nav().distanceToNextM, 1.0)
        assertEquals(1, count<NavEvent.AtTurn>())
    }

    @Test fun firstFixInsideFiftyMetresGivesOnlyTheNowEvent() {
        val e = engine(lShape)
        e.at(0.0, 370.0)
        assertEquals(0, count<NavEvent.ApproachingTurn>())
        assertEquals(1, count<NavEvent.AtTurn>())
    }

    @Test fun straightOnStepsAreSilent() {
        val e = engine(route(TravelMode.Drive, 10.0, 0.0 to 0.0, 0.0 to 400.0, 0.0 to 800.0, turn = Maneuver.STRAIGHT))
        var y = 0.0
        while (y < 700) { e.at(0.0, y); y += 20 }
        assertEquals(0, count<NavEvent.ApproachingTurn>() + count<NavEvent.AtTurn>())
    }

    @Test fun loopRouteNeverJumpsToTheNearbyReturnLeg() {
        // East 300 m, north 20 m, back west 400 m: the return leg runs 20 m from the outbound one (the destination is
        // 100 m past the start, so the start is not mistaken for an arrival).
        val r = route(TravelMode.Walk, 1.25, 0.0 to 0.0, 300.0 to 0.0, 300.0 to 20.0, -100.0 to 20.0)
        val e = engine(r)
        // Outbound with fixes biased 8 m toward the return leg (closer to it than... still nearer the outbound).
        var x = 0.0
        while (x <= 290) { e.at(x, 8.0); assertTrue("outbound at $x", e.nav().progressM <= x + 1); x += 10 }
        assertEquals(290.0, e.nav().progressM, 1.0)
        // A fix exactly between the legs must not jump 300 m ahead to the return leg.
        e.at(150.0, 10.0)
        assertTrue(e.nav().progressM < 300.0)
        // The return leg is followed once the user really goes round the corner.
        e.at(300.0, 10.0)
        x = 290.0
        while (x >= 100) { e.at(x, 20.0); x -= 10 }
        assertEquals(300.0 + 20.0 + 200.0, e.nav().progressM, 2.0)
    }

    @Test fun gpsJitterNeverMovesProgressBackwardsOrOffRoute() {
        val e = engine(straight)
        e.at(0.0, 200.0)
        val rnd = java.util.Random(7)
        repeat(60) {
            e.at(rnd.nextGaussian() * 8, 200 + rnd.nextGaussian() * 8, accuracy = 15f, speed = 0.2f)
        }
        val n = e.nav()
        assertTrue(n.progressM >= 200.0 - 0.01)
        assertTrue("jitter only moves forward a little", n.progressM < 230)
        assertEquals(0, count<NavEvent.OffRouteDetected>())
    }

    @Test fun uTurnIsAcceptedOnlyAfterThreeFixesBehind() {
        val e = engine(straight)
        for (y in 0..200 step 20) e.at(0.0, y.toDouble())
        e.at(0.0, 185.0)
        e.at(0.0, 175.0)
        assertEquals(200.0, e.nav().progressM, 0.5)
        e.at(0.0, 165.0)
        assertEquals(165.0, e.nav().progressM, 0.5)
    }

    @Test fun offRouteAfterThreeFixesThenBackWithinHalfTheThreshold() {
        val e = engine(straight) // walking: 35 m
        for (y in 0..100 step 20) e.at(0.0, y.toDouble())
        e.at(40.0, 110.0)
        e.at(40.0, 115.0)
        assertTrue(e.state is NavState.Navigating)
        e.at(40.0, 120.0)
        assertEquals(1, count<NavEvent.OffRouteDetected>())
        assertTrue(e.state is NavState.Rerouting)
        assertEquals(1, reroutes.size)
        // Between half and the full threshold the user is still off route.
        e.onRerouteResult(null)
        e.at(25.0, 125.0)
        assertTrue(e.state is NavState.OffRoute)
        e.at(10.0, 130.0)
        assertTrue(e.state is NavState.Navigating)
        assertEquals(130.0, e.nav().progressM, 0.5)
        // A pending answer for a reroute nobody needs any more is ignored.
        assertFalse(e.onRerouteResult(null))
    }

    @Test fun rerouteIsRateLimitedAndStopsAfterThreeInARow() {
        val e = engine(straight)
        e.at(0.0, 50.0)
        repeat(3) { e.at(80.0, 60.0) }
        assertEquals(1, reroutes.size)
        e.onRerouteResult(null)
        assertTrue("a failed reroute keeps guiding on the old route", e.state is NavState.OffRoute)
        repeat(19) { e.at(80.0, 60.0) } // 19 s after the first request
        assertEquals(1, reroutes.size)
        e.at(80.0, 60.0) // 20 s
        assertEquals(2, reroutes.size)
        e.onRerouteResult(null)
        repeat(19) { e.at(80.0, 60.0) }
        assertEquals(2, reroutes.size)
        e.at(80.0, 60.0) // 20 s after the second request
        assertEquals(3, reroutes.size)
        e.onRerouteResult(null)
        assertTrue(e.state is NavState.LostRoute)
        clock.ms += 60_000
        e.tick()
        e.at(80.0, 60.0)
        assertEquals("no fourth request without the user", 3, reroutes.size)
        assertTrue(e.state is NavState.LostRoute)
        e.retryReroute()
        assertEquals(4, reroutes.size)
        assertTrue(e.state is NavState.Rerouting)
    }

    @Test fun successfulRerouteReplacesTheRoute() {
        val e = engine(straight)
        e.at(0.0, 50.0)
        repeat(3) { e.at(80.0, 60.0) }
        val detour = route(TravelMode.Walk, 1.25, 80.0 to 60.0, 80.0 to 300.0, 0.0 to 500.0)
        assertTrue(e.onRerouteResult(detour))
        assertEquals(1, count<NavEvent.Rerouted>())
        assertTrue(e.state is NavState.Navigating)
        assertEquals(0.0, e.nav().progressM, 0.5)
        assertEquals(detour.distanceM, e.nav().remainingM, 1.0)
        e.at(80.0, 100.0)
        assertEquals(40.0, e.nav().progressM, 0.5)
    }

    @Test fun firstFixFarFromTheRouteGoesOffRouteNotToANearbyPoint() {
        val e = engine(straight)
        e.at(200.0, 0.0)
        assertTrue(e.state is NavState.Navigating)
        assertEquals(200.0, e.nav().distanceFromRouteM, 0.5)
        assertEquals("progress is not taken from a far fix", 0.0, e.nav().progressM, 0.01)
        e.at(200.0, 0.0)
        e.at(200.0, 0.0)
        assertEquals(1, count<NavEvent.OffRouteDetected>())
        assertEquals(1, reroutes.size)
        e.onRerouteResult(null)
        e.at(5.0, 40.0)
        assertTrue(e.state is NavState.Navigating)
        assertEquals(40.0, e.nav().progressM, 0.5)
    }

    @Test fun arrivalWalkingAndDriving() {
        val walk = engine(straight)
        walk.at(0.0, 470.0)
        walk.at(0.0, 484.0)
        assertTrue(walk.state is NavState.Navigating)
        walk.at(0.0, 486.0) // 14 m
        assertEquals(NavState.Arrived, walk.state)
        walk.at(0.0, 100.0)
        assertEquals("arrival is terminal", NavState.Arrived, walk.state)
        assertEquals(1, count<NavEvent.Arrived>())

        events.clear()
        val drive = engine(route(TravelMode.Drive, 10.0, 0.0 to 0.0, 0.0 to 500.0))
        drive.at(0.0, 470.0)
        assertTrue(drive.state is NavState.Navigating)
        drive.at(0.0, 476.0) // 24 m
        assertEquals(NavState.Arrived, drive.state)
    }

    @Test fun arrivalPastTheFinalSegment() {
        val e = engine(straight)
        e.at(0.0, 460.0)
        e.at(20.0, 525.0) // beyond the end, 32 m from it: not within 15 m, but snapped to the very end
        assertEquals(NavState.Arrived, e.state)
    }

    @Test fun gpsLostAfterTenSecondsAndRecoveredByTheNextFix() {
        val e = engine(straight)
        e.at(0.0, 100.0)
        repeat(9) { clock.ms += 1_000; e.tick() }
        assertTrue(e.state is NavState.Navigating)
        clock.ms += 1_000
        e.tick()
        assertTrue(e.state is NavState.GpsLost)
        assertEquals(100.0, (e.state as NavState.GpsLost).last!!.progressM, 0.5)
        assertEquals(1, count<NavEvent.GpsLost>())
        e.at(0.0, 110.0, accuracy = 90f) // still nothing usable
        assertTrue(e.state is NavState.GpsLost)
        e.at(0.0, 120.0)
        assertTrue(e.state is NavState.Navigating)
        assertEquals(1, count<NavEvent.GpsRecovered>())
    }

    @Test fun resumingAfterAPauseDoesNotReportALoss() {
        val e = engine(straight)
        e.at(0.0, 100.0)
        clock.ms += 60_000 // app in the background, no fixes on purpose
        e.onResumed()
        e.tick()
        assertTrue(e.state is NavState.Navigating)
        assertEquals(0, count<NavEvent.GpsLost>())
    }

    @Test fun etaFollowsSmoothedSpeedClampedAndHoldsWhenStationary() {
        val e = engine(route(TravelMode.Drive, 10.0, 0.0 to 0.0, 0.0 to 2000.0)) // 200 s expected
        e.at(0.0, 0.0, speed = 10f)
        assertEquals(200.0, e.nav().etaS, 1.0)
        // Twice the expected speed: the ratio approaches 2 smoothly, never instantly.
        e.at(0.0, 20.0, speed = 20f)
        val afterOne = e.nav().etaS
        val remainingRouteTime = e.nav().remainingM / 10.0
        assertTrue(afterOne < remainingRouteTime && afterOne > remainingRouteTime / 2)
        var y = 20.0
        repeat(20) { y += 20; e.at(0.0, y, speed = 20f) }
        assertEquals(e.nav().remainingM / 20.0, e.nav().etaS, 1.0)
        // Far above 2x is clamped.
        repeat(10) { y += 20; e.at(0.0, y, speed = 80f) }
        assertEquals(e.nav().remainingM / 20.0, e.nav().etaS, 1.0)
        // Stopped at a light: the estimate keeps its ratio instead of jumping to infinity or to the raw route time.
        val before = e.nav().etaS
        repeat(10) { e.at(0.0, y, speed = 0f) }
        assertEquals(before, e.nav().etaS, 0.01)
    }

    @Test fun nextAndThenManeuvers() {
        val e = engine(route(TravelMode.Drive, 10.0, 0.0 to 0.0, 0.0 to 300.0, 200.0 to 300.0, 200.0 to 600.0))
        e.at(0.0, 0.0)
        val n = e.nav()
        assertEquals("Calle 1", n.next?.street)
        assertEquals("Calle 2", n.then?.street)
        assertEquals(300.0, n.distanceToNextM, 1.0)
        for (y in 50..250 step 50) e.at(0.0, y.toDouble())
        e.at(10.0, 300.0)
        assertEquals("Calle 2", e.nav().next?.street)
        assertEquals(Maneuver.ARRIVE, e.nav().then?.maneuver)
    }

    @Test fun maneuversOnALoopAreNotSwapped() {
        // Square loop back to the start corner: the last turn is at the same place as the departure.
        val r = route(TravelMode.Walk, 1.25, 0.0 to 0.0, 200.0 to 0.0, 200.0 to 200.0, 0.0 to 200.0, 0.0 to 5.0)
        val g = RouteGeometry(r)
        val along = g.maneuverAlong.toList()
        assertEquals(listOf(200.0, 400.0, 600.0, 795.0), along.map { Math.round(it).toDouble() })
        assertNull(g.maneuvers.firstOrNull { it.maneuver == Maneuver.DEPART })
    }

    @Test fun costPerFixOnAThreeHundredPointRoute() {
        // A 300-vertex zig-zag of about 6 km (the router's simplified maximum).
        val corners = (0 until 300).map { i -> (i * 20.0) to (if (i % 2 == 0) 0.0 else 3.0) + 10 * sin(i / 10.0) }
        val r = route(TravelMode.Drive, 10.0, *corners.toTypedArray())
        val e = NavigationEngine(Route(corners.map { geo(it.first, it.second) }, r.distanceM, r.durationS, r.steps, r.mode), clock)
        val fixes = (0 until 2_000).map { k -> val x = k * 2.9; fix(x, 10 * sin(x / 200.0) + 1.5) }
        // Warm up the JIT, then measure.
        repeat(3) { val w = NavigationEngine(e.geometry.route, clock); fixes.forEach { w.onFix(it.copy(elapsedNanos = clock.ms * 1_000_000L)) } }
        val m = NavigationEngine(e.geometry.route, clock)
        val start = System.nanoTime()
        fixes.forEach { m.onFix(it.copy(elapsedNanos = clock.ms * 1_000_000L)) }
        val perFixUs = (System.nanoTime() - start) / 1_000.0 / fixes.size
        println("NavigationEngine cost per fix: %.2f us (300-point route, %d fixes)".format(perFixUs, fixes.size))
        assertTrue(m.state is NavState.Navigating || m.state == NavState.Arrived)
        assertTrue("per-fix cost $perFixUs us", perFixUs < 200)
    }
}

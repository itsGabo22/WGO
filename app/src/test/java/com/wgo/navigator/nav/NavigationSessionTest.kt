package com.wgo.navigator.nav

import com.wgo.navigator.TravelMode
import com.wgo.navigator.core.Fix
import com.wgo.navigator.core.Maneuver
import com.wgo.navigator.core.Outcome
import com.wgo.navigator.core.Route
import com.wgo.navigator.core.RouteStep
import com.wgo.navigator.core.Router
import com.wgo.navigator.core.WgoFailure
import com.wgo.navigator.ui.map.GeoCoord
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

/** The state holder: reroutes through the Router (one request each), trims the radar route, pauses without GPS-lost. */
class NavigationSessionTest {
    private var now = 1_000_000L
    private val clock = NavClock { now }
    private val lat0 = 1.2136
    private val lon0 = -77.2811
    private val kx = cos(Math.toRadians(lat0)) * 111_320.0
    private fun geo(x: Double, y: Double) = GeoCoord(lat0 + y / 111_320.0, lon0 + x / kx)
    private fun fix(x: Double, y: Double) = geo(x, y).let { Fix(it.lat, it.lon, 5f, Float.NaN, Float.NaN, 0L, now * 1_000_000L) }

    private fun north(fromX: Double, toY: Double) = Route(
        (0..(toY / 10).toInt()).map { geo(fromX, it * 10.0) }, toY, toY / 1.25,
        listOf(RouteStep(Maneuver.DEPART, "", geo(fromX, 0.0), toY), RouteStep(Maneuver.ARRIVE, "", geo(fromX, toY), 0.0)),
        TravelMode.Walk
    )

    private class CountingRouter(var answer: Outcome<Route>) : Router {
        val calls = ArrayList<Pair<GeoCoord, GeoCoord>>()
        override suspend fun route(from: GeoCoord, to: GeoCoord, mode: TravelMode): Outcome<Route> {
            calls += from to to
            assertEquals(TravelMode.Walk, mode)
            return answer
        }
    }

    @Test fun reroutesThroughTheRouterAndTrimsTheRemainingRoute() = runBlocking {
        val dest = geo(0.0, 500.0)
        val detour = north(80.0, 500.0)
        val router = CountingRouter(Outcome.Ok(detour))
        val s = NavigationSession(north(0.0, 500.0), dest, router, this, clock, tickMs = 60_000)
        s.onFix(fix(0.0, 100.0))
        assertTrue(s.state.value is NavState.Navigating)
        assertEquals(41, s.remaining.value.size) // the point at 100 m plus 40 vertices ahead
        s.onFix(fix(0.0, 102.0))
        assertEquals("under 5 m of progress: the radar route is not cut again", 41, s.remaining.value.size)
        repeat(3) { now += 1_000; s.onFix(fix(80.0, 120.0)) }
        assertTrue(s.state.value is NavState.Rerouting)
        yield() // the request runs on the session scope
        assertEquals(1, router.calls.size)
        assertEquals(dest, router.calls[0].second)
        assertEquals(detour, s.route.value)
        assertTrue(s.state.value is NavState.Navigating)
        assertEquals(1, s.rerouteRequests)
        s.stop()
    }

    @Test fun failedRerouteKeepsTheOldRoute() = runBlocking {
        val router = CountingRouter(Outcome.Err(WgoFailure.Offline))
        val original = north(0.0, 500.0)
        val s = NavigationSession(original, geo(0.0, 500.0), router, this, clock, tickMs = 60_000)
        s.onFix(fix(0.0, 100.0))
        repeat(3) { now += 1_000; s.onFix(fix(80.0, 120.0)) }
        yield()
        assertEquals(1, router.calls.size)
        assertEquals(original, s.route.value)
        assertTrue(s.state.value is NavState.OffRoute)
        s.stop()
    }

    @Test fun pausedSessionsReportNoGpsLoss() = runBlocking {
        val s = NavigationSession(north(0.0, 500.0), geo(0.0, 500.0), CountingRouter(Outcome.Err(WgoFailure.Offline)), this, clock, tickMs = 5)
        s.onFix(fix(0.0, 100.0))
        s.start()
        s.pause()
        now += 60_000
        s.resume()
        kotlinx.coroutines.delay(30)
        assertTrue(s.state.value is NavState.Navigating)
        now += 11_000
        kotlinx.coroutines.delay(30)
        assertTrue("still ticking after resume", s.state.value is NavState.GpsLost)
        s.stop()
    }
}

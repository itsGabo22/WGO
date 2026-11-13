package com.wgo.navigator.nav

import com.wgo.navigator.Language
import com.wgo.navigator.TravelMode
import com.wgo.navigator.core.Fix
import com.wgo.navigator.core.Maneuver
import com.wgo.navigator.core.Outcome
import com.wgo.navigator.core.Route
import com.wgo.navigator.core.RouteStep
import com.wgo.navigator.core.Router
import com.wgo.navigator.ui.map.AltLabelRules
import com.wgo.navigator.ui.map.GeoCoord
import com.wgo.navigator.ui.map.LabelBlock
import com.wgo.navigator.ui.map.RadarProjection
import com.wgo.navigator.ui.screens.HudMode
import com.wgo.navigator.ui.screens.HudRules
import com.wgo.navigator.ui.screens.altDeltaLabel
import com.wgo.navigator.ui.theme.WGO_FIT_LIMIT_GW5P
import com.wgo.navigator.ui.theme.wgoFit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

/** P11 route choice: the RUTA chip, the drawn alternatives and their labels, the HUD window after a switch. */
class RouteChoiceTest {
    private var now = 1_000_000L
    private val clock = NavClock { now }
    private val lat0 = 1.2136
    private val lon0 = -77.2811
    private val kx = cos(Math.toRadians(lat0)) * 111_320.0
    private fun geo(x: Double, y: Double) = GeoCoord(lat0 + y / 111_320.0, lon0 + x / kx)
    private fun fix(x: Double, y: Double) = geo(x, y).let { Fix(it.lat, it.lon, 5f, 10f, 0f, 0L, now * 1_000_000L) }

    private fun route(durationS: Double, vararg c: Pair<Double, Double>): Route {
        val pts = ArrayList<GeoCoord>()
        for (i in 0 until c.size - 1) {
            val n = maxOf(1, (Math.hypot(c[i + 1].first - c[i].first, c[i + 1].second - c[i].second) / 10).toInt())
            for (k in 0 until n) pts += geo(c[i].first + (c[i + 1].first - c[i].first) * k / n, c[i].second + (c[i + 1].second - c[i].second) * k / n)
        }
        pts += geo(c.last().first, c.last().second)
        val len = c.toList().zipWithNext { a, b -> Math.hypot(b.first - a.first, b.second - a.second) }.sum()
        return Route(pts, len, durationS, listOf(RouteStep(Maneuver.DEPART, "", pts.first(), len), RouteStep(Maneuver.ARRIVE, "", pts.last(), 0.0)), TravelMode.Drive)
    }

    private val main = route(60.0, 0.0 to 0.0, 0.0 to 600.0)
    private val east = route(120.0, 0.0 to 0.0, 0.0 to 100.0, 200.0 to 100.0, 200.0 to 600.0, 0.0 to 600.0)
    private val west = route(180.0, 0.0 to 0.0, 0.0 to 100.0, -250.0 to 100.0, -250.0 to 600.0, 0.0 to 600.0)

    private object NoRouter : Router {
        override suspend fun route(from: GeoCoord, to: GeoCoord, mode: TravelMode): Outcome<Route> = error("no request expected")
    }

    @Test fun theChipCyclesTheKnownRoutesInAStableOrderWithNoRequest() = runBlocking {
        val s = NavigationSession(main, geo(0.0, 600.0), NoRouter, this, clock, tickMs = 60_000, initialAlternatives = listOf(east, west))
        s.onFix(fix(0.0, 10.0))
        assertEquals(NavigationSession.RouteChoice(0, 3), s.choice.value)
        s.selectNext()
        assertSame(east, s.route.value)
        assertEquals(1, s.choice.value.index)
        s.selectNext()
        assertSame(west, s.route.value)
        s.selectNext()
        assertSame(main, s.route.value) // back to the first, in the same order
        assertEquals(0, s.rerouteRequests)
        s.stop()
    }

    @Test fun alternativesAreDrawnFromTheUserWithTheirTimeDifference() = runBlocking {
        val s = NavigationSession(main, geo(0.0, 600.0), NoRouter, this, clock, tickMs = 60_000, initialAlternatives = listOf(east))
        s.onFix(fix(0.0, 50.0))
        val line = s.alternativeLines.value.single()
        // Starts at the point of the detour nearest to the user, not at the trip's origin.
        assertTrue(RadarProjection.distanceMeters(line.points.first(), geo(0.0, 50.0)) < 1.0)
        // Remaining: detour 120 s * (1000 - 50) / 1000 = 114 s; active 60 s * 550 / 600 = 55 s -> +59 s.
        assertEquals(59.0, line.deltaS, 1.0)
        // Its label is where it is farthest from the active route (the far side at x = 200).
        assertTrue(RadarProjection.distanceMeters(line.label, geo(200.0, 350.0)) <= 260.0)
        // The active route is drawn only from the user on.
        assertTrue(RadarProjection.distanceMeters(s.remaining.value.first(), geo(0.0, 50.0)) < 1.0)
        s.stop()
    }

    @Test fun aSwitchOpensTheExpandedHudWindowWithoutAReroute() {
        val far = HudRules.Input(900.0, "m1", rerouting = false, exploring = false, nowMs = 0L, routeKey = 1)
        var state = HudRules.initial(far)
        assertEquals(HudMode.Compact, state.shown)
        state = HudRules.step(state, far.copy(maneuver = "m2", nowMs = 1_000L, routeKey = 2))
        assertEquals(HudMode.Expanded, state.shown)
        state = HudRules.step(state, far.copy(maneuver = "m2", nowMs = 1_000L + HudRules.REROUTE_EXPAND_MS, routeKey = 2))
        assertEquals(HudMode.Compact, state.shown)
    }

    @Test fun timeLabelsStayInsideTheDialAndOffTheControls() {
        val hud = LabelBlock(57.5f, 22f, 169.5f, 58f)
        val zoomMinus = LabelBlock(21.5f, 130f, 61.5f, 170f)
        // A far point is pulled inside the 78 dp circle.
        val far = AltLabelRules.place(113.5f, -200f, emptyList())!!
        assertEquals(78f, Math.hypot((far.x - 113.5f).toDouble(), (far.y - 113.5f).toDouble()).toFloat(), 0.01f)
        // Under the HUD, a zoom button or the user: hidden.
        assertNull(AltLabelRules.place(113.5f, 40f, listOf(hud)))
        assertNull(AltLabelRules.place(45f, 150f, listOf(zoomMinus)))
        assertNull(AltLabelRules.place(113.5f, 150f, emptyList()))
        assertNotNull(AltLabelRules.place(160f, 90f, listOf(hud, zoomMinus)))
        assertEquals("+2 MIN", altDeltaLabel(2, Language.ES))
        assertEquals("−1 MIN", altDeltaLabel(-1, Language.EN))
        assertEquals("=", altDeltaLabel(0, Language.ES))
    }

    @Test fun theRouteChipFitsAndKeepsItsTouchBoxClearOfTheZoomButtons() {
        // 80 x 32 centred at (113.5, 108): top 92.
        assertTrue(wgoFit(80f, 32f, 92f) <= WGO_FIT_LIMIT_GW5P)
        for (s in listOf(0.846f, 1f)) {
            val touchW = maxOf(80f, 48f / s) / 2
            val zoomTouch = 48f / s / 2
            assertTrue("s=$s", 113.5f - touchW >= 41.5f + zoomTouch || 108f + 48f / s / 2 <= 150f - zoomTouch)
        }
    }

    @Test fun drivingOntoAnAlternativeSaysSoButAManualPickDoesNot() {
        val events = ArrayList<NavEvent>()
        val e = NavigationEngine(main, clock, { events += it }, { _, _ -> }, listOf(east))
        for (p in listOf(0.0 to 100.0, 50.0 to 100.0, 100.0 to 100.0, 150.0 to 100.0)) { now += 1_000; e.onFix(fix(p.first, p.second)) }
        assertEquals(listOf(NavEvent.AlternativeTaken, NavEvent.Rerouted), events.filter { it == NavEvent.AlternativeTaken || it == NavEvent.Rerouted })
        events.clear()
        e.selectRoute(0)
        assertTrue(events.none { it == NavEvent.AlternativeTaken })
    }
}

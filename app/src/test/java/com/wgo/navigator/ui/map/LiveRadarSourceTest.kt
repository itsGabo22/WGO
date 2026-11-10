package com.wgo.navigator.ui.map

import com.wgo.navigator.TravelMode
import com.wgo.navigator.core.fix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveRadarSourceTest {
    private val now = 50_000_000_000L
    private fun radar(mode: TravelMode? = TravelMode.Walk) = LiveRadarSource(mode, null) { now }

    @Test fun startsSearchingAtPastoWithNoBlipsOrRoute() {
        val s = radar().state.value
        assertTrue(s.searching)
        assertEquals(PastoCentre, s.location)
        assertTrue(s.blips.isEmpty() && s.route.isEmpty())
    }

    @Test fun goodFixMovesTheUser() {
        val r = radar()
        r.onFix(fix(lat = 1.2200, lon = -77.2900, accuracyM = 10f, elapsedNanos = now))
        val s = r.state.value
        assertFalse(s.searching)
        assertEquals(1.2200, s.location.lat, 1e-9)
        assertEquals(10f, s.accuracyM)
        assertTrue(s.blips.isEmpty())
    }

    @Test fun inaccurateFixOnlyFlagsSearching() {
        val r = radar()
        r.onFix(fix(lat = 1.2200, accuracyM = 10f, elapsedNanos = now))
        r.onFix(fix(lat = 1.3000, accuracyM = 120f, elapsedNanos = now))
        val s = r.state.value
        assertTrue(s.searching)
        assertEquals("the user did not jump", 1.2200, s.location.lat, 1e-9)
    }

    @Test fun staleFixIsIgnored() {
        val r = radar()
        r.onFix(fix(lat = 1.2200, elapsedNanos = now))
        r.onFix(fix(lat = 1.3000, elapsedNanos = now - 11_000_000_000L))
        assertEquals(1.2200, r.state.value.location.lat, 1e-9)
        assertFalse(r.state.value.searching)
    }

    @Test fun headingFollowsGpsOnlyWhileMoving() {
        val r = radar(TravelMode.Drive)
        r.onFix(fix(bearingDeg = 90f, speedMps = 5f, elapsedNanos = now))
        assertEquals(90f, r.state.value.bearing)
        r.onFix(fix(bearingDeg = 200f, speedMps = 1.0f, elapsedNanos = now))
        assertEquals("standing still keeps the last heading", 90f, r.state.value.bearing)
    }

    @Test fun routeAndManeuverAreKeptAcrossFixes() {
        val route = listOf(GeoCoord(1.2120, -77.2830), GeoCoord(1.2120, -77.2808), GeoCoord(1.2142, -77.2808))
        val r = LiveRadarSource(TravelMode.Walk, null, route, route[1]) { now }
        val start = r.state.value
        assertEquals(route, start.route)
        assertEquals(route[0], start.location)
        assertEquals("faces the first segment (east)", 90f, start.bearing, 0.5f)
        r.onFix(fix(lat = 1.2121, lon = -77.2829, accuracyM = 8f, elapsedNanos = now))
        assertEquals(route, r.state.value.route)
        assertEquals(route[1], r.state.value.maneuver)
    }

    @Test fun headingIsDerivedFromMovementWhenTheFixHasNone() {
        val start = fix(lat = 1.2120, lon = -77.2830, speedMps = Float.NaN, elapsedNanos = now - 2_000_000_000L)
        // about 24 m north in 2 s = 12 m/s, no bearing in the fix
        val next = fix(lat = 1.21222, lon = -77.2830, speedMps = Float.NaN, elapsedNanos = now)
        assertEquals(0f, LiveRadarSource.nextBearing(45f, start, next), 0.5f)
        // too slow: keep the previous heading
        val slow = fix(lat = 1.212001, lon = -77.2830, speedMps = Float.NaN, elapsedNanos = now)
        assertEquals(45f, LiveRadarSource.nextBearing(45f, start, slow))
    }
}

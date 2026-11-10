package com.wgo.navigator.service

import com.wgo.navigator.Language
import com.wgo.navigator.core.Maneuver
import com.wgo.navigator.core.RouteStep
import com.wgo.navigator.nav.NavState
import com.wgo.navigator.ui.map.GeoCoord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OngoingStatusTest {
    private fun navigating(distance: Double, maneuver: Maneuver = Maneuver.RIGHT) = NavState.Navigating(
        progressM = 0.0, remainingM = 900.0, etaS = 600.0,
        next = RouteStep(maneuver, "Calle 18", GeoCoord(1.2, -77.3), 0.0),
        distanceToNextM = distance, then = null, distanceFromRouteM = 0.0, snapped = GeoCoord(1.2, -77.3)
    )

    @Test
    fun bucketsAreCoarse() {
        assertEquals(40.0, OngoingStatus.bucketMeters(43.0), 0.0)
        assertEquals(200.0, OngoingStatus.bucketMeters(212.0), 0.0)
        assertEquals(250.0, OngoingStatus.bucketMeters(238.0), 0.0)
        assertEquals(1000.0, OngoingStatus.bucketMeters(990.0), 0.0)
        assertEquals(1800.0, OngoingStatus.bucketMeters(1840.0), 0.0)
    }

    @Test
    fun textIsShortAndLocalised() {
        assertEquals("200 m · Gira a la derecha", OngoingStatus.text(navigating(212.0), Language.ES))
        assertEquals("200 m · Turn right", OngoingStatus.text(navigating(212.0), Language.EN))
        assertEquals("1.8 km · Turn right", OngoingStatus.text(navigating(1840.0), Language.EN))
        assertEquals("1,8 km · Gira a la derecha", OngoingStatus.text(navigating(1840.0), Language.ES))
        assertEquals("Sin señal GPS", OngoingStatus.text(NavState.GpsLost(null), Language.ES))
        assertEquals("Finding GPS", OngoingStatus.text(NavState.WaitingForGps, Language.EN))
        // Short enough for the watch-face chip / Recents line.
        assertTrue(OngoingStatus.text(navigating(640.0, Maneuver.SLIGHT_RIGHT), Language.ES).length <= 40)
    }

    @Test
    fun throttlePostsOnlyChangesAtMostEveryFiveSeconds() {
        val t = OngoingStatus.Throttle()
        assertTrue(t.shouldPost("200 m · A", 0))
        assertFalse(t.shouldPost("200 m · A", 10_000)) // unchanged
        assertFalse(t.shouldPost("150 m · A", 3_000)) // changed, too soon
        assertTrue(t.shouldPost("150 m · A", 5_000))
        assertFalse(t.shouldPost("100 m · A", 9_999))
        assertTrue(t.shouldPost("100 m · A", 10_000))
        assertEquals(3, t.posts)
    }
}

package com.wgo.navigator.ui.screens

import com.wgo.navigator.ui.map.ExploreBounds
import com.wgo.navigator.ui.map.GeoCoord
import com.wgo.navigator.ui.map.RadarProjection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploreControllerTest {
    private var now = 0L
    private val user = GeoCoord(1.2290, -77.2945)
    private val route = listOf(user, GeoCoord(1.2330, -77.2900))

    @Test fun autoReturnAfterTenSecondsPausedWhileTouching() {
        assertEquals(10_000L, ExploreRules.autoReturnInMs(lastTouchMs = 0, touching = false, nowMs = 0))
        assertEquals(4_000L, ExploreRules.autoReturnInMs(lastTouchMs = 0, touching = false, nowMs = 6_000))
        assertEquals(0L, ExploreRules.autoReturnInMs(lastTouchMs = 0, touching = false, nowMs = 12_000))
        assertNull(ExploreRules.autoReturnInMs(lastTouchMs = 0, touching = true, nowMs = 30_000))
    }

    @Test fun theFirstDragStartsExploringFromTheUserWithTheHeadingFrozen() {
        val c = ExploreController { now }
        assertFalse(c.exploring)
        assertNull(c.viewCenter(user))
        c.pan(0f, -163f, user, 37f, route, 17.0, 512.0)
        assertTrue(c.exploring)
        assertEquals(37f, c.viewBearing(90f)!!, 0f) // the user's heading changes; the explore view does not turn
        val p = RadarProjection.toScreen(user, c.viewCenter(user)!!, 37.0, 17.0, 225f, 297f, 512.0)
        assertEquals(297f - 163f, p.y, 0.1f)
    }

    @Test fun touchesRestartTheTimer() {
        val c = ExploreController { now }
        now = 1_000
        c.onTouch(true)
        assertTrue(c.touching)
        now = 5_000
        c.onTouch(false)
        assertEquals(5_000L, c.lastTouchMs)
        assertFalse(c.touching)
    }

    @Test fun thePanStaysInsideTheRouteBoxPlus400m() {
        val c = ExploreController { now }
        // Content follows the finger: dragging down reveals what is above (north up), so the centre goes north;
        // dragging left reveals the east.
        repeat(60) { c.pan(0f, 400f, user, 0f, route, 17.0, 512.0) }
        repeat(60) { c.pan(-400f, 0f, user, 0f, route, 17.0, 512.0) }
        val box = ExploreBounds.around(route + user)!!
        val center = c.viewCenter(user)!!
        assertTrue(center in box)
        assertEquals(box.north, center.lat, 1e-9)
        assertEquals(box.east, center.lon, 1e-9)
    }

    @Test fun recenterGlidesBackThenFollows() {
        val c = ExploreController { now }
        c.pan(100f, 0f, user, 10f, route, 17.0, 512.0)
        val explored = c.viewCenter(user)!!
        c.startReturn()
        c.glide = 0f
        assertEquals(explored, c.viewCenter(user))
        c.glide = 0.5f
        val mid = c.viewCenter(user)!!
        assertEquals((explored.lat + user.lat) / 2, mid.lat, 1e-9)
        assertEquals(ExploreRules.lerpBearing(10f, 50f, 0.5f), c.viewBearing(50f)!!, 1e-3f)
        c.finish()
        assertFalse(c.exploring)
        assertNull(c.viewCenter(user))
        assertNull(c.viewBearing(50f))
    }

    @Test fun headingsInterpolateTheShortWay() {
        assertEquals(0f, ExploreRules.lerpBearing(350f, 10f, 0.5f), 1e-3f)
        assertEquals(180f, ExploreRules.lerpBearing(170f, 190f, 0.5f), 1e-3f)
        assertEquals(10f, ExploreRules.lerpBearing(350f, 10f, 1f), 1e-3f)
    }
}

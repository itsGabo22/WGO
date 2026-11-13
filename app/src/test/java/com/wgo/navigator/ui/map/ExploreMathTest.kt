package com.wgo.navigator.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploreMathTest {
    private val user = GeoCoord(1.2290, -77.2945)

    @Test fun aDragMovesTheUserWithTheFingerAtAnyHeading() {
        for (bearing in listOf(0.0, 37.0, 90.0, 180.0, 271.0)) {
            for ((dx, dy) in listOf(0.0 to -163.0, 120.0 to 0.0, -80.0 to 55.0)) {
                val center = RadarProjection.panCenter(user, dx, dy, bearing, 17.0, 512.0)
                val p = RadarProjection.toScreen(user, center, bearing, 17.0, 225f, 297f, 512.0)
                assertEquals("bearing $bearing x", 225.0 + dx, p.x.toDouble(), 0.05)
                assertEquals("bearing $bearing y", 297.0 + dy, p.y.toDouble(), 0.05)
            }
        }
    }

    @Test fun worldPixelInversesRoundTrip() {
        val wx = RadarProjection.worldX(user.lon, 17.0, 512.0)
        val wy = RadarProjection.worldY(user.lat, 17.0, 512.0)
        assertEquals(user.lon, RadarProjection.lonOfWorldX(wx, 17.0, 512.0), 1e-9)
        assertEquals(user.lat, RadarProjection.latOfWorldY(wy, 17.0, 512.0), 1e-9)
    }

    @Test fun theClampIsTheRouteBoxGrownBy400m() {
        val route = listOf(user, GeoCoord(1.2330, -77.2900))
        val box = ExploreBounds.around(route)!!
        val south = GeoCoord(box.south, user.lon)
        assertEquals(400.0, RadarProjection.distanceMeters(GeoCoord(user.lat, user.lon), south), 1.0)
        val east = GeoCoord(1.2330, box.east)
        assertEquals(400.0, RadarProjection.distanceMeters(GeoCoord(1.2330, -77.2900), east), 2.0)
        // Inside stays put; outside is pulled to the edge.
        val inside = GeoCoord(1.2300, -77.2920)
        assertEquals(inside, box.clamp(inside))
        val far = GeoCoord(1.30, -77.20)
        assertFalse(far in box)
        assertTrue(box.clamp(far) in box)
        assertEquals(box.north, box.clamp(far).lat, 0.0)
        assertEquals(box.east, box.clamp(far).lon, 0.0)
    }
}

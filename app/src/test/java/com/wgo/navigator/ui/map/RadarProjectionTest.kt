package com.wgo.navigator.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarProjectionTest {
    private val pasto = GeoCoord(1.2136, -77.2811)

    @Test fun centerLandsOnTheAnchor() {
        val p = RadarProjection.toScreen(pasto, pasto, 37.0, 17.0, 192f, 254f, 512.0)
        assertEquals(192f, p.x, 0.001f)
        assertEquals(254f, p.y, 0.001f)
    }

    @Test fun northIsUpWhenHeadingNorth() {
        val north = RadarProjection.destination(pasto, 0.0, 100.0)
        val p = RadarProjection.toScreen(north, pasto, 0.0, 17.0, 192f, 254f, 512.0)
        assertEquals(192f, p.x, 0.5f)
        // 100 m = 167.7 px at zoom 17 with 512 px tiles near Pasto (value cross-checked against osmdroid's own projection)
        assertEquals(254f - 167.7f, p.y, 0.5f)
    }

    @Test fun headingEastPutsEastUp() {
        val east = RadarProjection.destination(pasto, 90.0, 100.0)
        val p = RadarProjection.toScreen(east, pasto, 90.0, 17.0, 192f, 254f, 512.0)
        assertEquals(192f, p.x, 0.5f)
        assertEquals(254f - 167.7f, p.y, 0.5f)
    }

    @Test fun headingEastPutsNorthOnTheLeft() {
        val north = RadarProjection.destination(pasto, 0.0, 100.0)
        val p = RadarProjection.toScreen(north, pasto, 90.0, 17.0, 192f, 254f, 512.0)
        assertEquals(192f - 167.7f, p.x, 0.5f)
        assertEquals(254f, p.y, 0.5f)
    }

    @Test fun zoomingInDoublesDistancesOnScreen() {
        val north = RadarProjection.destination(pasto, 0.0, 100.0)
        val z16 = RadarProjection.toScreen(north, pasto, 0.0, 16.0, 0f, 0f, 512.0)
        val z17 = RadarProjection.toScreen(north, pasto, 0.0, 17.0, 0f, 0f, 512.0)
        assertEquals(2.0, (z17.y / z16.y).toDouble(), 0.001)
    }

    @Test fun groundResolutionAtTheEquator() {
        assertEquals(156543.03392, RadarProjection.metersPerPixel(0.0, 0.0), 0.001)
        assertEquals(78271.51696, RadarProjection.metersPerPixel(0.0, 1.0), 0.001)
    }

    @Test fun ringMetersGrowWithRadius() {
        val a = RadarProjection.ringMeters(100.0, pasto.lat, 17.0, 512.0)
        val b = RadarProjection.ringMeters(200.0, pasto.lat, 17.0, 512.0)
        assertEquals(2.0, b / a, 1e-9)
    }

    @Test fun cardinalBearings() {
        assertEquals(0.0, RadarProjection.bearingBetween(pasto, GeoCoord(pasto.lat + 0.001, pasto.lon)), 0.01)
        assertEquals(90.0, RadarProjection.bearingBetween(pasto, GeoCoord(pasto.lat, pasto.lon + 0.001)), 0.01)
        assertEquals(180.0, RadarProjection.bearingBetween(pasto, GeoCoord(pasto.lat - 0.001, pasto.lon)), 0.01)
        assertEquals(270.0, RadarProjection.bearingBetween(pasto, GeoCoord(pasto.lat, pasto.lon - 0.001)), 0.01)
    }

    @Test fun distanceOfOneMilliDegreeOfLatitude() {
        assertEquals(111.19, RadarProjection.distanceMeters(pasto, GeoCoord(pasto.lat + 0.001, pasto.lon)), 0.05)
    }

    @Test fun destinationRoundTripsDistanceAndBearing() {
        val moved = RadarProjection.destination(pasto, 135.0, 250.0)
        assertEquals(250.0, RadarProjection.distanceMeters(pasto, moved), 0.01)
        assertEquals(135.0, RadarProjection.bearingBetween(pasto, moved), 0.01)
    }

    @Test fun demoRouteStartsHeadingEastThenTurnsNorth() {
        val start = SimulatedRadarSource.stateAt(0.0)
        assertEquals(90f, start.bearing, 0.5f)
        assertEquals(SimulatedRadarSource.DemoRoute.first().lat, start.location.lat, 1e-6)

        val firstSegment = RadarProjection.distanceMeters(SimulatedRadarSource.DemoRoute[0], SimulatedRadarSource.DemoRoute[1])
        val afterTurn = SimulatedRadarSource.stateAt(firstSegment + 5.0)
        assertEquals(0f, afterTurn.bearing, 0.5f)
    }

    @Test fun routeStartsAtTheUserAndShrinksAsTheUserAdvances() {
        val early = SimulatedRadarSource.stateAt(10.0)
        val late = SimulatedRadarSource.stateAt(400.0)
        assertEquals(early.location, early.route.first())
        assertTrue(late.route.size < early.route.size)
        assertTrue(early.maneuver != null)
    }

    @Test fun walkingWrapsAroundAtTheEndOfTheRoute() {
        val total = polylineLength(SimulatedRadarSource.DemoRoute)
        val wrapped = SimulatedRadarSource.stateAt(total + 20.0)
        val early = SimulatedRadarSource.stateAt(20.0)
        assertEquals(early.location.lat, wrapped.location.lat, 1e-6)
        assertEquals(early.location.lon, wrapped.location.lon, 1e-6)
    }
}

package com.wgo.navigator.route

import com.wgo.navigator.TravelMode
import com.wgo.navigator.ui.map.GeoCoord
import com.wgo.navigator.ui.map.PastoCentre
import com.wgo.navigator.ui.map.RadarProjection
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

/** Mode-selection estimates against real OSRM routes measured live (route length m, route minutes). */
class ModeEstimateTest {
    private data class Known(val name: String, val from: GeoCoord, val to: GeoCoord, val mode: TravelMode, val routeM: Double, val minutes: Double)

    private val udenar = GeoCoord(1.23253, -77.29339)
    private val terminal = GeoCoord(1.1983981, -77.2692668)

    private val known = listOf(
        Known("Udenar walk", PastoCentre, udenar, TravelMode.Walk, 2903.0, 38.8),
        Known("Udenar drive", PastoCentre, udenar, TravelMode.Drive, 3573.0, 6.5),
        Known("Terminal drive", PastoCentre, terminal, TravelMode.Drive, 3083.0, 4.6),
        Known("Terminal walk", PastoCentre, terminal, TravelMode.Walk, 2335.0, 31.3)
    )

    @Test fun estimatesStayCloseToRealRoutes() {
        for (k in known) {
            val estimate = ModeEstimate.seconds(RadarProjection.distanceMeters(k.from, k.to), k.mode) / 60
            val allowed = max(1.0, k.minutes * 0.15)
            assertTrue("${k.name}: estimate %.1f vs real %.1f".format(estimate, k.minutes), abs(estimate - k.minutes) <= allowed)
        }
    }

    @Test fun theOldFormulaWasTooSlowForDriving() {
        val crow = RadarProjection.distanceMeters(PastoCentre, terminal)
        val old = (crow * 1.4 / 6.5 + 60) / 60
        assertTrue("old %.1f min for a 4.6 min drive".format(old), old > 8)
        assertTrue(ModeEstimate.minutes(PastoCentre, terminal, TravelMode.Drive) <= 6)
    }
}

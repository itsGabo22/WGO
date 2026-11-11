package com.wgo.navigator.ui.screens

import com.wgo.navigator.core.Maneuver
import com.wgo.navigator.core.RouteStep
import com.wgo.navigator.nav.NavState
import com.wgo.navigator.ui.map.GeoCoord
import com.wgo.navigator.ui.theme.WGO_DIAL_RADIUS
import com.wgo.navigator.ui.theme.wgoFit
import com.wgo.navigator.ui.theme.wgoFits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientNavigationTest {
    private fun nav(distance: Double, maneuver: Maneuver = Maneuver.RIGHT, at: Double = 0.0) = NavState.Navigating(
        progressM = 0.0, remainingM = 900.0, etaS = 600.0,
        next = RouteStep(maneuver, "", GeoCoord(1.2 + at, -77.3), 0.0),
        distanceToNextM = distance, then = null, distanceFromRouteM = 0.0, snapped = GeoCoord(1.2, -77.3)
    )

    /**
     * Burn-in policy (current Wear OS guidance, chosen over the old "shift 1-2 dp per minute"): the app never shifts
     * content; the system shifts the display when burnInProtectionRequired, so every element keeps >= 10 dp of margin
     * inside the dial (fit <= 103) and is never clipped by that shift.
     */
    @Test
    fun everyElementObeysTheFitRuleWithBurnInMargin() {
        val boxes = mapOf(
            "icon" to Triple(AmbientLayout.ICON, AmbientLayout.ICON, AmbientLayout.ICON_TOP),
            "distance" to Triple(AmbientLayout.DISTANCE_W, AmbientLayout.DISTANCE_H, AmbientLayout.DISTANCE_TOP),
            "text" to Triple(AmbientLayout.TEXT_W, AmbientLayout.TEXT_H, AmbientLayout.TEXT_TOP),
            "totals" to Triple(AmbientLayout.TOTALS_W, AmbientLayout.TOTALS_H, AmbientLayout.TOTALS_TOP)
        )
        for ((name, b) in boxes) {
            val fit = wgoFit(b.first, b.second, b.third)
            println("ambient $name fit=${"%.1f".format(fit)}")
            assertTrue("$name fit=$fit", wgoFits(b.first, b.second, b.third))
            assertTrue("$name margin", WGO_DIAL_RADIUS - fit >= 10f)
        }
        // No overlap from top to bottom.
        assertTrue(AmbientLayout.ICON_TOP + AmbientLayout.ICON <= AmbientLayout.DISTANCE_TOP)
        assertTrue(AmbientLayout.DISTANCE_TOP + AmbientLayout.DISTANCE_H <= AmbientLayout.TEXT_TOP)
        assertTrue(AmbientLayout.TEXT_TOP + AmbientLayout.TEXT_H <= AmbientLayout.TOTALS_TOP)
    }

    @Test
    fun redrawsOnTickOrWhenTheManeuverChangesOnly() {
        val first = nav(300.0)
        val s = AmbientSnapshot(first)
        // A new fix every second toward the same maneuver: no redraw between ticks.
        assertFalse(s.update(nav(290.0), tick = 0))
        assertFalse(s.update(nav(280.0), tick = 0))
        assertEquals(first, s.shown)
        // The ambient tick (about once a minute) shows the latest values.
        assertTrue(s.update(nav(270.0), tick = 1))
        assertEquals(270.0, (s.shown as NavState.Navigating).distanceToNextM, 0.0)
        // A new maneuver, or a GPS loss, shows at once without waiting for the tick.
        assertTrue(s.update(nav(400.0, Maneuver.LEFT, at = 0.01), tick = 1))
        assertTrue(s.update(NavState.GpsLost(nav(390.0, Maneuver.LEFT, at = 0.01)), tick = 1))
        assertTrue(s.shown is NavState.GpsLost)
        assertFalse(s.update(NavState.GpsLost(nav(390.0, Maneuver.LEFT, at = 0.01)), tick = 1))
    }
}

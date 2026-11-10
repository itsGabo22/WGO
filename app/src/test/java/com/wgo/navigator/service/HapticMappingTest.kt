package com.wgo.navigator.service

import com.wgo.navigator.core.Maneuver
import com.wgo.navigator.core.RouteStep
import com.wgo.navigator.haptics.HapticEvent
import com.wgo.navigator.nav.NavEvent
import com.wgo.navigator.ui.map.GeoCoord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Spec section 8: the trip's haptics, played by the host (arrival is played when the trip ends). */
class HapticMappingTest {
    private val step = RouteStep(Maneuver.RIGHT, "", GeoCoord(1.2, -77.3), 0.0)

    @Test
    fun turnsAndGpsLostFollowTheSpec() {
        assertEquals(HapticEvent.Tick, hapticFor(NavEvent.ApproachingTurn(step, 200.0)))
        assertEquals(HapticEvent.DoubleClick, hapticFor(NavEvent.AtTurn(step, 50.0)))
        assertEquals(HapticEvent.HeavyClick, hapticFor(NavEvent.GpsLost))
    }

    @Test
    fun eventsWithoutASpecRowAreSilent() {
        listOf(NavEvent.Arrived, NavEvent.OffRouteDetected, NavEvent.Rerouted, NavEvent.GpsRecovered)
            .forEach { assertNull(it.toString(), hapticFor(it)) }
    }
}

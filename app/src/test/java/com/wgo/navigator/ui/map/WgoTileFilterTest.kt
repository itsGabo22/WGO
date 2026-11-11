package com.wgo.navigator.ui.map

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class WgoTileFilterTest {
    private val ground = Color(0.8f, 0.6f, 0.4f)
    private val street = Color(0.1f, 0.2f, 0.3f)
    private val m = WgoTileFilter.streetArray(ground, street)

    private fun rgb01(px: FloatArray) = WgoTileFilter.apply(m, px).map { it / 255f }

    @Test fun osmLandMapsExactlyToGround() {
        val out = rgb01(WgoTileFilter.OSM_LAND)
        assertEquals(ground.red, out[0], 0.002f)
        assertEquals(ground.green, out[1], 0.002f)
        assertEquals(ground.blue, out[2], 0.002f)
    }

    @Test fun osmStreetMapsExactlyToStreet() {
        val out = rgb01(WgoTileFilter.OSM_STREET)
        assertEquals(street.red, out[0], 0.002f)
        assertEquals(street.green, out[1], 0.002f)
        assertEquals(street.blue, out[2], 0.002f)
    }

    @Test fun alphaRowIsIdentity() = assertEquals(listOf(0f, 0f, 0f, 1f, 0f), m.slice(15..19))

    @Test fun matrixHasTwentyEntries() = assertEquals(20, m.size)
}

package com.wgo.navigator.ui.map

import com.wgo.navigator.TravelMode
import com.wgo.navigator.ui.theme.DaylightPalette
import com.wgo.navigator.ui.theme.MapStyleId
import com.wgo.navigator.ui.theme.NightPalette
import com.wgo.navigator.ui.theme.wgoMapStyle
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * PART 5 (P6-B) guards: the radar's look depends only on the chosen style and the theme, and a tile that has not
 * loaded yet is drawn as the style's ground, never a plain page.
 */
class MapStyleSelectionTest {
    /** Everything that decides the radar's look for a trip; the travel mode is deliberately one of the inputs. */
    private fun radarLook(id: MapStyleId, dark: Boolean, @Suppress("UNUSED_PARAMETER") mode: TravelMode): Pair<Any, FloatArray> {
        val style = wgoMapStyle(id, if (dark) NightPalette else DaylightPalette)
        return style to WgoTileFilter.streetArray(style.palette.ground, style.palette.street)
    }

    @Test fun walkingAndDrivingGetTheSameStyleInEveryStyleAndTheme() {
        for (id in MapStyleId.entries) for (dark in listOf(false, true)) {
            val (walkStyle, walkFilter) = radarLook(id, dark, TravelMode.Walk)
            val (driveStyle, driveFilter) = radarLook(id, dark, TravelMode.Drive)
            assertEquals("$id dark=$dark", walkStyle, driveStyle)
            assertArrayEquals("$id dark=$dark", walkFilter, driveFilter, 0f)
        }
    }

    @Test fun aTileStillLoadingIsDrawnAsTheStyleGround() {
        val c = WgoTileFilter.LOADING_TILE_ARGB
        val loading = floatArrayOf(((c shr 16) and 0xFF).toFloat(), ((c shr 8) and 0xFF).toFloat(), (c and 0xFF).toFloat())
        for (id in MapStyleId.entries) for (dark in listOf(false, true)) {
            val style = wgoMapStyle(id, if (dark) NightPalette else DaylightPalette)
            val out = WgoTileFilter.apply(WgoTileFilter.streetArray(style.palette.ground, style.palette.street), loading)
            assertEquals("$id red", style.palette.ground.red * 255f, out[0], 1f)
            assertEquals("$id green", style.palette.ground.green * 255f, out[1], 1f)
            assertEquals("$id blue", style.palette.ground.blue * 255f, out[2], 1f)
        }
    }
}

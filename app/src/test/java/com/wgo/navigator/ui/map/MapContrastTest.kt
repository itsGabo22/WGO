package com.wgo.navigator.ui.map

import androidx.compose.ui.graphics.Color
import com.wgo.navigator.ui.theme.DaylightPalette
import com.wgo.navigator.ui.theme.MapStyleId
import com.wgo.navigator.ui.theme.NightPalette
import com.wgo.navigator.ui.theme.wgoMapStyle
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Map legibility targets (spec section 9.1, P6-B), every style, Daylight and Night: OSM streets against land >= 3:1
 * and the route against land >= 4.5:1 (against its halo >= 3:1), after the real tile filter and the Night dim. The
 * OSM colours are the ones measured on the Pasto tiles cached on the Galaxy Watch5 Pro.
 */
class MapContrastTest {
    private val land = floatArrayOf(0xF2.toFloat(), 0xEF.toFloat(), 0xE9.toFloat())
    private val street = floatArrayOf(255f, 255f, 255f)
    private val secondary = floatArrayOf(0xF7.toFloat(), 0xFA.toFloat(), 0xBF.toFloat())
    private val primary = floatArrayOf(0xFC.toFloat(), 0xD6.toFloat(), 0xA4.toFloat())

    private fun lin(v: Float): Double {
        val c = v / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun lum(rgb: FloatArray) = 0.2126 * lin(rgb[0]) + 0.7152 * lin(rgb[1]) + 0.0722 * lin(rgb[2])

    private fun contrast(a: FloatArray, b: FloatArray): Double {
        val (hi, lo) = listOf(lum(a), lum(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun Color.rgb() = floatArrayOf(red * 255f, green * 255f, blue * 255f)
    private fun FloatArray.dim(d: Float) = FloatArray(3) { this[it] * (1f - d) }

    private data class Row(val style: String, val street: Double, val major: Double, val routeLand: Double, val routeHalo: Double)

    private fun rows(): List<Row> = MapStyleId.entries.flatMap { id ->
        listOf(false, true).map { dark ->
            val style = wgoMapStyle(id, if (dark) NightPalette else DaylightPalette)
            val m = WgoTileFilter.streetArray(style.palette.ground, style.palette.street)
            val d = if (dark && id.dimsInNight()) id.nightDimAlpha() else 0f
            val l = WgoTileFilter.apply(m, land).dim(d)
            val st = WgoTileFilter.apply(m, street).dim(d)
            val major = minOf(contrast(WgoTileFilter.apply(m, secondary).dim(d), l), contrast(WgoTileFilter.apply(m, primary).dim(d), l))
            val route = style.palette.route.rgb().dim(d)
            val halo = style.palette.routeHalo.rgb().dim(d)
            Row("$id ${if (dark) "night" else "day"}", contrast(st, l), major, contrast(route, l), contrast(route, halo))
        }
    }

    @Test fun everyStyleMeetsTheLegibilityTargets() {
        val table = rows()
        println(table.joinToString("\n") { "%-16s street %5.2f  major %5.2f  route:land %5.2f  route:halo %5.2f".format(it.style, it.street, it.major, it.routeLand, it.routeHalo) })
        table.forEach {
            assertTrue("${it.style} street ${it.street}", it.street >= 3.0)
            assertTrue("${it.style} main roads ${it.major}", it.major >= 3.0)
            assertTrue("${it.style} route:land ${it.routeLand}", it.routeLand >= 4.5)
            assertTrue("${it.style} route:halo ${it.routeHalo}", it.routeHalo >= 3.0)
        }
    }

    @Test fun theOldFiltersFailedTheTargets() {
        // Documented baseline (P6-B audit): land and white streets landed on the same colour.
        assertTrue(contrast(land, street) < 1.2)
    }
}

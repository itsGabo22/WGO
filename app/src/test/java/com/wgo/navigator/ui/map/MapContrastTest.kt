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
 * Map legibility targets (spec section 9.1), every style, Daylight and Night, for both tile bases (P7: CARTO Dark
 * Matter, the default with a key; P6-B: OpenStreetMap, the fallback): streets against land >= 3:1, main roads >= 3:1,
 * the route against land >= 4.5:1 and against its halo >= 3:1, the route standing out from the streets, after the real
 * tile filter and the Night dim; and (P7) the label text against its halo >= 4.5:1 over the style's ground and streets.
 * Base colours are the ones measured over Pasto (OSM on the Galaxy Watch5 Pro cache, CARTO in the P7 audit).
 */
class MapContrastTest {
    private class BaseColours(val land: FloatArray, val street: FloatArray, val majors: List<FloatArray>)

    private fun rgb(hex: Int) = floatArrayOf(((hex shr 16) and 0xFF).toFloat(), ((hex shr 8) and 0xFF).toFloat(), (hex and 0xFF).toFloat())

    private val bases = mapOf(
        TileBase.OSM to BaseColours(rgb(0xF2EFE9), rgb(0xFFFFFF), listOf(rgb(0xF7FABF), rgb(0xFCD6A4))),
        TileBase.DARK_MATTER to BaseColours(rgb(0x0E0E0E), rgb(0x414758), listOf(rgb(0x535666), rgb(0x3F4455)))
    )

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
    private fun mix(under: FloatArray, over: FloatArray, alpha: Float) = FloatArray(3) { under[it] * (1 - alpha) + over[it] * alpha }

    private data class Row(
        val style: String, val street: Double, val major: Double, val routeLand: Double, val routeHalo: Double,
        val routeStreet: Double = 0.0, val haloStreet: Double = 0.0, val label: Double = 99.0
    ) {
        /** The route stands out from the streets: by itself (>= 3:1) or through a contrasting halo (casing). */
        val routeOverStreets get() = routeStreet >= 3.0 || (haloStreet >= 3.0 && routeHalo >= 3.0)
    }

    /** CARTO's label pixels (light_only_labels, measured): text ~#5c666f at 90 %, halo #f8f8f8 at <= 60 %. */
    private val labelText = rgb(0x5C666F)
    private val labelHalo = rgb(0xF8F8F8)

    /** Label text vs its halo, both through the label filter, over [backgrounds] (worst case). */
    private fun labelContrast(inverted: Boolean, backgrounds: List<FloatArray>, d: Float): Double {
        val m = WgoTileFilter.labelArray(inverted)
        val text = WgoTileFilter.apply(m, labelText).dim(d)
        val halo = WgoTileFilter.apply(m, labelHalo).dim(d)
        val aText = WgoTileFilter.applyAlpha(m, labelText, 0.90f * 255) / 255f
        val aHalo = WgoTileFilter.applyAlpha(m, labelHalo, 0.60f * 255) / 255f
        return backgrounds.minOf { bg ->
            val under = mix(bg, halo, aHalo)
            contrast(mix(under, text, aText), under)
        }
    }

    private fun rows(base: TileBase): List<Row> = MapStyleId.entries.flatMap { id ->
        listOf(false, true).map { dark ->
            val c = bases.getValue(base)
            val style = wgoMapStyle(id, if (dark) NightPalette else DaylightPalette)
            val m = WgoTileFilter.streetArray(style.palette.ground, style.palette.street, base)
            val d = if (dark && id.dimsInNight()) id.nightDimAlpha() else 0f
            val l = WgoTileFilter.apply(m, c.land).dim(d)
            val st = WgoTileFilter.apply(m, c.street).dim(d)
            val major = c.majors.minOf { contrast(WgoTileFilter.apply(m, it).dim(d), l) }
            val route = style.palette.route.rgb().dim(d)
            val halo = style.palette.routeHalo.rgb().dim(d)
            Row(
                "$base $id ${if (dark) "night" else "day"}", contrast(st, l), major, contrast(route, l), contrast(route, halo),
                routeStreet = contrast(route, st), haloStreet = contrast(halo, st),
                label = if (base == TileBase.DARK_MATTER) labelContrast(WgoTileFilter.labelsInverted(style), listOf(l, st), d) else 99.0
            )
        }
    }

    private fun check(table: List<Row>) {
        println(table.joinToString("\n") {
            "%-28s street %5.2f  major %5.2f  route:land %5.2f  route:halo %5.2f  route:street %5.2f  halo:street %5.2f  label %5.2f  %s".format(
                it.style, it.street, it.major, it.routeLand, it.routeHalo, it.routeStreet, it.haloStreet, it.label,
                if (it.routeOverStreets) "ok" else "ROUTE BLENDS WITH STREETS"
            )
        })
        table.forEach {
            assertTrue("${it.style} street ${it.street}", it.street >= 3.0)
            assertTrue("${it.style} main roads ${it.major}", it.major >= 3.0)
            assertTrue("${it.style} route:land ${it.routeLand}", it.routeLand >= 4.5)
            assertTrue("${it.style} route:halo ${it.routeHalo}", it.routeHalo >= 3.0)
            assertTrue("${it.style} route over streets: ${it.routeStreet} / halo ${it.haloStreet}", it.routeOverStreets)
            assertTrue("${it.style} label text vs halo ${it.label}", it.label >= 4.5)
        }
    }

    @Test fun cartoBaseMeetsTheLegibilityTargets() = check(rows(TileBase.DARK_MATTER))

    @Test fun osmFallbackMeetsTheLegibilityTargets() = check(rows(TileBase.OSM))

    @Test fun cartoBuildingsAndWaterStayNearTheGround() {
        // Dark Matter buildings (#2b2b2b) score about 0.24 (faint blocks), water (#0a0a0a) at or below the ground.
        val w = TileBase.DARK_MATTER.weights
        fun t(rgb: FloatArray): Float {
            fun dot(c: FloatArray) = w[0] * c[0] + w[1] * c[1] + w[2] * c[2]
            return (dot(rgb) - dot(TileBase.DARK_MATTER.land)) / (dot(TileBase.DARK_MATTER.street) - dot(TileBase.DARK_MATTER.land))
        }
        assertTrue(t(rgb(0x2B2B2B)) in 0.15f..0.35f)
        assertTrue(t(rgb(0x0A0A0A)) <= 0f)
        assertTrue(t(rgb(0x535666)) >= 1f)
    }

    @Test fun rawCartoLabelsFailedTheTarget() {
        // Documented baseline (P7 audit): CARTO's own label text against its halo is under 3:1.
        assertTrue(contrast(labelText, labelHalo) < 6.0)
        assertTrue(contrast(mix(labelHalo, labelText, 0.9f), mix(rgb(0x0E0E0E), labelHalo, 0.6f)) < 4.5)
    }

    @Test fun theOldFiltersFailedTheTargets() {
        // Documented baseline (P6-B audit): land and white streets landed on the same colour.
        assertTrue(contrast(rgb(0xF2EFE9), rgb(0xFFFFFF)) < 1.2)
    }
}

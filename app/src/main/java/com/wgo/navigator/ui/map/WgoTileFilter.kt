package com.wgo.navigator.ui.map

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.compose.ui.graphics.Color
import com.wgo.navigator.ui.theme.DaylightPalette
import com.wgo.navigator.ui.theme.MapStyleId
import com.wgo.navigator.ui.theme.NightPalette
import com.wgo.navigator.ui.theme.WgoMapPalette
import com.wgo.navigator.ui.theme.WgoMapStyle

/**
 * Color filters applied over the raster tiles so they read as a radar background. Every tint comes from the active
 * theme's `map-*` palette; the numbers below are blend factors, not colors.
 */
object WgoTileFilter {
    // Luminance weights (Rec. 709).
    private const val LR = 0.2126f
    private const val LG = 0.7152f
    private const val LB = 0.0722f

    /** Daylight: desaturate and lift the tiles toward `map-ground` (low contrast, the route dominates). */
    private const val DAYLIGHT_GROUND_MIX = 0.40f

    /** Night: invert luminance (a = -NIGHT_GAIN, b = NIGHT_GAIN) and tint between `map-ground` and `map-road-major`. */
    private const val NIGHT_GAIN = 2.0f

    /** Luminance of a typical OSM land tile (about #F2EFE9): it becomes the style's PAPER color. */
    private const val TILE_PAPER_LUMINANCE = 0.93f

    /**
     * The filter for [style]. COBALTO keeps its theme filter; the other seven turn the desaturated tile into a
     * duotone between PAPER (the style `ground`) and INK (what streets and labels become): `roadMajor` for the dark
     * styles and `roadCasing` for the light ones. The contrast knob keeps the street grid faint so the route dominates.
     */
    fun filterFor(style: WgoMapStyle, dark: Boolean): ColorMatrixColorFilter {
        val map = style.palette
        return when (style.id) {
            MapStyleId.COBALTO -> cobalto(dark)
            MapStyleId.BARRIO -> duotone(map.ground, map.roadMajor, 0.85f)
            MapStyleId.PERGAMINO -> duotone(map.ground, map.roadCasing, 0.70f)
            MapStyleId.NEON -> duotone(map.ground, map.roadMajor, 0.30f)
            MapStyleId.PIXEL -> duotone(map.ground, map.roadCasing, 0.60f)
            MapStyleId.CIRCUITO -> duotone(map.ground, map.roadMajor, 0.25f)
            MapStyleId.SONAR -> duotone(map.ground, map.roadMajor, 0.45f)
            MapStyleId.ISLA -> duotone(map.ground, map.roadCasing, 0.70f)
        }
    }

    private fun duotone(paper: Color, ink: Color, contrast: Float) =
        ColorMatrixColorFilter(ColorMatrix(duotoneArray(paper, ink, contrast)))

    /**
     * Row-major 4x5 matrix for `out = ink + (paper - ink) * t`, with `t = contrast * luminance + (1 - contrast * 0.93)`,
     * so a land-colored tile (luminance 0.93) maps exactly to [paper] whatever the contrast; darker features (street
     * casings, labels) move toward [ink]. Pure function (no Android types) so it can be unit-tested.
     */
    fun duotoneArray(paper: Color, ink: Color, contrast: Float): FloatArray {
        val bias = 1f - contrast * TILE_PAPER_LUMINANCE
        fun row(p: Float, i: Float): FloatArray {
            val span = p - i
            return floatArrayOf(span * contrast * LR, span * contrast * LG, span * contrast * LB, 0f, 255f * (i + span * bias))
        }
        val r = row(paper.red, ink.red)
        val g = row(paper.green, ink.green)
        val b = row(paper.blue, ink.blue)
        return floatArrayOf(
            r[0], r[1], r[2], r[3], r[4],
            g[0], g[1], g[2], g[3], g[4],
            b[0], b[1], b[2], b[3], b[4],
            0f, 0f, 0f, 1f, 0f
        )
    }

    fun cobalto(dark: Boolean): ColorMatrixColorFilter {
        val map = if (dark) NightPalette.map else DaylightPalette.map
        return ColorMatrixColorFilter(if (dark) night(map) else daylight(map))
    }

    /** out = (1 - k) * luminance + k * ground */
    internal fun daylight(map: WgoMapPalette): ColorMatrix {
        val k = DAYLIGHT_GROUND_MIX
        val ground = map.ground
        fun row(groundChannel: Float) =
            floatArrayOf((1 - k) * LR, (1 - k) * LG, (1 - k) * LB, 0f, 255f * k * groundChannel)
        return matrix(row(ground.red), row(ground.green), row(ground.blue))
    }

    /** out = ground + (roadMajor - ground) * (b + a * luminance), with a = -gain and b = gain. */
    internal fun night(map: WgoMapPalette): ColorMatrix {
        val a = -NIGHT_GAIN
        val b = NIGHT_GAIN
        fun row(from: Float, to: Float): FloatArray {
            val span = to - from
            return floatArrayOf(span * a * LR, span * a * LG, span * a * LB, 0f, 255f * (from + span * b))
        }
        return matrix(
            row(map.ground.red, map.roadMajor.red),
            row(map.ground.green, map.roadMajor.green),
            row(map.ground.blue, map.roadMajor.blue)
        )
    }

    private fun matrix(r: FloatArray, g: FloatArray, b: FloatArray) = ColorMatrix(
        floatArrayOf(
            r[0], r[1], r[2], r[3], r[4],
            g[0], g[1], g[2], g[3], g[4],
            b[0], b[1], b[2], b[3], b[4],
            0f, 0f, 0f, 1f, 0f
        )
    )
}

package com.wgo.navigator.ui.map

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
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

    /** The filter for [style]. For now every style uses the COBALTO filter of the active theme. */
    fun filterFor(style: WgoMapStyle, dark: Boolean): ColorMatrixColorFilter {
        // TODO(P4b): MapStyleId.BARRIO  - green-grey tint toward its ground, keep roads readable.
        // TODO(P4b): MapStyleId.PERGAMINO - sepia tint (warm, low saturation) toward its parchment ground.
        // TODO(P4b): MapStyleId.NEON - high-contrast invert with a violet tint.
        // TODO(P4b): MapStyleId.PIXEL - four-level posterize toward its green ramp.
        // TODO(P4b): MapStyleId.CIRCUITO - dark high-contrast grayscale.
        // TODO(P4b): MapStyleId.SONAR - green-channel only, near-black ground.
        // TODO(P4b): MapStyleId.ISLA - saturated warm tint toward its sand ground.
        return when (style.id) {
            MapStyleId.COBALTO,
            MapStyleId.BARRIO,
            MapStyleId.PERGAMINO,
            MapStyleId.NEON,
            MapStyleId.PIXEL,
            MapStyleId.CIRCUITO,
            MapStyleId.SONAR,
            MapStyleId.ISLA -> cobalto(dark)
        }
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

package com.wgo.navigator.ui.map

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.wgo.navigator.ui.theme.WgoMapStyle

/**
 * The raster base a style is recoloured from (spec section 9.1). Each has its own "street score" weights on R, G, B and
 * the two anchors of the score: its land colour (-> the style's `ground`) and its street fill (-> the style's `street`).
 */
enum class TileBase(val weights: FloatArray, val land: FloatArray, val street: FloatArray) {
    /**
     * CARTO Dark Matter without labels (P7, the default with a key): land #0e0e0e, roads #414758. The roads are the only
     * blue-tinted feature, so blue minus red separates them: major roads #535666 score 1.05, buildings #2b2b2b 0.24,
     * water #0a0a0a -0.04.
     */
    DARK_MATTER(floatArrayOf(-2f, 0f, 3f), floatArrayOf(14f, 14f, 14f), floatArrayOf(65f, 71f, 88f)),

    /**
     * OpenStreetMap Carto (P6-B; the fallback without a key): land #F2EFE9, white minor streets, yellow / orange main
     * roads scoring about 0.85; weights favour red and green (OSM road fills are white, yellow and orange). Side effect:
     * school / retail areas (#FFFFE5, #FFD6D1) take the street colour, as filled blocks.
     */
    OSM(floatArrayOf(1.4f, 0.3f, -0.15f), floatArrayOf(242f, 239f, 233f), floatArrayOf(255f, 255f, 255f));

    /**
     * ARGB of osmdroid's loading placeholder (background and grid lines): the base's land colour, so the filter turns a
     * tile that has not arrived yet into the style's `ground` instead of a plain page.
     */
    val loadingArgb: Int
        get() = (0xFF shl 24) or (land[0].toInt() shl 16) or (land[1].toInt() shl 8) or land[2].toInt()
}

/**
 * The colour filters of the map (spec section 9.1, legibility). The base filter is one affine street score per pixel,
 * `t = (w . rgb - w . land) / (w . street - w . land)`, mapping the base's land to the style's `ground`, its streets to
 * the style's `street` (main roads a little beyond, the matrix clamps) and everything else along that line. The labels
 * filter is hue-free: CARTO's grey text and white halo become black on white (light grounds) or white on black (dark
 * grounds), with the alpha raised, so names keep >= 4.5:1 against their halo on every style. Pure maths in
 * [streetArray] / [labelArray] for unit tests.
 */
object WgoTileFilter {
    /** OSM land, kept for the P6-B tests and audits. */
    val OSM_LAND = TileBase.OSM.land
    val OSM_STREET = TileBase.OSM.street

    /** The OSM placeholder colour (P6-B name). */
    val LOADING_TILE_ARGB: Int = TileBase.OSM.loadingArgb

    /** Ground luminance under which the labels are drawn light on a dark halo. */
    const val DARK_GROUND_LUMINANCE = 0.18f

    /** Grey level of CARTO's label text (about #5c666f) and halo (#f8f8f8) are mapped around this threshold. */
    private const val LABEL_GREY_MID = 120f
    private const val LABEL_GREY_SPAN = 120f
    const val LABEL_ALPHA_GAIN = 1.35f

    fun filterFor(style: WgoMapStyle, @Suppress("UNUSED_PARAMETER") dark: Boolean, base: TileBase = TileBase.OSM): ColorMatrixColorFilter =
        // COBALTO's palette is already the active theme's (map-ground / map-street); the others are theme-independent.
        ColorMatrixColorFilter(ColorMatrix(streetArray(style.palette.ground, style.palette.street, base)))

    /** True when [style]'s labels are drawn white on a black halo. */
    fun labelsInverted(style: WgoMapStyle): Boolean = style.palette.ground.luminance() < DARK_GROUND_LUMINANCE

    fun labelFilterFor(style: WgoMapStyle): ColorMatrixColorFilter =
        ColorMatrixColorFilter(ColorMatrix(labelArray(labelsInverted(style))))

    private fun dot(w: FloatArray, rgb: FloatArray) = w[0] * rgb[0] + w[1] * rgb[1] + w[2] * rgb[2]

    /** Row-major 4x5 colour matrix: `out = ground + (street - ground) * t`, on 0..255 channels. */
    fun streetArray(ground: Color, street: Color, base: TileBase = TileBase.OSM): FloatArray {
        val w = base.weights
        val land = dot(w, base.land)
        val span = dot(w, base.street) - land
        fun row(g: Float, s: Float): FloatArray {
            val k = (s - g) * 255f / span
            return floatArrayOf(k * w[0], k * w[1], k * w[2], 0f, g * 255f - k * land)
        }
        val r = row(ground.red, street.red)
        val g = row(ground.green, street.green)
        val b = row(ground.blue, street.blue)
        return floatArrayOf(
            r[0], r[1], r[2], r[3], r[4],
            g[0], g[1], g[2], g[3], g[4],
            b[0], b[1], b[2], b[3], b[4],
            0f, 0f, 0f, 1f, 0f
        )
    }

    /**
     * Labels: grey `0.3R + 0.59G + 0.11B` stretched so text (about 100) goes to 0 and the halo (248) to 255 (inverted
     * on dark grounds); alpha x[LABEL_ALPHA_GAIN]. No hue.
     */
    fun labelArray(invert: Boolean): FloatArray {
        val k = 255f / LABEL_GREY_SPAN * if (invert) -1f else 1f
        val offset = if (invert) 255f + LABEL_GREY_MID * 255f / LABEL_GREY_SPAN else -LABEL_GREY_MID * 255f / LABEL_GREY_SPAN
        val row = floatArrayOf(0.3f * k, 0.59f * k, 0.11f * k, 0f, offset)
        return row + row + row + floatArrayOf(0f, 0f, 0f, LABEL_ALPHA_GAIN, 0f)
    }

    /** Applies [m] to an sRGB pixel (0..255) the way Android does (affine, then clamped). For tests and audits. */
    fun apply(m: FloatArray, rgb: FloatArray): FloatArray = FloatArray(3) { c ->
        (m[c * 5] * rgb[0] + m[c * 5 + 1] * rgb[1] + m[c * 5 + 2] * rgb[2] + m[c * 5 + 4]).coerceIn(0f, 255f)
    }

    /** Applies [m] to the alpha (0..255) of a pixel. */
    fun applyAlpha(m: FloatArray, rgb: FloatArray, alpha: Float): Float =
        (m[15] * rgb[0] + m[16] * rgb[1] + m[17] * rgb[2] + m[18] * alpha + m[19]).coerceIn(0f, 255f)
}

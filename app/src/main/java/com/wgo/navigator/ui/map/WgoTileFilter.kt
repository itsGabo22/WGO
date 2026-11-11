package com.wgo.navigator.ui.map

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import androidx.compose.ui.graphics.Color
import com.wgo.navigator.ui.theme.WgoMapStyle

/**
 * The colour filter over the raster tiles (spec section 9.1, legibility, P6-B). One affine "street score" per pixel,
 * `t = (w . rgb - w . land) / (w . white - w . land)`, maps:
 * - OSM land (#F2EFE9, t = 0) to the style's `ground`,
 * - OSM street fills (white minor streets, t = 1; yellow secondary and orange primary roads, t > 1) to the style's
 *   `street` colour or beyond it (the matrix clamps, so the main avenues come out a little stronger),
 * - buildings, parks, water, casings and labels (t < 0) further away from `street`, past `ground`.
 *
 * The weights favour red and green over blue because OSM's road fills are white, yellow and orange while land,
 * buildings, parks and water are greyer or bluer: measured on the real Pasto tiles cached on the watch, a plain
 * luminance curve left the main avenues at 1.1-1.8:1 against land. Side effect: school / retail areas (#FFFFE5,
 * #FFD6D1) also take the street colour, as filled blocks. Pure maths in [streetArray] for unit tests.
 */
object WgoTileFilter {
    /** Street-score weights on R, G, B. */
    private val W = floatArrayOf(1.4f, 0.3f, -0.15f)

    /** OSM Carto land and minor street fill, the two anchors of the score (0..255). */
    val OSM_LAND = floatArrayOf(242f, 239f, 233f)
    val OSM_STREET = floatArrayOf(255f, 255f, 255f)

    /**
     * ARGB of osmdroid's loading placeholder (background and grid lines): the OSM land colour, so the filter turns a
     * tile that has not arrived yet into the style's `ground` instead of a plain white / grey page.
     */
    const val LOADING_TILE_ARGB: Int = (0xFF shl 24) or (242 shl 16) or (239 shl 8) or 233

    fun filterFor(style: WgoMapStyle, @Suppress("UNUSED_PARAMETER") dark: Boolean): ColorMatrixColorFilter =
        // COBALTO's palette is already the active theme's (map-ground / map-street); the others are theme-independent.
        ColorMatrixColorFilter(ColorMatrix(streetArray(style.palette.ground, style.palette.street)))

    private fun dot(rgb: FloatArray) = W[0] * rgb[0] + W[1] * rgb[1] + W[2] * rgb[2]

    /** Row-major 4x5 colour matrix: `out = ground + (street - ground) * t`, on 0..255 channels. */
    fun streetArray(ground: Color, street: Color): FloatArray {
        val land = dot(OSM_LAND)
        val span = dot(OSM_STREET) - land
        fun row(g: Float, s: Float): FloatArray {
            val k = (s - g) * 255f / span
            return floatArrayOf(k * W[0], k * W[1], k * W[2], 0f, g * 255f - k * land)
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

    /** Applies [m] to an sRGB pixel (0..255) the way Android does (affine, then clamped). For tests and audits. */
    fun apply(m: FloatArray, rgb: FloatArray): FloatArray = FloatArray(3) { c ->
        (m[c * 5] * rgb[0] + m[c * 5 + 1] * rgb[1] + m[c * 5 + 2] * rgb[2] + m[c * 5 + 4]).coerceIn(0f, 255f)
    }
}

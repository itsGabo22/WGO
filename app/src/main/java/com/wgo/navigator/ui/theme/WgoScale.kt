package com.wgo.navigator.ui.theme

import androidx.compose.runtime.compositionLocalOf

/** The whole design is written in dp on this reference canvas (Wear OS Large Round, 227 x 227 dp). */
const val WGO_REFERENCE_DP = 227f

private const val MIN_SCALE = 0.8f
private const val MAX_SCALE = 1.25f
private const val MIN_TEXT_SCALE = 0.9f
private const val MAX_TEXT_SCALE = 1.1f
private const val MIN_TOUCH_REAL_DP = 48f

/** Uniform scale `s` for a screen whose smaller side is [screenMinDp]: `screenMinDp / 227`, clamped to 0.8..1.25. */
fun wgoScaleFor(screenMinDp: Float): Float =
    (screenMinDp / WGO_REFERENCE_DP).coerceIn(MIN_SCALE, MAX_SCALE)

/** Text follows the scale but only within 0.9..1.1 (it is combined with the user's system font scale). */
fun wgoTextScaleFor(s: Float): Float = s.coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE)

/** Size in reference dp that equals 48 REAL dp at scale [s] (e.g. 56.7 at s = 0.846). */
fun wgoMinTouchRefDp(s: Float): Float = MIN_TOUCH_REAL_DP / s

/** Dial radius on the reference canvas (227 / 2). */
const val WGO_DIAL_RADIUS = WGO_REFERENCE_DP / 2f

/** Pill fit-rule limit for generic round watches (spec section 4). */
const val WGO_FIT_LIMIT = 104f

/** Stricter limit for the Galaxy Watch5 Pro (usable radius R - 9.5); new placements must meet this one. */
const val WGO_FIT_LIMIT_GW5P = 103f

/**
 * Spec section 4 pill fit value for a box of [w] x [h] reference dp whose top sits at [top] dp:
 * `sqrt((w/2 - h/2)^2 + y^2) + h/2`, with `y` the offset of the box centre from the dial centre. It is
 * scale-invariant, so it can be evaluated in reference dp for every screen size. When `h > w` the box is treated as
 * a circle of diameter `h`.
 */
fun wgoFit(w: Float, h: Float, top: Float): Float {
    val y = top + h / 2f - WGO_DIAL_RADIUS
    val dx = maxOf(0f, w / 2f - h / 2f)
    return kotlin.math.sqrt(dx * dx + y * y) + h / 2f
}

/** True when the box obeys the Galaxy Watch5 Pro limit. */
fun wgoFits(w: Float, h: Float, top: Float): Boolean = wgoFit(w, h, top) <= WGO_FIT_LIMIT_GW5P

/** The active scale `s`; 1 outside [WgoResponsive]. Geometry is still written in reference dp. */
val LocalWgoScale = compositionLocalOf { 1f }

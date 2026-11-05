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

/** The active scale `s`; 1 outside [WgoResponsive]. Geometry is still written in reference dp. */
val LocalWgoScale = compositionLocalOf { 1f }

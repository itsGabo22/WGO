package com.wgo.navigator.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/** Easings and durations from docs/design/WGO_DESIGN_SYSTEM.md, section 5. */
object WgoMotion {
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecel = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccel = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Button release: tween(120, Standard) for [SNAP], spring for [pop]. */
    fun <T> pop(): SpringSpec<T> =
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow)

    /** Toggle knob / control states: spring(0.8, StiffnessMedium). */
    fun <T> base(): SpringSpec<T> =
        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)

    const val INSTANT = 0       // pressed color inversion, no animation
    const val SNAP = 120        // icon/badge/text swaps
    const val BASE = 240        // toggle knob, control states
    const val ENTER = 420       // screen element entrance
    const val EXIT = 200        // screen exit
    const val STAGGER = 40      // delay between sibling entrances (delayMillis = i * STAGGER)
}

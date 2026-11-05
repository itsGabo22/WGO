package com.wgo.navigator.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.TransformOrigin
import com.wgo.navigator.ScreenState
import com.wgo.navigator.TravelMode
import com.wgo.navigator.ui.theme.WgoMotion

/** Vertical center of the mode pads on the 227 dp canvas: (102 + 70) / 227. */
private const val PAD_CENTER_Y = 0.76f

/**
 * Transitions between screens (docs/design section 7). Settings keeps its plain swap.
 * - Listening -> ModeSelection: the old content fades out in 120 ms; the new screen runs its own rise-in.
 * - ModeSelection -> Navigation: zoom 0.85 -> 1 plus fade in 420 ms, pivoting on the center of the pressed half.
 * - Back / cancel to Listening: fade + scale 1 -> 0.96 in 200 ms (EmphasizedAccel).
 * With reduce motion every change is a 120 ms fade.
 */
fun wgoScreenTransition(
    from: ScreenState,
    to: ScreenState,
    mode: TravelMode?,
    reduceMotion: Boolean
): ContentTransform {
    val involvesSettings = from == ScreenState.Settings || to == ScreenState.Settings
    val involvesPicker = from == ScreenState.MapPicker || to == ScreenState.MapPicker
    if (from == to || (involvesSettings && !involvesPicker)) {
        return EnterTransition.None togetherWith ExitTransition.None
    }
    if (reduceMotion) {
        return fadeIn(tween(WgoMotion.SNAP)) togetherWith fadeOut(tween(WgoMotion.SNAP))
    }
    return when {
        to == ScreenState.Listening || (from == ScreenState.MapPicker && to == ScreenState.Settings) -> {
            val accel = tween<Float>(WgoMotion.EXIT, easing = WgoMotion.EmphasizedAccel)
            EnterTransition.None togetherWith (fadeOut(accel) + scaleOut(accel, targetScale = 0.96f))
        }
        from == ScreenState.ModeSelection && to == ScreenState.Navigation -> {
            val decel = tween<Float>(WgoMotion.ENTER, easing = WgoMotion.EmphasizedDecel)
            val origin = TransformOrigin(if (mode == TravelMode.Walk) 0.25f else 0.75f, PAD_CENTER_Y)
            // The pressed pad stays visible (and inverted) under the zoom until the transition ends.
            (fadeIn(decel) + scaleIn(decel, initialScale = 0.85f, transformOrigin = origin)) togetherWith
                // (a near-opaque fade keeps the exiting screen alive for the whole 420 ms without visibly dimming it)
                fadeOut(tween(WgoMotion.ENTER), targetAlpha = 0.99f)
        }
        else -> EnterTransition.None togetherWith fadeOut(tween(WgoMotion.SNAP))
    }
}

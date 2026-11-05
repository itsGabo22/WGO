package com.wgo.navigator.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Screen entrance: the element rises [rise] and fades in over 420 ms with EmphasizedDecel, after [delayMs]
 * (use `index * WgoMotion.STAGGER`-style delays for siblings). Runs once when the element enters composition and
 * is applied in the draw phase only. With reduce motion it is a 120 ms fade without movement.
 */
@Composable
fun Modifier.wgoRiseIn(delayMs: Int = 0, rise: Dp = 14.dp): Modifier {
    val reduceMotion = LocalWgoReduceMotion.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            1f,
            if (reduceMotion) tween(WgoMotion.SNAP, easing = WgoMotion.Standard)
            else tween(WgoMotion.ENTER, delayMillis = delayMs, easing = WgoMotion.EmphasizedDecel)
        )
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = if (reduceMotion) 0f else (1f - p) * rise.toPx()
    }
}

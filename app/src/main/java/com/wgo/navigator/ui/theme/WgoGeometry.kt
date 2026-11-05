package com.wgo.navigator.ui.theme

import androidx.compose.foundation.layout.sizeIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Enforces a minimum hit area in REAL dp (default 48 x 48) without touching the visual: in the scaled density it
 * becomes `48 / s` reference dp, so at s = 0.846 the box is 56.7 dp. Put it on the outer clickable box that wraps the
 * visual; the box only grows when the content is smaller than the minimum.
 */
@Composable
fun Modifier.wgoMinTouch(width: Dp = 48.dp, height: Dp = 48.dp): Modifier {
    val s = LocalWgoScale.current
    return sizeIn(minWidth = width / s, minHeight = height / s)
}

/** Hit-area side in reference dp for a visual of [visual] dp: the larger of the visual and `48 / s`. */
@Composable
fun wgoTouchSide(visual: Dp): Dp = maxOf(visual, wgoMinTouchRefDp(LocalWgoScale.current).dp)

/** Hairlines never go under [min] REAL dp (default 1): at s = 0.8 a 1 dp stroke is lifted to 1.25 reference dp. */
@Composable
fun Dp.atLeastRealDp(min: Dp = 1.dp): Dp = maxOf(this, min / LocalWgoScale.current)

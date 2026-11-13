package com.wgo.navigator.ui.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.wgo.navigator.ui.theme.LocalWgoReduceMotion
import com.wgo.navigator.ui.theme.MapStyleId
import com.wgo.navigator.ui.theme.Wgo
import com.wgo.navigator.ui.theme.WgoClock
import com.wgo.navigator.ui.theme.WgoMotion
import com.wgo.navigator.ui.theme.accent
import com.wgo.navigator.ui.theme.wgoMapStyle
import kotlinx.coroutines.launch

private const val IRIS_MS = 500
private const val FLASH_MS = 550

/**
 * Live preview disc of the explored style (the caller sizes it, 104 reference dp in the picker), with a 3 dp `ink` ring.
 * When [styleId] changes the new scene opens over the old one through a circular IRIS from the centre (500 ms,
 * EmphasizedDecel) and a FLASH ring in the new accent scales 1 -> 1.5 while it fades from .95 to 0 (550 ms).
 * Both animations are read only in draw lambdas. With reduce motion the scene is simply swapped.
 */
@Composable
fun MapPickerDisc(styleId: MapStyleId, clock: WgoClock, modifier: Modifier = Modifier) {
    val theme = Wgo.palette
    val reduceMotion = LocalWgoReduceMotion.current
    var shown by remember { mutableStateOf(styleId) }
    var previous by remember { mutableStateOf<MapStyleId?>(null) }
    val iris = remember { Animatable(1f) }
    val flash = remember { Animatable(1f) }

    LaunchedEffect(styleId) {
        if (styleId == shown) return@LaunchedEffect
        if (reduceMotion) {
            shown = styleId
            previous = null
            return@LaunchedEffect
        }
        previous = shown
        shown = styleId
        iris.snapTo(0f)
        flash.snapTo(0f)
        launch { iris.animateTo(1f, tween(IRIS_MS, easing = WgoMotion.EmphasizedDecel)) }
        flash.animateTo(1f, tween(FLASH_MS, easing = WgoMotion.Standard))
        previous = null
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
        ) {
            previous?.let { old ->
                RadarSchematic(
                    style = wgoMapStyle(old, theme),
                    live = false,
                    modifier = Modifier.fillMaxSize()
                )
            }
            // Circular iris: a circle-outline layer that grows from 0 to the full disc while its content is counter-scaled,
            // so the scene stays fixed and is revealed from the centre. A layer property, not a per-frame clipPath.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = maxOf(MapPickerMath.irisScale(iris.value), 0.001f)
                        scaleX = p
                        scaleY = p
                        shape = CircleShape
                        clip = true
                    }
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val inverse = 1f / maxOf(MapPickerMath.irisScale(iris.value), 0.001f)
                            scaleX = inverse
                            scaleY = inverse
                        }
                ) {
                    RadarSchematic(
                        style = wgoMapStyle(shown, theme),
                        live = true,
                        clock = clock,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        Box(Modifier.fillMaxSize().border(3.dp, theme.ink, CircleShape))

        // Flash ring in the new accent: 3 dp border scaling 1 -> 1.5 and fading .95 -> 0 (drawn outside the disc).
        val accent = shown.accent
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
            val f = flash.value
            if (f < 1f) {
                drawCircle(
                    color = accent.copy(alpha = 0.95f * (1f - f)),
                    radius = size.minDimension / 2f * (1f + 0.5f * f),
                    center = size.center,
                    style = Stroke(3.dp.toPx())
                )
            }
        }
    }
}

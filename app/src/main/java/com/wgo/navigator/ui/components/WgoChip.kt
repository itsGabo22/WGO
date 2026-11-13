package com.wgo.navigator.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Text
import com.wgo.navigator.ui.theme.LocalWgoReduceMotion
import com.wgo.navigator.ui.theme.Wgo
import com.wgo.navigator.ui.theme.WgoMotion
import com.wgo.navigator.ui.theme.WgoType
import com.wgo.navigator.ui.theme.wgoMinTouch

/**
 * Small secondary pill (default 104 x 32 dp visual, 48 dp touch height): `surface` + `edge-control`,
 * 12 sp bold uppercase label. Pressing inverts to `ink`/`inkInverse` instantly; release uses [WgoMotion.pop].
 */
@Composable
fun WgoChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = 104.dp,
    /** Touch height in REAL dp (48 = `hit-min`; 60 = `hit-drive` for critical actions while driving). */
    touchHeight: androidx.compose.ui.unit.Dp = 48.dp
) {
    val palette = Wgo.palette
    val reduceMotion = LocalWgoReduceMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = if (pressed || reduceMotion) snap() else WgoMotion.pop(),
        label = "chipScale"
    )

    Box(
        modifier = modifier
            .width(width)
            .wgoMinTouch(height = touchHeight)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width, 32.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(if (pressed) palette.ink else palette.surface)
                .border(1.5.dp, palette.ink, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label.uppercase(),
                style = WgoType.labelMd,
                color = if (pressed) palette.inkInverse else palette.ink,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ChipPreview(dark: Boolean) {
    WgoPreviewFrame(dark) { WgoChip(label = "Cancelar", onClick = {}) }
}

@WgoPreview
@Composable
private fun WgoChipDaylight() = ChipPreview(false)

@WgoPreview
@Composable
private fun WgoChipNight() = ChipPreview(true)

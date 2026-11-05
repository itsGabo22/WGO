package com.wgo.navigator.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.LocalContentColor
import androidx.wear.compose.material.ProvideTextStyle
import androidx.wear.compose.material.Text
import com.wgo.navigator.ui.theme.LocalWgoReduceMotion
import com.wgo.navigator.ui.theme.Wgo
import com.wgo.navigator.ui.theme.WgoMotion
import com.wgo.navigator.ui.theme.WgoType
import com.wgo.navigator.ui.theme.wgoMinTouch

enum class MassiveButtonVariant {
    Primary, Inverted, Secondary, Alert, Go
}

private val HitMassive = 52.dp
private const val PRESSED_SCALE = 0.96f
private const val DISABLED_ALPHA = 0.38f

/**
 * Pill button, 52 dp high. Pressing swaps colors INSTANTLY (ink/inkInverse; inverted -> primary/onPrimary),
 * scales to 0.96 with no animation on the way in and releases with [WgoMotion.pop].
 * The haptic belongs to the ViewModel behind [onClick].
 */
@Composable
fun MassiveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: MassiveButtonVariant = MassiveButtonVariant.Primary,
    enabled: Boolean = true,
    eta: String? = null,
    content: @Composable () -> Unit
) {
    val palette = Wgo.palette
    val reduceMotion = LocalWgoReduceMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val restBackground: Color
    val restContent: Color
    when (variant) {
        MassiveButtonVariant.Primary -> { restBackground = palette.primary; restContent = palette.onPrimary }
        MassiveButtonVariant.Inverted -> { restBackground = palette.ink; restContent = palette.inkInverse }
        MassiveButtonVariant.Secondary -> { restBackground = palette.surface; restContent = palette.ink }
        MassiveButtonVariant.Alert -> { restBackground = palette.alert; restContent = palette.onAlert }
        MassiveButtonVariant.Go -> { restBackground = palette.go; restContent = palette.onGo }
    }
    val inverted = variant == MassiveButtonVariant.Inverted
    val showPressed = pressed && enabled
    val background = when {
        !showPressed -> restBackground
        inverted -> palette.primary
        else -> palette.ink
    }
    val contentColor = when {
        !showPressed -> restContent
        inverted -> palette.onPrimary
        else -> palette.inkInverse
    }

    val scale by animateFloatAsState(
        targetValue = if (showPressed) PRESSED_SCALE else 1f,
        animationSpec = if (showPressed || reduceMotion) snap() else WgoMotion.pop(),
        label = "massiveButtonScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .wgoMinTouch(height = 48.dp)
            .height(HitMassive)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else DISABLED_ALPHA
            }
            .clip(CircleShape)
            .background(background)
            .then(
                if (variant == MassiveButtonVariant.Secondary) {
                    Modifier.border(1.5.dp, palette.ink, CircleShape)
                } else Modifier
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            ProvideTextStyle(WgoType.button) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    content()
                    if (eta != null) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(contentColor.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = eta,
                                style = WgoType.labelMd.copy(fontSize = 11.sp),
                                color = contentColor
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Convenience overload: uppercase [label] with an optional 24 dp [icon]. */
@Composable
fun MassiveButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    variant: MassiveButtonVariant = MassiveButtonVariant.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    eta: String? = null
) {
    MassiveButton(onClick, modifier, variant, enabled, eta) {
        if (icon != null) Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Text(text = label.uppercase(), style = WgoType.button)
    }
}

@Composable
private fun ButtonsPreview(dark: Boolean, vararg variants: Pair<MassiveButtonVariant, Boolean>) {
    WgoPreviewFrame(dark) {
        Column(
            modifier = Modifier.padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
        ) {
            variants.forEach { (v, disabled) ->
                MassiveButton(
                    onClick = {},
                    label = if (disabled) "Disabled" else v.name,
                    variant = v,
                    icon = WgoIcons.DirectionsCar,
                    enabled = !disabled,
                    eta = if (v == MassiveButtonVariant.Go) "8 min" else null
                )
            }
        }
    }
}

private val setA = arrayOf(
    MassiveButtonVariant.Primary to false,
    MassiveButtonVariant.Inverted to false,
    MassiveButtonVariant.Secondary to false
)
private val setB = arrayOf(
    MassiveButtonVariant.Alert to false,
    MassiveButtonVariant.Go to false,
    MassiveButtonVariant.Primary to true
)

@WgoPreview
@Composable
private fun MassiveButtonDaylightA() = ButtonsPreview(false, *setA)

@WgoPreview
@Composable
private fun MassiveButtonNightA() = ButtonsPreview(true, *setA)

@WgoPreview
@Composable
private fun MassiveButtonDaylightB() = ButtonsPreview(false, *setB)

@WgoPreview
@Composable
private fun MassiveButtonNightB() = ButtonsPreview(true, *setB)

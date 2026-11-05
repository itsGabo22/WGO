package com.wgo.navigator.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.wgo.navigator.TravelMode
import com.wgo.navigator.ui.StringKey
import com.wgo.navigator.ui.components.StatusBadge
import com.wgo.navigator.ui.components.StatusBadgeVariant
import com.wgo.navigator.ui.components.WgoIcons
import com.wgo.navigator.ui.string
import com.wgo.navigator.ui.theme.LocalWgoReduceMotion
import com.wgo.navigator.ui.theme.Wgo
import com.wgo.navigator.ui.theme.WgoClock
import com.wgo.navigator.ui.theme.WgoMotion
import com.wgo.navigator.ui.theme.WgoType
import com.wgo.navigator.ui.theme.rememberWgoClock
import com.wgo.navigator.ui.theme.wgoRiseIn
import kotlinx.coroutines.launch
import kotlin.math.hypot

private const val REFERENCE_SIZE = 227f

/** Duration of the aura ripple that fills the pressed half (the spec leaves it open). */
private const val RIPPLE_MS = 400

/**
 * Screen 02, split dial, no scroll. Positions follow the spec (`top` in dp on a 227 dp canvas) as fractions of the
 * real screen. [pressedMode] forces a pad into its pressed look (used while the zoom transition runs and in previews).
 */
@Composable
fun ScreenModeSelection(
    onSelectMode: (TravelMode) -> Unit,
    modifier: Modifier = Modifier,
    destination: String = string(StringKey.DESTINATION_NAME),
    address: String = string(StringKey.DESTINATION_ADDRESS),
    walkMinutes: Int = 15,
    driveMinutes: Int = 8,
    pressedMode: TravelMode? = null,
    clock: WgoClock = rememberWgoClock()
) {
    val palette = Wgo.palette

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(palette.canvas)
    ) {
        val k = maxWidth.value / REFERENCE_SIZE
        fun dial(value: Float): Dp = (value * k).dp

        StatusBadge(
            text = string(StringKey.VOICE_ON),
            variant = StatusBadgeVariant.Soft,
            equalizer = true,
            clock = clock,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = dial(16f)).wgoRiseIn()
        )
        Text(
            text = destination,
            color = palette.ink,
            style = WgoType.headlineSm,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = dial(38f))
                .width(dial(150f))
                .wgoRiseIn()
        )
        Text(
            text = address,
            color = palette.inkMuted,
            style = WgoType.bodyMd,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = dial(80f))
                .width(dial(170f))
                .wgoRiseIn()
        )

        // Pads: left 0-111, 5 dp seam, right 116-227, start at top 102, 140 high (the circle clips the rest).
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(y = dial(102f))
                .fillMaxWidth()
                .height(dial(140f))
        ) {
            ModePad(
                mode = TravelMode.Walk,
                label = string(StringKey.BTN_WALK),
                description = string(StringKey.MODE_WALK_DESC),
                minutes = walkMinutes,
                icon = WgoIcons.DirectionsWalk,
                restBackground = palette.ink,
                restContent = palette.inkInverse,
                pressedBackground = palette.primary,
                pressedContent = palette.onPrimary,
                shape = RoundedCornerShape(topEnd = 16.dp),
                startPadding = 12.dp,
                endPadding = 0.dp,
                forcePressed = pressedMode == TravelMode.Walk,
                onClick = { onSelectMode(TravelMode.Walk) },
                modifier = Modifier.weight(111f).fillMaxHeight().wgoRiseIn(delayMs = 2 * WgoMotion.STAGGER)
            )
            Spacer(Modifier.weight(5f))
            ModePad(
                mode = TravelMode.Drive,
                label = string(StringKey.BTN_DRIVE),
                description = string(StringKey.MODE_DRIVE_DESC),
                minutes = driveMinutes,
                icon = WgoIcons.DirectionsCar,
                restBackground = palette.primary,
                restContent = palette.onPrimary,
                pressedBackground = palette.ink,
                pressedContent = palette.inkInverse,
                shape = RoundedCornerShape(topStart = 16.dp),
                startPadding = 0.dp,
                endPadding = 12.dp,
                forcePressed = pressedMode == TravelMode.Drive,
                onClick = { onSelectMode(TravelMode.Drive) },
                modifier = Modifier.weight(111f).fillMaxHeight().wgoRiseIn(delayMs = 4 * WgoMotion.STAGGER)
            )
        }
    }
}

@Composable
private fun ModePad(
    mode: TravelMode,
    label: String,
    description: String,
    minutes: Int,
    icon: ImageVector,
    restBackground: Color,
    restContent: Color,
    pressedBackground: Color,
    pressedContent: Color,
    shape: Shape,
    startPadding: Dp,
    endPadding: Dp,
    forcePressed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = Wgo.palette
    val reduceMotion = LocalWgoReduceMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val touchPressed by interactionSource.collectIsPressedAsState()
    val pressed = touchPressed || forcePressed

    val background = if (pressed) pressedBackground else restBackground
    val content = if (pressed) pressedContent else restContent

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = if (pressed || reduceMotion) snap() else WgoMotion.pop(),
        label = "padScale"
    )

    // Aura ripple: a circle that grows from the touch point to the farthest corner, drawn in the draw phase.
    var touch by remember { mutableStateOf(Offset.Unspecified) }
    val ripple = remember { Animatable(0f) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    touch = interaction.pressPosition
                    ripple.snapTo(0f)
                    launch {
                        ripple.animateTo(
                            1f,
                            if (reduceMotion) tween(WgoMotion.SNAP) else tween(RIPPLE_MS, easing = WgoMotion.EmphasizedDecel)
                        )
                    }
                }
                is PressInteraction.Cancel -> ripple.snapTo(0f)
                else -> Unit // on release the click follows; the ripple finishes while the screen zooms
            }
        }
    }

    val minutesText = "$minutes ${string(StringKey.ETA_MIN_UNIT)}"
    val spoken = "$description, $minutes ${string(StringKey.MINUTES_WORD)}"

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(background)
            .drawBehind {
                val origin = touch
                if (origin != Offset.Unspecified && ripple.value > 0f) {
                    val far = maxOf(
                        hypot(origin.x, origin.y),
                        hypot(size.width - origin.x, origin.y),
                        hypot(origin.x, size.height - origin.y),
                        hypot(size.width - origin.x, size.height - origin.y)
                    )
                    drawCircle(
                        color = palette.aura.copy(alpha = 0.35f),
                        radius = far * ripple.value,
                        center = origin
                    )
                }
            }
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier.padding(start = startPadding, end = endPadding, top = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(content.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = label.uppercase(),
                color = content,
                style = WgoType.button.copy(fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.04.em),
                maxLines = 1
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(content.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = minutesText,
                    color = content,
                    style = WgoType.labelMd.copy(fontSize = 11.sp)
                )
            }
        }
    }
}

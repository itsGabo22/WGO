package com.wgo.navigator.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.wgo.navigator.ui.StringKey
import com.wgo.navigator.ui.components.BezelGauge
import com.wgo.navigator.ui.components.BezelStyle
import com.wgo.navigator.ui.components.MassiveButton
import com.wgo.navigator.ui.components.MassiveButtonVariant
import com.wgo.navigator.ui.components.StatusBadge
import com.wgo.navigator.ui.components.StatusBadgeVariant
import com.wgo.navigator.ui.components.WgoChip
import com.wgo.navigator.ui.components.WgoIcons
import com.wgo.navigator.ui.components.WgoMarqueeText
import com.wgo.navigator.ui.string
import com.wgo.navigator.ui.theme.ClockRate
import com.wgo.navigator.ui.theme.LocalWgoReduceMotion
import com.wgo.navigator.ui.theme.Wgo
import com.wgo.navigator.ui.theme.WgoClock
import com.wgo.navigator.ui.theme.WgoMotion
import com.wgo.navigator.ui.theme.WgoType
import com.wgo.navigator.ui.theme.rememberWgoClock
import com.wgo.navigator.ui.theme.wgoMinTouch
import com.wgo.navigator.ui.theme.wgoRiseIn
import com.wgo.navigator.ui.theme.wgoTouchSide

/*
 * Live-navigation surfaces (P5-D). Geometry in reference dp on the 227 dp canvas (WgoResponsive scales it); every box
 * lists its fit value against the Galaxy Watch5 Pro limit of 103 (`wgoFit(w, h, top)`), checked by
 * NavigationSurfacesFitTest. The celebratory arrival and the rebuilt HUD are P6.
 */

/** Places the visual centre of a box whose touch side is [touch] on the spec line [centreY]. */
private fun topAt(centreY: Float, touch: Dp): Dp = centreY.dp - touch / 2

/**
 * GPS SEARCHING: no usable fix yet. Bezel GPS-search arc (ambience rate); badge 80x18 @16 (102.8); label `label-md`
 * 130x14 @38 (96.8, live region); hint `body-md` 150x18 @54 (92.1); location disc 64 @92 (42.5); CANCEL chip 104x32
 * @176 (102.4). Same grid as the Working surface.
 */
@Composable
fun ScreenGpsSearching(
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    clock: WgoClock = rememberWgoClock()
) {
    val palette = Wgo.palette
    Box(modifier.fillMaxSize().background(palette.canvas)) {
        BezelGauge(style = BezelStyle.GpsSearch, clock = clock)
        StatusBadge(
            text = "WGO • NAV",
            variant = StatusBadgeVariant.Neutral,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = 16.dp)
        )
        Text(
            text = string(StringKey.NAV_GPS_SEARCHING),
            color = palette.primaryInk,
            style = WgoType.labelMd,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 38.dp)
                .width(130.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
        )
        Text(
            text = string(StringKey.NAV_GPS_SEARCH_HINT),
            color = palette.inkMuted,
            style = WgoType.bodyMd,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = 54.dp).width(150.dp)
        )
        Box(
            Modifier.align(Alignment.TopCenter).offset(y = 92.dp).size(64.dp).clip(CircleShape).background(palette.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(WgoIcons.LocationPin, contentDescription = null, tint = palette.primaryInk, modifier = Modifier.size(32.dp))
        }
        val chipTouch = wgoTouchSide(32.dp)
        WgoChip(
            label = string(StringKey.BTN_CANCEL),
            onClick = onCancel,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = topAt(192f, chipTouch))
        )
    }
}

/**
 * GPS LOST overlay (spec section 7, ScreenGpsLost): full `alert` background, text `onAlert`. Ring r 108 (track 28%
 * `onAlert`, fill 100%) breathing at 1.6 s; badge "EN PAUSA" (onAlert/alert) at most 80x18 @18 (100.9; keep this label
 * short: a 96 dp badge would be 103.9); satellite-off icon 38 in a 40 box at (93.5, 64) (49.5); three 48 dp search rings at (113.5, 84), scale 0.6 -> 2.4, alpha 0.9 -> 0,
 * 2400 ms staggered 800 ms (drawn, not hit-tested); title `headline-md` 170x24 @112 (85.8, live region); hint
 * `body-md` 170x18 @138 with three fading dots (92.1); RETRY pill 120x40 @166 (102.8). Every loop runs on the screen
 * clock at the ambience rate; reduce motion shows static rings and dots. The engine keeps running underneath.
 */
@Composable
fun GpsLostOverlay(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    clock: WgoClock = rememberWgoClock()
) {
    val palette = Wgo.palette
    val reduceMotion = LocalWgoReduceMotion.current
    clock.Use(ClockRate.Ambience, active = !reduceMotion)
    val onAlert = palette.onAlert
    Box(modifier.fillMaxSize().background(palette.alert)) {
        BezelGauge(
            progress = { 1f },
            color = onAlert,
            track = onAlert.copy(alpha = 0.28f),
            modifier = Modifier.graphicsLayer { alpha = if (reduceMotion) 1f else 0.6f + 0.4f * clock.pingPong(1_600) }
        )
        Canvas(Modifier.fillMaxSize().clearAndSetSemantics { }) {
            val centre = Offset(size.width / 2f, 84.dp.toPx())
            val base = 24.dp.toPx()
            val stroke = Stroke(3.dp.toPx())
            if (reduceMotion) {
                drawCircle(onAlert.copy(alpha = 0.5f), radius = base, center = centre, style = stroke)
            } else for (k in 0 until 3) {
                val p = ((clock.millis + k * 800L) % 2_400L) / 2_400f
                drawCircle(onAlert.copy(alpha = 0.9f * (1f - p)), radius = base * (0.6f + 1.8f * p), center = centre, style = stroke)
            }
        }
        StatusBadge(
            text = string(StringKey.NAV_PAUSED),
            variant = StatusBadgeVariant.OnAlert,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = 18.dp)
        )
        Box(Modifier.offset(x = 93.5.dp, y = 64.dp).size(40.dp), contentAlignment = Alignment.Center) {
            Icon(WgoIcons.SatelliteOff, contentDescription = null, tint = onAlert, modifier = Modifier.size(38.dp))
        }
        Text(
            text = string(StringKey.NAV_GPS_LOST),
            color = onAlert,
            style = WgoType.headlineMd,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 112.dp)
                .width(170.dp)
                .semantics { liveRegion = LiveRegionMode.Assertive }
        )
        Row(
            modifier = Modifier.align(Alignment.TopCenter).offset(y = 138.dp).width(170.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = string(StringKey.NAV_GPS_LOST_HINT), color = onAlert, style = WgoType.bodyMd, maxLines = 1)
            Spacer(Modifier.width(3.dp))
            Canvas(Modifier.size(width = 16.dp, height = 18.dp).clearAndSetSemantics { }) {
                val r = 1.6.dp.toPx()
                for (k in 0 until 3) {
                    // 1.2 s per cycle: far below the 3 Hz flash limit.
                    val a = if (reduceMotion) 1f else {
                        val p = (clock.phase(1_200) - k / 3f + 1f) % 1f
                        0.25f + 0.75f * (1f - p)
                    }
                    drawCircle(onAlert.copy(alpha = a), r, Offset(r + k * 5.dp.toPx(), size.height * 0.72f))
                }
            }
        }
        val pillTouch = wgoTouchSide(40.dp)
        PillButton(
            label = string(StringKey.PERM_RETRY),
            onClick = onRetry,
            background = onAlert,
            content = palette.alert,
            width = 120.dp,
            height = 40.dp,
            textStyle = WgoType.button.copy(fontSize = 13.sp),
            modifier = Modifier.align(Alignment.TopCenter).offset(y = topAt(186f, pillTouch))
        )
    }
}

/**
 * LOST ROUTE: three reroutes failed in a row; ask instead of looping. Same grid as the confirm surface: badge
 * (alert, about 76x18) @22 (100.3); title `headline-sm` 150x40 @44 (94.0, live region); hint `body-md` 160x18 @86
 * (82.4); RETRY MassiveButton 150 centred at 134 (79.1); END chip 104x32 @176 (102.4). Static: no clock.
 */
@Composable
fun ScreenLostRoute(onRetry: () -> Unit, onEnd: () -> Unit, modifier: Modifier = Modifier) {
    QuestionSurface(
        badge = string(StringKey.NAV_LOST_BADGE),
        title = string(StringKey.NAV_LOST_TITLE),
        subtitle = string(StringKey.NAV_LOST_HINT),
        primaryLabel = string(StringKey.PERM_RETRY),
        primaryVariant = MassiveButtonVariant.Primary,
        primaryIcon = null,
        onPrimary = onRetry,
        secondaryLabel = string(StringKey.BTN_END),
        onSecondary = onEnd,
        modifier = modifier
    )
}

/**
 * END NAVIGATION? confirm (Back or CANCEL during navigation): title `headline-sm` 150x40 @44 (94.0, live region);
 * destination `body-md` 160x18 @86 (82.4); END MassiveButton (alert, close icon) 150 centred at 134 (79.1; 120 clipped TERMINAR + icon); KEEP chip
 * 104x32 @176 (102.4). Back on this surface means KEEP. Static: no clock.
 */
@Composable
fun ScreenEndNavigation(destination: String, onEnd: () -> Unit, onKeep: () -> Unit, modifier: Modifier = Modifier) {
    QuestionSurface(
        badge = null,
        title = string(StringKey.NAV_END_TITLE),
        subtitle = destination,
        primaryLabel = string(StringKey.BTN_END),
        primaryVariant = MassiveButtonVariant.Alert,
        primaryIcon = WgoIcons.Close,
        onPrimary = onEnd,
        secondaryLabel = string(StringKey.BTN_KEEP),
        onSecondary = onKeep,
        modifier = modifier
    )
}

@Composable
private fun QuestionSurface(
    badge: String?,
    title: String,
    subtitle: String,
    primaryLabel: String,
    primaryVariant: MassiveButtonVariant,
    primaryIcon: androidx.compose.ui.graphics.vector.ImageVector?,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    modifier: Modifier
) {
    val palette = Wgo.palette
    Box(modifier.fillMaxSize().background(palette.canvas)) {
        if (badge != null) StatusBadge(
            text = badge,
            variant = StatusBadgeVariant.Alert,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = 22.dp).wgoRiseIn()
        )
        Text(
            text = title,
            color = palette.ink,
            style = WgoType.headlineSm,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 44.dp)
                .width(150.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
                .wgoRiseIn()
        )
        if (subtitle.isNotEmpty()) WgoMarqueeText(
            text = subtitle,
            color = palette.inkMuted,
            style = WgoType.bodyMd,
            textAlign = TextAlign.Center,
            tapToRestart = true,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = 86.dp).width(160.dp).wgoRiseIn()
        )
        val buttonTouch = wgoTouchSide(52.dp)
        MassiveButton(
            onClick = onPrimary,
            label = primaryLabel,
            icon = primaryIcon,
            variant = primaryVariant,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = topAt(134f, buttonTouch))
                .width(150.dp)
                .wgoRiseIn(delayMs = 2 * WgoMotion.STAGGER)
        )
        val chipTouch = wgoTouchSide(32.dp)
        WgoChip(
            label = secondaryLabel,
            onClick = onSecondary,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = topAt(192f, chipTouch))
        )
    }
}

/**
 * A pill of any size with a 48 REAL dp touch height. Pressing swaps background and content instantly and scales to
 * 0.96 (release with [WgoMotion.pop]), as every WGO control does.
 */
@Composable
internal fun PillButton(
    label: String,
    onClick: () -> Unit,
    background: Color,
    content: Color,
    width: Dp,
    height: Dp,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    border: Color? = null,
    uppercase: Boolean = true
) {
    val reduceMotion = LocalWgoReduceMotion.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = if (pressed || reduceMotion) snap() else WgoMotion.pop(),
        label = "pillScale"
    )
    Box(
        modifier = modifier
            .width(width)
            .wgoMinTouch(height = 48.dp)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(width, height)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(if (pressed) content else background)
                .then(if (border != null && !pressed) Modifier.border(1.5.dp, border, CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            val color = if (pressed) background else content
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                }
                // A label that does not fit scrolls (P8 marquee: voice picker rows, the not-found suggestion).
                WgoMarqueeText(
                    text = if (uppercase) label.uppercase() else label,
                    style = textStyle,
                    color = color
                )
            }
        }
    }
}

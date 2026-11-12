package com.wgo.navigator.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.wgo.navigator.core.Maneuver
import com.wgo.navigator.ui.StringKey
import com.wgo.navigator.ui.string
import com.wgo.navigator.ui.screens.HudMode
import com.wgo.navigator.ui.theme.ClockRate
import com.wgo.navigator.ui.theme.LocalWgoReduceMotion
import com.wgo.navigator.ui.theme.Wgo
import com.wgo.navigator.ui.theme.WgoClock
import com.wgo.navigator.ui.theme.WgoMotion
import com.wgo.navigator.ui.theme.WgoType
import com.wgo.navigator.ui.theme.atLeastRealDp
import com.wgo.navigator.ui.theme.wgoMinTouch

/** What the navigation HUD shows; built by the screen from the engine state. */
data class HudContent(
    val maneuver: Maneuver?,
    /** Distance to the next maneuver in metres (null: no guidance yet). */
    val distanceM: Double?,
    /** "250 m" split as number and unit, already localised. */
    val distanceNumber: String,
    val distanceUnit: String,
    val instruction: String,
    /** OFF ROUTE / REROUTING text, replacing the instruction (null when on the route). */
    val status: String?,
    /** Remaining distance · ETA, or null. */
    val totals: String?
)

/** Geometry of the HUD (reference dp, spec section 7 / tokens.json `navigation`). */
object NavHudGeometry {
    val ExpandedWidth = 150.dp
    val ExpandedHeight = 90.dp
    val ExpandedTop = 30.dp
    val ExpandedRadius = 32.dp
    val CompactWidth = 112.dp
    val CompactHeight = 36.dp
    val CompactTop = 22.dp
}

private val STRAIGHT_LIKE = setOf(Maneuver.STRAIGHT, Maneuver.DEPART, Maneuver.ARRIVE)
private val LEFT_LIKE = setOf(Maneuver.SLIGHT_LEFT, Maneuver.LEFT, Maneuver.SHARP_LEFT, Maneuver.U_TURN)
private const val PULSE_PERIOD_MS = 700
private val TABULAR_HEADLINE = WgoType.headlineMd.copy(fontFeatureSettings = "tnum")

/**
 * `numeral-lg` with its line box trimmed to the glyphs: the card is a fixed 90 dp and must hold the distance row, two
 * instruction lines and the totals line (measured on the Galaxy Watch5 Pro, the untrimmed row clipped the totals).
 */
private val TRIMMED_NUMERAL = WgoType.numeralLg.copy(
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
    platformStyle = PlatformTextStyle(includeFontPadding = false)
)

/**
 * The adaptive navigation HUD (spec section 7): the EXPANDED card or the COMPACT pill, swapped with a cross-fade and
 * a 0.94 -> 1 scale (an instant cut with reduce motion), never a size animation. The whole card, or the pill grown to
 * 48 real dp high, is one button: [onTap] (the screen toggles the state, or recenters while exploring). Positioned by
 * itself from the top of the screen; align it TopCenter. [clock] drives the imminent pulse (draw phase only).
 */
@Composable
fun NavigationHud(
    mode: HudMode,
    content: HudContent,
    onTap: () -> Unit,
    clock: WgoClock,
    modifier: Modifier = Modifier
) {
    val reduceMotion = LocalWgoReduceMotion.current
    AnimatedContent(
        targetState = mode,
        transitionSpec = {
            if (reduceMotion) EnterTransition.None togetherWith ExitTransition.None
            else (fadeIn(tween(WgoMotion.SNAP)) + scaleIn(tween(WgoMotion.BASE, easing = WgoMotion.Standard), initialScale = 0.94f)) togetherWith
                fadeOut(tween(WgoMotion.SNAP))
        },
        contentAlignment = Alignment.TopCenter,
        modifier = modifier,
        label = "navHud"
    ) { shown ->
        when (shown) {
            HudMode.Expanded -> HudCard(content, onTap, clock)
            HudMode.Compact -> HudPill(content, onTap)
        }
    }
}

/** Imminent (<= 50 m) colours: `primary` / `onPrimary`; otherwise `surface` / `ink`. */
private data class HudColors(val background: Color, val content: Color, val accent: Color, val muted: Color)

@Composable
private fun hudColors(imminent: Boolean): HudColors {
    val p = Wgo.palette
    return if (imminent) HudColors(p.primary, p.onPrimary, p.onPrimary, p.onPrimary)
    else HudColors(p.surface, p.ink, p.primaryInk, p.inkMuted)
}

private fun HudContent.imminent() = distanceM != null && distanceM <= TURN_IMMINENT_METERS

@Composable
private fun ManeuverIcon(maneuver: Maneuver?, tint: Color, size: Dp) {
    val turn = remember { WgoIcons.turnRight(2.6f) }
    Icon(
        imageVector = if (maneuver == null || maneuver in STRAIGHT_LIKE) WgoIcons.ArrowUpward else turn,
        contentDescription = null,
        tint = tint,
        // Left turns mirror the right-turn glyph.
        modifier = Modifier.size(size).graphicsLayer { scaleX = if (maneuver in LEFT_LIKE) -1f else 1f }
    )
}

@Composable
private fun HudCard(content: HudContent, onTap: () -> Unit, clock: WgoClock) {
    val palette = Wgo.palette
    val reduceMotion = LocalWgoReduceMotion.current
    val imminent = content.imminent()
    val colors = hudColors(imminent)
    val shape = RoundedCornerShape(NavHudGeometry.ExpandedRadius)
    val pulsing = imminent && !reduceMotion
    if (pulsing) clock.Use(ClockRate.Full)
    val collapse = string(StringKey.HUD_COLLAPSE)
    val spoken = listOfNotNull(
        "${content.distanceNumber} ${content.distanceUnit}", content.status ?: content.instruction, content.totals
    ).joinToString(". ")
    Column(
        modifier = Modifier
            .offset(y = NavHudGeometry.ExpandedTop)
            .graphicsLayer {
                if (pulsing) {
                    val s = 1f + 0.04f * clock.pingPong(PULSE_PERIOD_MS)
                    scaleX = s
                    scaleY = s
                }
            }
            .width(NavHudGeometry.ExpandedWidth)
            .height(NavHudGeometry.ExpandedHeight)
            .background(colors.background, shape)
            .border(1.5.dp.atLeastRealDp(), palette.ink, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = collapse,
                onClick = onTap
            )
            .semantics(mergeDescendants = true) {
                contentDescription = spoken
                if (imminent) liveRegion = LiveRegionMode.Polite
            }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ManeuverIcon(content.maneuver, colors.accent, 26.dp)
            Odometer(text = content.distanceNumber, color = colors.content, style = TRIMMED_NUMERAL)
            Text(text = content.distanceUnit, style = WgoType.labelMd, color = colors.content)
        }
        if (content.status != null) {
            Text(
                text = content.status,
                color = colors.accent,
                style = WgoType.labelMd,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.widthIn(max = 134.dp).semantics { liveRegion = LiveRegionMode.Polite }
            )
        } else {
            // A long street name scrolls (P8 marquee); the card is a tap target itself, so no tap-to-restart here.
            WgoMarqueeText(
                text = content.instruction,
                color = colors.content,
                style = WgoType.bodyMd.copy(fontSize = 12.sp, lineHeight = 14.sp),
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.widthIn(max = 134.dp)
            )
        }
        if (content.totals != null) {
            Text(
                text = content.totals,
                color = colors.muted,
                style = WgoType.labelMd,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.widthIn(max = 124.dp)
            )
        }
    }
}

@Composable
private fun HudPill(content: HudContent, onTap: () -> Unit) {
    val palette = Wgo.palette
    val imminent = content.imminent()
    val colors = hudColors(imminent)
    val expand = string(StringKey.HUD_EXPAND)
    val spoken = listOfNotNull("${content.distanceNumber} ${content.distanceUnit}", content.status).joinToString(". ")
    // The touch box is 48 REAL dp high around the 36 dp pill, centred on it.
    Box(
        modifier = Modifier
            .offset(y = NavHudGeometry.CompactTop - (touchHeight() - NavHudGeometry.CompactHeight) / 2)
            .wgoMinTouch(width = NavHudGeometry.CompactWidth, height = 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = expand,
                onClick = onTap
            )
            .semantics(mergeDescendants = true) { contentDescription = spoken },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .width(NavHudGeometry.CompactWidth)
                .height(NavHudGeometry.CompactHeight)
                .background(colors.background, CircleShape)
                .border(1.5.dp.atLeastRealDp(), palette.ink, CircleShape)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)
        ) {
            ManeuverIcon(content.maneuver, colors.accent, 22.dp)
            Odometer(text = content.distanceNumber, color = colors.content, style = TABULAR_HEADLINE)
            Text(text = content.distanceUnit, style = WgoType.labelMd, color = colors.content)
        }
    }
}

/** The pill's touch height in reference dp: 48 REAL dp. */
@Composable
private fun touchHeight(): Dp = com.wgo.navigator.ui.theme.wgoTouchSide(NavHudGeometry.CompactHeight)

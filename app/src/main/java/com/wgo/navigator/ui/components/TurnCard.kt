package com.wgo.navigator.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.wgo.navigator.Language
import com.wgo.navigator.ui.LocalLanguage
import com.wgo.navigator.ui.StringKey
import com.wgo.navigator.ui.string
import com.wgo.navigator.ui.theme.ClockRate
import com.wgo.navigator.ui.theme.LocalWgoReduceMotion
import com.wgo.navigator.ui.theme.Wgo
import com.wgo.navigator.ui.theme.WgoClock
import com.wgo.navigator.ui.theme.WgoMotion
import com.wgo.navigator.ui.theme.WgoType
import com.wgo.navigator.ui.theme.rememberWgoClock
import java.util.Locale

/** Distance at or below which the card is "imminent" (inverted and pulsing). */
const val TURN_IMMINENT_METERS = 50

private const val PULSE_PERIOD_MS = 700
private const val ODOMETER_TRAVEL_DP = 30

/**
 * Turn HUD card, 140 x 66 dp. The distance rolls like an odometer (each character slides 30 dp in 240 ms;
 * tabular numerals keep the width stable). At [imminent] it inverts to `primary`/`onPrimary` and pulses 1 -> 1.04.
 */
@Composable
fun TurnCard(
    distanceMeters: Int,
    instruction: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = remember { WgoIcons.turnRight(2.6f) },
    imminent: Boolean = distanceMeters <= TURN_IMMINENT_METERS,
    clock: WgoClock? = null
) {
    val palette = Wgo.palette
    val sharedClock = if (imminent) clock ?: rememberWgoClock() else null
    sharedClock?.Use(ClockRate.Full)
    val background = if (imminent) palette.primary else palette.surface
    val iconColor = if (imminent) palette.onPrimary else palette.primaryInk
    val textColor = if (imminent) palette.onPrimary else palette.ink

    val (number, unit) = formatDistance(distanceMeters)
    val spoken = "$number $unit. $instruction" +
        if (imminent) ". " + string(StringKey.TURN_IMMINENT) else ""
    val shape = RoundedCornerShape(24.dp)

    Column(
        modifier = modifier
            .graphicsLayer {
                val t = sharedClock
                if (t != null) {
                    val s = 1f + 0.04f * t.pingPong(PULSE_PERIOD_MS)
                    scaleX = s
                    scaleY = s
                }
            }
            .width(140.dp)
            .height(66.dp)
            .background(background, shape)
            .border(1.5.dp, palette.ink, shape)
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = spoken
                if (imminent) liveRegion = LiveRegionMode.Polite
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(26.dp)
            )
            Odometer(text = number, color = textColor)
            Text(text = unit, style = WgoType.labelMd, color = textColor)
        }
        Text(
            text = instruction,
            style = WgoType.bodyMd.copy(fontSize = 12.sp, lineHeight = 14.sp),
            color = textColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 124.dp)
        )
    }
}

/** One AnimatedContent per character, keyed from the right so digit positions stay stable when the length changes. */
@Composable
private fun Odometer(text: String, color: androidx.compose.ui.graphics.Color) {
    val reduceMotion = LocalWgoReduceMotion.current
    val travel = with(LocalDensity.current) { ODOMETER_TRAVEL_DP.dp.roundToPx() }
    Row(modifier = Modifier.clipToBounds()) {
        text.forEachIndexed { index, ch ->
            key(text.length - index) {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        if (reduceMotion) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            val spec = tween<androidx.compose.ui.unit.IntOffset>(240, easing = WgoMotion.Standard)
                            val fade = tween<Float>(240, easing = WgoMotion.Standard)
                            (slideInVertically(spec) { travel } + fadeIn(fade)) togetherWith
                                (slideOutVertically(spec) { -travel } + fadeOut(fade))
                        }
                    },
                    label = "odometerDigit"
                ) { c ->
                    Text(text = c.toString(), style = WgoType.numeralLg, color = color, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun formatDistance(meters: Int): Pair<String, String> {
    val locale = if (LocalLanguage.current == Language.ES) Locale("es", "CO") else Locale.US
    return if (meters >= 1000) {
        String.format(locale, "%.1f", meters / 1000.0) to string(StringKey.UNIT_KM)
    } else {
        meters.toString() to string(StringKey.UNIT_M)
    }
}

@Composable
private fun TurnPreview(dark: Boolean, meters: Int) {
    WgoPreviewFrame(dark) {
        TurnCard(distanceMeters = meters, instruction = "Gira a la derecha en Calle 18")
    }
}

@WgoPreview
@Composable
private fun TurnCardNormalDaylight() = TurnPreview(false, 200)

@WgoPreview
@Composable
private fun TurnCardNormalNight() = TurnPreview(true, 200)

@WgoPreview
@Composable
private fun TurnCardImminentDaylight() = TurnPreview(false, 40)

@WgoPreview
@Composable
private fun TurnCardImminentNight() = TurnPreview(true, 40)

@WgoPreview
@Composable
private fun TurnCardKilometersDaylight() = TurnPreview(false, 1200)

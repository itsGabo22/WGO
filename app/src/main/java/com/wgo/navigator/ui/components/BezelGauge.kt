package com.wgo.navigator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.wgo.navigator.ui.theme.Wgo
import com.wgo.navigator.ui.theme.WgoClock
import com.wgo.navigator.ui.theme.rememberWgoClock

/**
 * Animation layered on top of the gauge track (and the optional progress fill).
 * One composable, one sealed style: pieces on the same screen share a single [WgoClock].
 */
sealed interface BezelStyle {
    /** Track + progress fill only. */
    data object Gauge : BezelStyle

    /** Listening: 30% `primaryInk` arc (3200 ms) and 12% `auraDeep` arc reversed (4600 ms). */
    data object ListeningComets : BezelStyle

    /** Idle: one slow 9% `primaryInk` comet (7000 ms). */
    data object IdleComet : BezelStyle

    /** GPS search: a 22% `alert` arc rotating (1200 ms). */
    data object GpsSearch : BezelStyle
}

private val StrokeWidth = 4.dp
private val Inset = 3.dp

/**
 * Bezel ring: stroke 4 dp, inset 3 dp, track [track], progress fill with round cap from 12 o'clock clockwise.
 * [progress] is a lambda read only inside the draw scope, so progress changes never recompose.
 */
@Composable
fun BezelGauge(
    modifier: Modifier = Modifier,
    progress: () -> Float = { 0f },
    color: Color = Wgo.palette.primaryInk,
    track: Color = Wgo.palette.surfaceRaised,
    style: BezelStyle = BezelStyle.Gauge,
    clock: WgoClock? = null
) {
    val palette = Wgo.palette
    val animated = style != BezelStyle.Gauge
    val sharedClock = if (animated) clock ?: rememberWgoClock() else null

    Canvas(modifier = modifier.fillMaxSize().clearAndSetSemantics { }) {
        val stroke = StrokeWidth.toPx()
        val inset = Inset.toPx() + stroke / 2f
        val diameter = size.minDimension - inset * 2f
        val topLeft = Offset(inset, inset)
        val arcSize = Size(diameter, diameter)
        val round = Stroke(stroke, cap = StrokeCap.Round)

        drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))

        val fill = progress().coerceIn(0f, 1f)
        if (fill > 0f) drawArc(color, -90f, 360f * fill, false, topLeft, arcSize, style = round)

        val t = sharedClock
        if (t != null) when (style) {
            BezelStyle.ListeningComets -> {
                comet(palette.primaryInk, 0.30f, t.phase(3200), topLeft, arcSize, round)
                comet(palette.auraDeep, 0.12f, -t.phase(4600), topLeft, arcSize, round)
            }
            BezelStyle.IdleComet -> comet(palette.primaryInk, 0.09f, t.phase(7000), topLeft, arcSize, round)
            BezelStyle.GpsSearch -> comet(palette.alert, 0.22f, t.phase(1200), topLeft, arcSize, round)
            BezelStyle.Gauge -> Unit
        }
    }
}

/** Draws an arc covering [fraction] of the ring whose head starts at [turns] revolutions from 12 o'clock. */
private fun DrawScope.comet(
    color: Color, fraction: Float, turns: Float, topLeft: Offset, arcSize: Size, stroke: Stroke
) {
    drawArc(color, -90f + 360f * turns, 360f * fraction, false, topLeft, arcSize, style = stroke)
}

@Composable
private fun GaugePreview(dark: Boolean, style: BezelStyle, progress: Float = 0f) {
    WgoPreviewFrame(dark) {
        BezelGauge(progress = { progress }, style = style)
    }
}

@WgoPreview
@Composable
private fun BezelGaugeProgressDaylight() = GaugePreview(false, BezelStyle.Gauge, 0.65f)

@WgoPreview
@Composable
private fun BezelGaugeProgressNight() = GaugePreview(true, BezelStyle.Gauge, 0.65f)

@WgoPreview
@Composable
private fun BezelGaugeListeningDaylight() = GaugePreview(false, BezelStyle.ListeningComets)

@WgoPreview
@Composable
private fun BezelGaugeListeningNight() = GaugePreview(true, BezelStyle.ListeningComets)

@WgoPreview
@Composable
private fun BezelGaugeIdleDaylight() = GaugePreview(false, BezelStyle.IdleComet)

@WgoPreview
@Composable
private fun BezelGaugeIdleNight() = GaugePreview(true, BezelStyle.IdleComet)

@WgoPreview
@Composable
private fun BezelGaugeGpsDaylight() = GaugePreview(false, BezelStyle.GpsSearch)

@WgoPreview
@Composable
private fun BezelGaugeGpsNight() = GaugePreview(true, BezelStyle.GpsSearch)

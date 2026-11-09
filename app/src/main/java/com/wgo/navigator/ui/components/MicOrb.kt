package com.wgo.navigator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Icon
import com.wgo.navigator.ui.StringKey
import com.wgo.navigator.ui.string
import com.wgo.navigator.ui.theme.ClockRate
import com.wgo.navigator.ui.theme.Wgo
import com.wgo.navigator.ui.theme.WgoClock
import com.wgo.navigator.ui.theme.atLeastRealDp
import com.wgo.navigator.ui.theme.rememberWgoClock
import com.wgo.navigator.ui.theme.wgoMinTouch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** [Waiting]: the system speech dialog is showing (fallback strategy); static, no loop. */
enum class MicState { Idle, Listening, Processing, Waiting }

/** Idle inner border of the orb (docs/design spec: 1 dp #0038B8). Not a palette token, so it lives here. */
private val OrbIdleBorder = Color(0xFF0038B8)

private val OrbSize = 64.dp
private val IconSize = 32.dp
private const val BAR_COUNT = 40
private const val BAR_BASE_RADIUS_DP = 38f
private const val BAR_EXTENT_DP = 11f
private const val BAR_MIN_AMPLITUDE = 0.18f
private const val RING_PERIOD_MS = 2400
private const val BREATH_PERIOD_MS = 1100
private const val SPIN_PERIOD_MS = 1100
private const val PROCESSING_RADIUS_DP = 46f
private const val WAITING_RING_RADIUS_DP = 38f

/**
 * Microphone orb, 64 dp. [state] selects idle, listening (breathing, two pulse rings, 40 voice bars) or
 * processing (rotating arc). Everything animates in the draw phase from one shared [clock].
 * [amplitude] maps a bar index to 0..1; without it, [level] (the live voice level, 0..1, read ONLY here in the draw
 * phase) scales a wave driven by the shared clock phase; with neither, the bars run a synthetic wave.
 * Haptics belong to the ViewModel behind [onClick]; a null [onClick] makes a display-only orb (no button role).
 */
@Composable
fun MicOrb(
    state: MicState,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    amplitude: ((Int) -> Float)? = null,
    level: (() -> Float)? = null,
    clock: WgoClock? = null
) {
    val palette = Wgo.palette
    val animated = state == MicState.Listening || state == MicState.Processing
    val sharedClock = if (animated) clock ?: rememberWgoClock() else null
    sharedClock?.Use(ClockRate.Full)

    val label = string(StringKey.ACTIVATE_MIC)
    val stateLabel = string(
        when (state) {
            MicState.Idle -> StringKey.MIC_STATE_IDLE
            MicState.Listening -> StringKey.MIC_STATE_LISTENING
            MicState.Processing -> StringKey.MIC_STATE_PROCESSING
            MicState.Waiting -> StringKey.MIC_STATE_WAITING
        }
    )

    Box(
        modifier = modifier
            .wgoMinTouch()
            .size(OrbSize)
            .drawBehind {
                if (state == MicState.Waiting) {
                    // One static ring: the system dialog owns the microphone now.
                    drawCircle(palette.primaryInk, WAITING_RING_RADIUS_DP.dp.toPx(), center, style = Stroke(2.dp.toPx()))
                    return@drawBehind
                }
                val t = sharedClock ?: return@drawBehind
                when (state) {
                    MicState.Listening -> {
                        drawPulseRing(palette.primaryInk, t, 0)
                        drawPulseRing(palette.auraDeep, t, RING_PERIOD_MS / 2)
                        drawVoiceBars(palette.primaryInk, palette.auraDeep, t, amplitude, level?.invoke())
                    }
                    MicState.Processing -> {
                        val r = PROCESSING_RADIUS_DP.dp.toPx()
                        drawArc(
                            color = palette.primaryInk,
                            startAngle = -90f + 360f * t.phase(SPIN_PERIOD_MS),
                            sweepAngle = 360f * 0.26f,
                            useCenter = false,
                            topLeft = Offset(center.x - r, center.y - r),
                            size = Size(2 * r, 2 * r),
                            style = Stroke(4.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    MicState.Idle, MicState.Waiting -> Unit
                }
            }
            .semantics(mergeDescendants = true) {
                contentDescription = label
                stateDescription = stateLabel
            }
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(OrbSize)
                .graphicsLayer {
                    val t = sharedClock
                    if (state == MicState.Listening && t != null) {
                        val s = 1f + 0.06f * t.pingPong(BREATH_PERIOD_MS)
                        scaleX = s
                        scaleY = s
                    }
                }
                .clip(CircleShape)
                .background(palette.primary)
                .then(if (state == MicState.Idle) Modifier.border(1.dp.atLeastRealDp(), OrbIdleBorder, CircleShape) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = WgoIcons.Mic,
                contentDescription = null,
                tint = palette.onPrimary,
                modifier = Modifier.size(IconSize)
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPulseRing(color: Color, t: WgoClock, offsetMs: Int) {
    val p = ((t.millis + offsetMs) % RING_PERIOD_MS).toFloat() / RING_PERIOD_MS
    val scale = 1f + 1.1f * p // 1 -> 2.1
    drawCircle(
        color = color.copy(alpha = 0.5f * (1f - p)),
        radius = (OrbSize.toPx() / 2f) * scale,
        center = center,
        style = Stroke(2.dp.toPx())
    )
}

/** All 40 bars in a single pass with a single phase; [level] (0..1, live voice) scales the wave when given. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawVoiceBars(
    primaryInk: Color, auraDeep: Color, t: WgoClock, amplitude: ((Int) -> Float)?, level: Float?
) {
    val phase = t.phase(BREATH_PERIOD_MS * 2)
    val base = BAR_BASE_RADIUS_DP.dp.toPx()
    val extent = BAR_EXTENT_DP.dp.toPx()
    val width = 2.dp.toPx()
    for (i in 0 until BAR_COUNT) {
        val wave = 0.5f + 0.5f * sin(2.0 * PI * (phase * 2f + i * 0.17f)).toFloat()
        val raw = amplitude?.invoke(i)
            ?: level?.let { it.coerceIn(0f, 1f) * (0.55f + 0.45f * wave) }
            ?: wave
        val a = BAR_MIN_AMPLITUDE + (1f - BAR_MIN_AMPLITUDE) * raw.coerceIn(0f, 1f)
        val angle = (2.0 * PI * i / BAR_COUNT) - PI / 2.0
        val cx = cos(angle).toFloat()
        val cy = sin(angle).toFloat()
        drawLine(
            color = if ((i + 1) % 3 == 0) auraDeep else primaryInk,
            start = Offset(center.x + cx * base, center.y + cy * base),
            end = Offset(center.x + cx * (base + extent * a), center.y + cy * (base + extent * a)),
            strokeWidth = width,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun MicPreview(dark: Boolean, state: MicState) {
    WgoPreviewFrame(dark) { MicOrb(state = state, onClick = {}) }
}

@WgoPreview
@Composable
private fun MicOrbIdleDaylight() = MicPreview(false, MicState.Idle)

@WgoPreview
@Composable
private fun MicOrbIdleNight() = MicPreview(true, MicState.Idle)

@WgoPreview
@Composable
private fun MicOrbListeningDaylight() = MicPreview(false, MicState.Listening)

@WgoPreview
@Composable
private fun MicOrbListeningNight() = MicPreview(true, MicState.Listening)

@WgoPreview
@Composable
private fun MicOrbProcessingDaylight() = MicPreview(false, MicState.Processing)

@WgoPreview
@Composable
private fun MicOrbProcessingNight() = MicPreview(true, MicState.Processing)

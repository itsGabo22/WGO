package com.wgo.navigator.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import com.wgo.navigator.Language
import com.wgo.navigator.ui.StringKey
import com.wgo.navigator.ui.components.WgoMarqueeText
import com.wgo.navigator.ui.components.BezelGauge
import com.wgo.navigator.ui.components.WgoIcons
import com.wgo.navigator.ui.formatDistance
import com.wgo.navigator.ui.string
import com.wgo.navigator.ui.stringFor
import com.wgo.navigator.ui.theme.LocalWgoReduceMotion
import com.wgo.navigator.ui.theme.Wgo
import com.wgo.navigator.ui.theme.WgoMotion
import com.wgo.navigator.ui.theme.WgoType
import com.wgo.navigator.ui.theme.wgoTouchSide
import kotlin.math.roundToInt

/** The arrival summary line (spec section 7, P7): "1,2 KM · 8 MIN"; "< 1 MIN" under a minute. Pure. */
object ArrivalSummary {
    fun text(distanceM: Double, durationS: Double, language: Language): String {
        val minutes = if (durationS < 60) stringFor(StringKey.ARRIVAL_UNDER_MIN, language)
        else "${(durationS / 60).roundToInt()} ${stringFor(StringKey.ETA_MIN_UNIT, language)}".uppercase()
        return "${formatDistance(distanceM, language).uppercase()} · $minutes"
    }
}

/** Total length of the arrival timeline (spec: <= 2.5 s). */
const val ARRIVAL_TIMELINE_MS = 2_400

/**
 * ARRIVED, the celebratory version (spec section 7, P7): bezel and a ring close around the destination pin, a check
 * draws itself, one soft radial pulse, then title, place name, trip [summary] and LISTO (Home). One [Animatable]
 * drives everything and is read only in draw / graphicsLayer lambdas (no recomposition per frame); reduce motion shows
 * the final frame. The haptic and the spoken phrase come from the navigation host.
 */
@Composable
fun ScreenArrived(destination: String, onFinish: () -> Unit, modifier: Modifier = Modifier, summary: String? = null) {
    val palette = Wgo.palette
    val reduceMotion = LocalWgoReduceMotion.current
    val t = remember { Animatable(if (reduceMotion) ARRIVAL_TIMELINE_MS.toFloat() else 0f) }
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) t.snapTo(ARRIVAL_TIMELINE_MS.toFloat())
        else t.animateTo(ARRIVAL_TIMELINE_MS.toFloat(), tween(ARRIVAL_TIMELINE_MS - t.value.toInt(), easing = LinearEasing))
    }
    /** Progress of [start]..[end] ms, eased. */
    fun p(start: Int, end: Int, ease: (Float) -> Float = WgoMotion.Standard::transform): Float =
        ease(((t.value - start) / (end - start)).coerceIn(0f, 1f))

    val pin = rememberVectorPainter(WgoIcons.LocationPin)
    Box(modifier.fillMaxSize().background(palette.canvas)) {
        BezelGauge(progress = { p(0, 900, WgoMotion.EmphasizedDecel::transform) }, color = palette.go)

        // Disc @26 (centre y 56), its ring, the pin -> check swap and the single pulse: one canvas, draw phase only.
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, 56.dp.toPx())
            val pulse = p(1100, 1800, LinearEasing::transform)
            if (pulse > 0f && pulse < 1f) {
                drawCircle(palette.go.copy(alpha = 0.4f * (1f - pulse)), radius = 40.dp.toPx() * (1f + 0.8f * pulse), center = c,
                    style = Stroke(2.dp.toPx()))
            }
            drawCircle(palette.go, radius = 30.dp.toPx() * (0.85f + 0.15f * p(0, 500)), center = c)
            val ring = p(200, 900)
            if (ring > 0f) {
                val r = 36.dp.toPx()
                drawArc(palette.go, -90f, 360f * ring, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }
            val pinAlpha = 1f - p(900, 1100)
            if (pinAlpha > 0f) {
                val s = 24.dp.toPx()
                translate(c.x - s / 2, c.y - s / 2) {
                    with(pin) { draw(Size(s, s), alpha = pinAlpha, colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(palette.onGo)) }
                }
            }
            val check = p(900, 1300)
            if (check > 0f) {
                // The 24-grid check "M5 12l5 5L20 7" in a 34 dp box, trimmed to [check].
                val unit = 34.dp.toPx() / 24f
                val o = Offset(c.x - 12 * unit, c.y - 12 * unit)
                val full = Path().apply {
                    moveTo(o.x + 5 * unit, o.y + 12 * unit)
                    lineTo(o.x + 10 * unit, o.y + 17 * unit)
                    lineTo(o.x + 20 * unit, o.y + 7 * unit)
                }
                val measure = PathMeasure().apply { setPath(full, false) }
                val part = Path()
                measure.getSegment(0f, measure.length * check, part, true)
                drawPath(part, palette.onGo, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }

        Text(
            text = string(StringKey.NAV_ARRIVED),
            color = palette.ink,
            style = WgoType.headlineLg,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 92.dp)
                .width(170.dp)
                .graphicsLayer {
                    val v = p(1300, 1700, WgoMotion.EmphasizedDecel::transform)
                    alpha = v
                    translationY = (1f - v) * 8.dp.toPx()
                }
                .semantics { liveRegion = LiveRegionMode.Polite }
        )
        // A long place name scrolls (P8 marquee); the ambient arrival keeps the static 2-line clamp.
        if (destination.isNotEmpty()) WgoMarqueeText(
            text = destination,
            color = palette.ink,
            style = WgoType.bodyMd,
            textAlign = TextAlign.Center,
            tapToRestart = true,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = 122.dp).width(160.dp).graphicsLayer { alpha = p(1400, 1800) }
        )
        if (summary != null) Text(
            text = summary,
            color = palette.inkMuted,
            style = WgoType.labelMd,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = 140.dp).width(150.dp).graphicsLayer { alpha = p(1400, 1800) }
        )
        val pillTouch = wgoTouchSide(48.dp)
        PillButton(
            label = string(StringKey.BTN_DONE),
            onClick = onFinish,
            background = palette.ink,
            content = palette.inkInverse,
            width = 120.dp,
            height = 48.dp,
            textStyle = WgoType.button.copy(fontSize = 14.sp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 182.dp - pillTouch / 2)
                .graphicsLayer { alpha = p(1600, 2000) }
        )
    }
}

/**
 * ARRIVED in ambient (spec section 7, P7): static, `ambient-*` greys only, outlines and text: a check outline 40 dp @30
 * (1.5 dp stroke), "¡Llegamos!" `headline-md` @84, the place name `body-md` @116. No fills, no motion.
 */
@Composable
fun AmbientArrivalScreen(destination: String, modifier: Modifier = Modifier) {
    val ambient = Wgo.palette.ambient
    Box(modifier.fillMaxSize().background(ambient.canvas)) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, 50.dp.toPx())
            drawCircle(ambient.stroke, radius = 20.dp.toPx(), center = c, style = Stroke(1.5.dp.toPx()))
            val u = 20.dp.toPx() / 12f
            val check = Path().apply {
                moveTo(c.x - 7 * u + 2 * u, c.y)
                lineTo(c.x - 2 * u, c.y + 5 * u)
                lineTo(c.x + 7 * u, c.y - 5 * u)
            }
            drawPath(check, ambient.ink, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Text(
            text = string(StringKey.NAV_ARRIVED),
            color = ambient.ink,
            style = WgoType.headlineMd,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = 84.dp).width(170.dp)
        )
        if (destination.isNotEmpty()) Text(
            text = destination,
            color = ambient.inkMuted,
            style = WgoType.bodyMd,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = 116.dp).width(160.dp)
        )
    }
}

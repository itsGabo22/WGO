package com.wgo.navigator.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.wgo.navigator.BuildConfig
import com.wgo.navigator.Language
import com.wgo.navigator.metrics.WgoMetrics
import com.wgo.navigator.ui.IntroWords
import com.wgo.navigator.ui.LocalLanguage
import com.wgo.navigator.ui.StringKey
import com.wgo.navigator.ui.string
import com.wgo.navigator.ui.theme.ClockRate
import com.wgo.navigator.ui.theme.LocalWgoReduceMotion
import com.wgo.navigator.ui.theme.LocalWgoScale
import com.wgo.navigator.ui.theme.WGO_DIAL_RADIUS
import com.wgo.navigator.ui.theme.WGO_FIT_LIMIT_GW5P
import com.wgo.navigator.ui.theme.Wgo
import com.wgo.navigator.ui.theme.WgoMotion
import com.wgo.navigator.ui.theme.WgoType
import com.wgo.navigator.ui.theme.rememberWgoClock
import com.wgo.navigator.ui.theme.wgoFit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.sin

/** Spec positions (`top` in reference dp on the 227 canvas). */
private const val TOP_WHERE = 50f
private const val TOP_WE = 94f
private const val TOP_GO = 138f
private const val TOP_TAGLINE = 150f
private const val DOT_RADIUS_DP = 3.5f
private const val DOT_GAP_DP = 3f
private const val RING_FROM_DP = 18f
private const val RING_TO_DP = 104f
private const val RING_STROKE_DP = 2f

/**
 * Cold-start brand moment: WHERE / WE / GO / ? appear word by word, then merge into the WGO lockup (see
 * [IntroTimeline]). Everything is drawn in ONE Canvas from text measured once; the draw lambda reads the screen's
 * single [com.wgo.navigator.ui.theme.WgoClock], so nothing recomposes per frame and nothing allocates per frame.
 *
 * - [onLockup] fires once when the lockup forms (the ViewModel emits the Click haptic).
 * - [onFinished] fires at [IntroTimeline.EXIT_AT]; the caller switches to screen 01 with a cross-dissolve.
 * - A tap calls [onSkip]; with [skipped] the static lockup is drawn and the clock is released.
 * - Reduce motion: the static lockup for [IntroTimeline.REDUCED_HOLD_MS], then [onFinished] (the caller cuts).
 * - [frozenAtMs] freezes the timeline for previews.
 */
@Composable
fun ScreenIntro(
    onLockup: () -> Unit,
    onFinished: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    skipped: Boolean = false,
    frozenAtMs: Long? = null
) {
    val palette = Wgo.palette
    val reduce = LocalWgoReduceMotion.current
    val showTagline = LocalLanguage.current == Language.ES
    val tagline = string(StringKey.INTRO_TAGLINE).uppercase()
    val animated = frozenAtMs == null && !reduce && !skipped

    val clock = rememberWgoClock()
    clock.Use(ClockRate.Full, active = animated)

    val measurer = rememberTextMeasurer(cacheSize = 0)
    val density = LocalDensity.current
    val scale = LocalWgoScale.current
    val layout = remember(measurer, density, showTagline, tagline) {
        IntroLayout.measure(measurer, density, if (showTagline) tagline else null)
    }

    // Debug only: the fit value of every text box, measured with the real font on this device.
    if (BuildConfig.DEBUG && frozenAtMs == null) LaunchedEffect(layout) {
        WgoMetrics.event("intro_fit s=${"%.3f".format(scale)} ${layout.fitReport()} limit=$WGO_FIT_LIMIT_GW5P")
    }

    val lockup by rememberUpdatedState(onLockup)
    val finished by rememberUpdatedState(onFinished)
    if (frozenAtMs == null) {
        LaunchedEffect(reduce, skipped) {
            when {
                skipped -> Unit // the ViewModel already handled the jump
                reduce -> {
                    lockup()
                    delay(IntroTimeline.REDUCED_HOLD_MS)
                    finished()
                }
                else -> {
                    snapshotFlow { clock.millis >= IntroTimeline.LOCKUP }.first { it }
                    lockup()
                    snapshotFlow { clock.millis >= IntroTimeline.EXIT_AT }.first { it }
                    finished()
                }
            }
        }
    }

    val colors = IntroColors(
        initial = palette.primaryInk, // cobalt as text on canvas is primary-ink (spec section 1)
        ink = palette.ink,
        muted = palette.inkMuted,
        ring = palette.map.rangeRing
    )
    val description = "${IntroWords.WHERE} ${IntroWords.WE} ${IntroWords.GO}${IntroWords.MARK}"

    Canvas(
        modifier
            .fillMaxSize()
            .semantics { contentDescription = description }
            .pointerInput(Unit) { detectTapGestures { onSkip() } }
    ) {
        val t = when {
            frozenAtMs != null -> frozenAtMs
            animated -> clock.millis
            else -> IntroTimeline.STATIC_T
        }
        drawIntro(layout, colors, t)
    }
}

private class IntroColors(val initial: Color, val ink: Color, val muted: Color, val ring: Color)

/** Glyph runs measured once, with their phase-A and lockup positions in px relative to the dial centre. */
private class IntroLayout(
    val w1: TextLayoutResult, val here: TextLayoutResult,
    val w2: TextLayoutResult, val e: TextLayoutResult,
    val g: TextLayoutResult, val o: TextLayoutResult, val mark: TextLayoutResult,
    val tagline: TextLayoutResult?,
    private val pxPerDp: Float
) {
    val lineH = w1.size.height.toFloat()
    val baseline = w1.firstBaseline
    val rise = IntroTimeline.RISE_DP * pxPerDp
    val dotR = DOT_RADIUS_DP * pxPerDp
    val ringFrom = RING_FROM_DP * pxPerDp
    val ringTo = RING_TO_DP * pxPerDp
    val ringStroke = Stroke(RING_STROKE_DP * pxPerDp)

    private fun top(dp: Float) = (dp - WGO_DIAL_RADIUS) * pxPerDp
    val y1 = top(TOP_WHERE)
    val y2 = top(TOP_WE)
    val y3 = top(TOP_GO)
    val yTag = top(TOP_TAGLINE)

    private val l1 = w1.width() + here.width()
    val xW1 = -l1 / 2f
    val xHere = xW1 + w1.width()
    private val l2 = w2.width() + e.width()
    val xW2 = -l2 / 2f
    val xE = xW2 + w2.width()
    private val l3 = g.width() + o.width() + mark.width()
    val xG = -l3 / 2f
    val xO = xG + g.width()
    val xMark = xO + o.width()

    /** The lockup "WGO" is centred on the WE row; the dot hangs after it like a period. */
    private val lw = w2.width() + g.width() + o.width()
    val lxW = -lw / 2f
    val lxG = lxW + w2.width()
    val lxO = lxG + g.width()
    val ly = y2
    val dotFromX = xMark + mark.width() / 2f
    val dotFromY = y3 + baseline - dotR
    val dotToX = lxO + o.width() + DOT_GAP_DP * pxPerDp + dotR
    val dotToY = ly + baseline - dotR
    val xTag = -(tagline?.width() ?: 0f) / 2f

    /** Fit values (reference dp) of every text box, for the debug log. */
    fun fitReport(): String {
        fun fit(widthPx: Float, heightPx: Float, topDp: Float) = "%.1f".format(wgoFit(widthPx / pxPerDp, heightPx / pxPerDp, topDp))
        val lockupW = lw + DOT_GAP_DP * pxPerDp + 2 * dotR
        return buildString {
            append("where=").append(fit(l1, lineH, TOP_WHERE))
            append(" we=").append(fit(l2, lineH, TOP_WE))
            append(" go?=").append(fit(l3, lineH, TOP_GO))
            append(" lockup=").append(fit(lockupW, lineH, TOP_WE))
            tagline?.let { append(" tagline=").append(fit(it.width(), it.size.height.toFloat(), TOP_TAGLINE)) }
        }
    }

    companion object {
        fun measure(measurer: TextMeasurer, density: Density, tagline: String?): IntroLayout {
            val style = WgoType.displayXl
            fun m(text: String, s: TextStyle = style) = measurer.measure(text, s, softWrap = false, density = density)
            val where = IntroWords.WHERE
            val we = IntroWords.WE
            val go = IntroWords.GO
            return IntroLayout(
                w1 = m(where.take(1)), here = m(where.drop(1)),
                w2 = m(we.take(1)), e = m(we.drop(1)),
                g = m(go.take(1)), o = m(go.drop(1)), mark = m(IntroWords.MARK),
                tagline = tagline?.let { m(it, WgoType.labelSm) },
                pxPerDp = with(density) { 1.dp.toPx() }
            )
        }
    }
}

private fun TextLayoutResult.width() = size.width.toFloat()

private fun lerp(a: Float, b: Float, f: Float) = a + (b - a) * f

/** Draws the intro at time [t]. Only reads precomputed fields: no allocation per frame. */
private fun DrawScope.drawIntro(l: IntroLayout, c: IntroColors, t: Long) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val tl = IntroTimeline
    val decel = WgoMotion.EmphasizedDecel
    val accel = WgoMotion.EmphasizedAccel

    // Phase A: word reveal (rise + fade).
    val r0 = decel.transform(tl.progress(t, tl.wordStart(0), tl.WORD_IN_MS))
    val r1 = decel.transform(tl.progress(t, tl.wordStart(1), tl.WORD_IN_MS))
    val r2 = decel.transform(tl.progress(t, tl.wordStart(2), tl.WORD_IN_MS))
    // Phase B: collapse and slide.
    val collapse = accel.transform(tl.progress(t, tl.COLLAPSE_START, tl.COLLAPSE_MS))
    val slide = decel.transform(tl.progress(t, tl.SLIDE_START, tl.SLIDE_MS))
    val markOut = tl.progress(t, tl.COLLAPSE_START, tl.COLLAPSE_MS)

    // Non-initial letters collapse toward their left edge.
    if (r0 > 0f && collapse < 1f) {
        letter(l.here, c.ink, cx + l.xHere, cy + l.y1 + (1 - r0) * l.rise, r0 * (1 - collapse), 1 - collapse)
    }
    if (r1 > 0f && collapse < 1f) {
        letter(l.e, c.ink, cx + l.xE, cy + l.y2 + (1 - r1) * l.rise, r1 * (1 - collapse), 1 - collapse)
    }

    // The two W's slide into one: the upper W merges onto the WE row and fades on arrival.
    if (r0 > 0f) {
        val w1Alpha = r0 * (1 - tl.progress(t, tl.SLIDE_START + tl.SLIDE_MS * 6 / 10, tl.SLIDE_MS * 4 / 10))
        if (w1Alpha > 0f) {
            letter(l.w1, c.initial, cx + lerp(l.xW1, l.lxW, slide), cy + lerp(l.y1 + (1 - r0) * l.rise, l.ly, slide), w1Alpha, 1f)
        }
    }
    if (r1 > 0f) {
        letter(l.w2, c.initial, cx + lerp(l.xW2, l.lxW, slide), cy + l.y2 + (1 - r1) * l.rise, r1, 1f)
    }
    if (r2 > 0f) {
        val gy = cy + lerp(l.y3 + (1 - r2) * l.rise, l.ly, slide)
        letter(l.g, c.initial, cx + lerp(l.xG, l.lxG, slide), gy, r2, 1f)
        letter(l.o, c.ink, cx + lerp(l.xO, l.lxO, slide), gy, r2, 1f)
    }

    // The "?" pops in, then hands over to a dot that rides with GO.
    val markAlpha = tl.progress(t, tl.MARK_IN, tl.MARK_FADE_MS) * (1 - markOut)
    if (markAlpha > 0f) {
        val s = tl.popScale(t)
        val x = cx + l.xMark
        val y = cy + l.y3
        withTransform({ scale(s, s, Offset(x + l.mark.width() / 2f, y + l.baseline)) }) {
            drawText(l.mark, color = c.ink, topLeft = Offset(x, y), alpha = markAlpha)
        }
    }
    if (markOut > 0f) {
        val pulse = sin(PI.toFloat() * tl.progress(t, tl.RING_START, 240L))
        drawCircle(
            color = c.initial,
            radius = l.dotR * (1f + 0.5f * pulse),
            center = Offset(cx + lerp(l.dotFromX, l.dotToX, slide), cy + lerp(l.dotFromY, l.dotToY, slide)),
            alpha = markOut
        )
    }

    // One radar ring expands from the centre of the dial (stroke only).
    val ring = tl.progress(t, tl.RING_START, tl.RING_MS)
    if (ring > 0f && ring < 1f) {
        drawCircle(
            color = c.ring,
            radius = lerp(l.ringFrom, l.ringTo, decel.transform(ring)),
            center = Offset(cx, cy),
            alpha = 1f - ring,
            style = l.ringStroke
        )
    }

    // ES tagline under the lockup.
    val tag = l.tagline
    if (tag != null) {
        val a = WgoMotion.Standard.transform(tl.progress(t, tl.TAGLINE_START, tl.TAGLINE_MS))
        if (a > 0f) drawText(tag, color = c.muted, topLeft = Offset(cx + l.xTag, cy + l.yTag), alpha = a)
    }
}

private fun DrawScope.letter(text: TextLayoutResult, color: Color, x: Float, y: Float, alpha: Float, scaleX: Float) {
    if (alpha <= 0f || scaleX <= 0f) return
    if (scaleX >= 1f) {
        drawText(text, color = color, topLeft = Offset(x, y), alpha = alpha)
    } else {
        withTransform({ scale(scaleX, 1f, Offset(x, y)) }) {
            drawText(text, color = color, topLeft = Offset(x, y), alpha = alpha)
        }
    }
}

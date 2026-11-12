package com.wgo.navigator.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Text
import com.wgo.navigator.ui.theme.LocalWgoReduceMotion
import kotlinx.coroutines.delay

/** Marquee timing (spec section 6, WgoMarqueeText). */
object WgoMarquee {
    const val START_DELAY_MS = 800
    const val CYCLES = 2
    const val VELOCITY_DP_S = 30f
    const val GAP_DP = 24f
    const val BETWEEN_CYCLES_MS = 600

    /** How long the scrolling lasts for a single line [lineWidthDp] wide (then the ellipsis is back). */
    fun durationMs(lineWidthDp: Float): Long {
        val cycle = (lineWidthDp + GAP_DP) / VELOCITY_DP_S * 1000f
        return (START_DELAY_MS + CYCLES * cycle + (CYCLES - 1) * BETWEEN_CYCLES_MS).toLong()
    }
}

/**
 * A text that scrolls when it does not fit (spec section 6, P8). Drawn as the clamped text ([maxLines], ellipsis); when
 * the layout overflows (and motion is allowed) it becomes ONE scrolling line, centred in the same measured box, for
 * [WgoMarquee.CYCLES] cycles, then the ellipsized text again. Restarts when [text] changes and, with [tapToRestart]
 * (only for text that is not itself a button), on a tap. Reduced motion (also forced under covering surfaces): static.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WgoMarqueeText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    textAlign: TextAlign? = null,
    tapToRestart: Boolean = false
) {
    val reduceMotion = LocalWgoReduceMotion.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    var overflow by remember(text) { mutableStateOf(false) }
    var box by remember(text) { mutableStateOf(IntSize.Zero) }
    var scrolling by remember(text) { mutableStateOf(false) }
    var restarts by remember { mutableIntStateOf(0) }
    val canScroll = overflow && !reduceMotion && box != IntSize.Zero

    LaunchedEffect(text, canScroll, restarts) {
        scrolling = false
        if (!canScroll) return@LaunchedEffect
        val lineWidthPx = measurer.measure(text, style, softWrap = false, maxLines = 1).size.width
        scrolling = true
        delay(WgoMarquee.durationMs(with(density) { lineWidthPx.toDp().value }))
        scrolling = false
    }

    val tap = if (tapToRestart && overflow && !reduceMotion) {
        Modifier.pointerInput(text) { detectTapGestures { restarts++ } }
    } else Modifier

    if (scrolling) {
        // The clamped text's own size: the switch to one line changes nothing in the layout.
        val size = with(density) { Modifier.size(box.width.toDp(), box.height.toDp()) }
        Box(modifier.then(size).then(tap), contentAlignment = Alignment.CenterStart) {
            Text(
                text = text,
                style = style,
                color = color,
                maxLines = 1,
                softWrap = false,
                textAlign = textAlign,
                modifier = Modifier.basicMarquee(
                    iterations = WgoMarquee.CYCLES,
                    delayMillis = WgoMarquee.BETWEEN_CYCLES_MS,
                    initialDelayMillis = WgoMarquee.START_DELAY_MS,
                    spacing = MarqueeSpacing(WgoMarquee.GAP_DP.dp),
                    velocity = WgoMarquee.VELOCITY_DP_S.dp
                )
            )
        }
    } else {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            onTextLayout = { layout ->
                overflow = layout.hasVisualOverflow
                box = layout.size
            },
            modifier = modifier.then(tap)
        )
    }
}

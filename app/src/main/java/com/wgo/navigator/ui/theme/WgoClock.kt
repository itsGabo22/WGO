package com.wgo.navigator.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos

/**
 * One shared animation clock for every looping piece on a screen. It is a single frame loop that writes a
 * millisecond counter; pieces derive their phase from it and read it ONLY inside draw/graphicsLayer lambdas,
 * so nothing recomposes per frame. It needs zero InfiniteTransitions. With reduce motion the clock stays at 0
 * (static states).
 */
@Stable
class WgoClock internal constructor(private val state: State<Long>) {
    val millis: Long get() = state.value

    /** 0..1 sawtooth over [periodMs]. */
    fun phase(periodMs: Int): Float = (millis % periodMs).toFloat() / periodMs

    /** 0..1..0 triangle (auto-reverse) with a full forward travel of [periodMs]. */
    fun pingPong(periodMs: Int): Float {
        val p = (millis % (2L * periodMs)).toFloat() / periodMs
        return if (p <= 1f) p else 2f - p
    }
}

@Composable
fun rememberWgoClock(): WgoClock {
    val reduce = LocalWgoReduceMotion.current
    val state = remember { mutableLongStateOf(0L) }
    LaunchedEffect(reduce) {
        if (reduce) {
            state.longValue = 0L
        } else {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { state.longValue = (it - start) / 1_000_000L }
        }
    }
    return remember(state) { WgoClock(state) }
}

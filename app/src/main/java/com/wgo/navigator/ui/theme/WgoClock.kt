package com.wgo.navigator.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/** How often a looping piece needs a new frame. */
enum class ClockRate {
    /** Every vsync: interactive pieces (picker, listening orb, imminent turn card, transitions). */
    Full,

    /** About 30 fps: slow ambience (idle comet, GPS-search sweep, radar sweep and flow, caret). */
    Ambience
}

/**
 * One shared animation clock per screen. It is a single frame loop that writes a millisecond counter; pieces derive
 * their phase from it and read it ONLY inside draw/graphicsLayer lambdas, so nothing recomposes per frame (zero
 * InfiniteTransitions).
 *
 * The loop is ref-counted: it runs only while at least one piece is attached with [Use], the screen's lifecycle is
 * RESUMED (screen off, another activity or the app in the background all pause it) and reduce motion is off. It runs
 * at full rate when any attached piece asks for [ClockRate.Full], otherwise at the 30 fps ambience rate. Time does
 * not jump across a pause. With reduce motion the clock stays at 0 (static states).
 */
@Stable
class WgoClock internal constructor() {
    internal val time = mutableLongStateOf(0L)
    internal var fullUsers by mutableIntStateOf(0)
    internal var ambienceUsers by mutableIntStateOf(0)

    val millis: Long get() = time.longValue

    /** 0..1 sawtooth over [periodMs]. */
    fun phase(periodMs: Int): Float = (millis % periodMs).toFloat() / periodMs

    /** 0..1..0 triangle (auto-reverse) with a full forward travel of [periodMs]. */
    fun pingPong(periodMs: Int): Float {
        val p = (millis % (2L * periodMs)).toFloat() / periodMs
        return if (p <= 1f) p else 2f - p
    }

    /** Attaches the calling piece to the clock while it stays in composition (and [active] is true). */
    @Composable
    fun Use(rate: ClockRate, active: Boolean = true) {
        DisposableEffect(this, rate, active) {
            if (active) {
                if (rate == ClockRate.Full) fullUsers++ else ambienceUsers++
            }
            onDispose {
                if (active) {
                    if (rate == ClockRate.Full) fullUsers-- else ambienceUsers--
                }
            }
        }
    }

    internal companion object {
        /** Wait after an ambience frame so the next vsync callback lands about 33 ms later (30 fps). */
        const val AMBIENCE_GAP_MS = 20L
    }
}

/** What the frame loop should do right now. */
internal enum class ClockDrive { Stopped, Ambience, Full }

internal fun clockDrive(reduceMotion: Boolean, resumed: Boolean, fullUsers: Int, ambienceUsers: Int): ClockDrive = when {
    reduceMotion || !resumed -> ClockDrive.Stopped
    fullUsers > 0 -> ClockDrive.Full
    ambienceUsers > 0 -> ClockDrive.Ambience
    else -> ClockDrive.Stopped
}

@Composable
fun rememberWgoClock(): WgoClock {
    val reduce = LocalWgoReduceMotion.current
    val clock = remember { WgoClock() }
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumed by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(clock, reduce) {
        if (reduce) clock.time.longValue = 0L
        snapshotFlow { clockDrive(reduce, resumed, clock.fullUsers, clock.ambienceUsers) }
            .distinctUntilChanged()
            .collectLatest { drive ->
                if (drive == ClockDrive.Stopped) return@collectLatest
                // Continue from the last value so phases do not jump after a pause.
                val base = clock.time.longValue
                val start = withFrameNanos { it }
                while (true) {
                    withFrameNanos { clock.time.longValue = base + (it - start) / 1_000_000L }
                    if (drive == ClockDrive.Ambience) delay(WgoClock.AMBIENCE_GAP_MS)
                }
            }
    }
    return clock
}

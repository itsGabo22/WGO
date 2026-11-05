package com.wgo.navigator.ui.map

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** Pure logic of the map picker dial, kept free of Compose so it is unit-testable. */
object MapPickerMath {
    const val STYLE_COUNT = 8
    const val DEGREES_PER_STYLE = 45f
    const val ARC_SWEEP_DEGREES = 38f

    /** Minimum time between two steps coming from the crown or taps. */
    const val MIN_STEP_INTERVAL_MS = 150L

    /** Accumulated rotary scroll (px) that makes one step. */
    const val ROTARY_PX_PER_STEP = 40f

    /** Fling speed (px/s) above which a release moves exactly one style. */
    const val FLICK_VELOCITY = 600f

    /** The active style for the continuous index [k]: `k.roundToInt().mod(8)`. */
    fun activeIndex(k: Float, count: Int = STYLE_COUNT): Int = k.roundToInt().mod(count)

    /** Ring rotation in degrees: the whole ring turns so the active arc sits under the fixed pointer at 12 o'clock. */
    fun ringRotation(k: Float): Float = -k * DEGREES_PER_STYLE

    /** Index of the neighbour [delta] steps away, wrapping around. */
    fun neighbour(index: Int, delta: Int, count: Int = STYLE_COUNT): Int = (index + delta).mod(count)

    /**
     * Where the dial settles after a drag: the nearest style, or exactly one step in the direction of a flick.
     * [velocityPxPerSec] > 0 means the finger moved right, which goes to the previous style (k decreases).
     */
    fun snapTarget(k: Float, velocityPxPerSec: Float): Float {
        if (abs(velocityPxPerSec) < FLICK_VELOCITY) return k.roundToInt().toFloat()
        return if (velocityPxPerSec > 0) {
            val lower = floor(k)
            if (lower == k) k - 1f else lower
        } else {
            val upper = ceil(k)
            if (upper == k) k + 1f else upper
        }
    }

    /** Radius of the circular iris (0 -> [maxRadius]) for a 0..1 [progress]. */
    fun irisRadius(maxRadius: Float, progress: Float): Float = maxRadius * progress.coerceIn(0f, 1f)

    /** Converts a drag of [dragPx] pixels into a change of the continuous index (dragging right goes back). */
    fun indexDelta(dragPx: Float, pxPerStyle: Float): Float = -dragPx / pxPerStyle
}

/** Drops steps that arrive less than [MapPickerMath.MIN_STEP_INTERVAL_MS] after the previous accepted one. */
class StepThrottle(private val minIntervalMs: Long = MapPickerMath.MIN_STEP_INTERVAL_MS) {
    private var last = Long.MIN_VALUE

    fun accept(nowMs: Long): Boolean {
        if (last != Long.MIN_VALUE && nowMs - last < minIntervalMs) return false
        last = nowMs
        return true
    }
}

/** Turns raw rotary deltas into whole steps: one step per [MapPickerMath.ROTARY_PX_PER_STEP] of accumulated scroll. */
class RotaryAccumulator(private val pxPerStep: Float = MapPickerMath.ROTARY_PX_PER_STEP) {
    private var accumulated = 0f

    /** Returns -1, 0 or +1 for this delta. */
    fun add(delta: Float): Int {
        accumulated += delta
        return when {
            accumulated >= pxPerStep -> { accumulated = 0f; 1 }
            accumulated <= -pxPerStep -> { accumulated = 0f; -1 }
            else -> 0
        }
    }
}

package com.wgo.navigator.ui.screens

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Timeline of the cold-start intro (all times in ms from the first frame). Pure math so it is unit-tested; the
 * screen applies the easings from WgoMotion on top of the linear progress values returned here.
 *
 * Phase A (reveal, 150-1900): WHERE, WE and GO rise 14 dp and fade in one at a time (320 ms each, 540 ms apart);
 * the "?" pops onto GO at 1600.
 * Phase B (merge, 1900-2550): HERE and E collapse (alpha + scaleX), both W's slide into one W, GO attaches to it and
 * the "?" turns into a dot that emits one radar ring.
 * Phase C (hold, 2550-2800): the WGO lockup holds 250 ms (one Click haptic at [LOCKUP]); then a [DISSOLVE_MS]
 * cross-dissolve to screen 01 ends at [TOTAL] = 3000.
 */
object IntroTimeline {
    const val WHERE_IN = 150L
    const val WE_IN = 690L
    const val GO_IN = 1230L
    const val MARK_IN = 1600L
    const val WORD_IN_MS = 320L
    const val MARK_FADE_MS = 120L
    const val RISE_DP = 14f

    const val PHASE_A_END = 1900L

    const val COLLAPSE_START = 1900L
    const val COLLAPSE_MS = 300L
    const val SLIDE_START = 2000L
    const val SLIDE_MS = 550L
    const val RING_START = 2150L
    const val RING_MS = 650L
    const val TAGLINE_START = 2250L
    const val TAGLINE_MS = 300L

    /** The lockup is complete: the slide ends here and the Click haptic fires. */
    const val LOCKUP = SLIDE_START + SLIDE_MS
    const val HOLD_MS = 250L

    /** Screen 01 is requested at this time; the cross-dissolve runs after it. */
    const val EXIT_AT = LOCKUP + HOLD_MS
    const val DISSOLVE_MS = 200
    const val TOTAL = EXIT_AT + DISSOLVE_MS

    /** A tap or Back jumps to the lockup and dissolves to screen 01 in this time. */
    const val SKIP_DISSOLVE_MS = 160

    /** Reduce motion: the static lockup is shown this long, then a 120 ms cut. */
    const val REDUCED_HOLD_MS = 600L

    /** Time drawn for the static lockup (skip and reduce motion): everything settled, the ring already gone. */
    const val STATIC_T = EXIT_AT

    enum class Phase { Waiting, Reveal, Merge, Hold, Done }

    fun phaseAt(t: Long): Phase = when {
        t < WHERE_IN -> Phase.Waiting
        t < PHASE_A_END -> Phase.Reveal
        t < LOCKUP -> Phase.Merge
        t < EXIT_AT -> Phase.Hold
        else -> Phase.Done
    }

    /** Linear 0..1 progress of a segment starting at [start] and lasting [duration], clamped. */
    fun progress(t: Long, start: Long, duration: Long): Float = when {
        t <= start -> 0f
        t >= start + duration -> 1f
        else -> (t - start).toFloat() / duration
    }

    /** Start time of word [index] (0 WHERE, 1 WE, 2 GO). */
    fun wordStart(index: Int): Long = when (index) {
        0 -> WHERE_IN
        1 -> WE_IN
        else -> GO_IN
    }

    /**
     * Scale of the "?" from [MARK_IN]: the step response of the `pop` spring (damping 0.6, stiffness 400,
     * i.e. WgoMotion.pop), solved analytically so it can be read from the clock. 0 before the start, overshoots
     * slightly, settles to 1.
     */
    fun popScale(t: Long): Float {
        if (t <= MARK_IN) return 0f
        val s = (t - MARK_IN) / 1000f
        val w0 = sqrt(POP_STIFFNESS)
        val decay = POP_DAMPING * w0
        val wd = w0 * sqrt(1f - POP_DAMPING * POP_DAMPING)
        val k = POP_DAMPING / sqrt(1f - POP_DAMPING * POP_DAMPING)
        return 1f - exp(-decay * s) * (cos(wd * s) + k * sin(wd * s))
    }

    private const val POP_DAMPING = 0.6f
    private const val POP_STIFFNESS = 400f // Spring.StiffnessMediumLow
}

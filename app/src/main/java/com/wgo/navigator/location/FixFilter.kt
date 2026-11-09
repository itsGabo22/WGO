package com.wgo.navigator.location

import com.wgo.navigator.TravelMode
import com.wgo.navigator.core.Fix

enum class FixQuality {
    /** Usable by the navigation engine. */
    Good,

    /** Too coarse for the engine (> 50 m walking, > 80 m driving, or unknown): only feeds the "searching" state. */
    Inaccurate,

    /** Older than 10 s: ignored completely. */
    Stale
}

/** Stale/accuracy rules for fixes, shared by the radar and (later) the navigation engine. */
object FixFilter {
    const val MAX_AGE_MS = 10_000L
    const val WALK_MAX_ACCURACY_M = 50f
    const val DRIVE_MAX_ACCURACY_M = 80f

    fun maxAccuracyM(mode: TravelMode?): Float =
        if (mode == TravelMode.Drive) DRIVE_MAX_ACCURACY_M else WALK_MAX_ACCURACY_M

    /** [nowElapsedNanos] is SystemClock.elapsedRealtimeNanos(), the same clock as [Fix.elapsedNanos]. */
    fun classify(fix: Fix, mode: TravelMode?, nowElapsedNanos: Long): FixQuality {
        val ageMs = (nowElapsedNanos - fix.elapsedNanos) / 1_000_000L
        return when {
            ageMs > MAX_AGE_MS -> FixQuality.Stale
            fix.accuracyM.isNaN() || fix.accuracyM > maxAccuracyM(mode) -> FixQuality.Inaccurate
            else -> FixQuality.Good
        }
    }
}

package com.wgo.navigator.service

import com.wgo.navigator.location.LocationProfiles

/**
 * Speed along the route, smoothed, from successive guidance updates (progress metres over elapsed time). Some
 * providers report no speed or 0 for moving fixes (the emulator's injected fixes, some fused fixes); the screen-off
 * interval would then think the user stands still. Pure Kotlin, unit-tested.
 */
class ProgressSpeed(private val alpha: Double = 0.4) {
    private var lastProgressM = Double.NaN
    private var lastAtMs = 0L

    /** Smoothed metres per second, NaN until two updates at least [MIN_DT_MS] apart. */
    var mps: Double = Double.NaN
        private set

    fun onProgress(progressM: Double, nowMs: Long) {
        if (!lastProgressM.isNaN()) {
            val dt = nowMs - lastAtMs
            if (dt < MIN_DT_MS) return
            val v = ((progressM - lastProgressM) * 1000.0 / dt).coerceAtLeast(0.0)
            mps = if (mps.isNaN()) v else mps + alpha * (v - mps)
        }
        lastProgressM = progressM
        lastAtMs = nowMs
    }

    /** The fix's speed when it is a real movement, else this estimate (NaN when neither is known). */
    fun pick(fixSpeedMps: Float): Float = when {
        !fixSpeedMps.isNaN() && fixSpeedMps >= LocationProfiles.STILL_MPS -> fixSpeedMps
        !mps.isNaN() -> mps.toFloat()
        else -> fixSpeedMps
    }

    fun reset() {
        lastProgressM = Double.NaN
        lastAtMs = 0L
        mps = Double.NaN
    }

    companion object {
        const val MIN_DT_MS = 500L
    }
}

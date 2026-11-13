package com.wgo.navigator.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressSpeedTest {
    @Test
    fun estimatesSpeedFromProgressAndPrefersARealFixSpeed() {
        val p = ProgressSpeed()
        assertTrue(p.pick(Float.NaN).isNaN())
        p.onProgress(0.0, 0)
        p.onProgress(10.0, 1_000)
        assertEquals(10.0, p.mps, 1e-9)
        p.onProgress(20.0, 1_200) // too soon: ignored
        assertEquals(10.0, p.mps, 1e-9)
        p.onProgress(30.0, 3_000) // 10 m/s again
        assertEquals(10.0, p.mps, 1e-9)
        // A fix reporting 0 (or nothing) while the route progresses: use the progress.
        assertEquals(10f, p.pick(0f), 1e-6f)
        assertEquals(10f, p.pick(Float.NaN), 1e-6f)
        // A real fix speed wins.
        assertEquals(1.4f, p.pick(1.4f), 1e-6f)
        // Standing still: progress stops, the estimate decays toward 0.
        repeat(20) { p.onProgress(30.0, 4_000L + it * 1_000L) }
        assertTrue(p.mps < 0.3)
        p.reset()
        assertTrue(p.mps.isNaN())
    }
}

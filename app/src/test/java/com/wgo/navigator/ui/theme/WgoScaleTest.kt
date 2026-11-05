package com.wgo.navigator.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.sqrt

class WgoScaleTest {
    private val eps = 0.001f

    @Test fun smallRoundScalesDown() = assertEquals(0.846f, wgoScaleFor(192f), eps)

    @Test fun watch5ProIsAlmostReference() = assertEquals(0.991f, wgoScaleFor(225f), eps)

    @Test fun referenceIsOne() = assertEquals(1f, wgoScaleFor(227f), eps)

    @Test fun largerScreenScalesUp() = assertEquals(1.057f, wgoScaleFor(240f), eps)

    @Test fun scaleClampsAtMax() = assertEquals(1.25f, wgoScaleFor(300f), eps)

    @Test fun scaleClampsAtMin() = assertEquals(0.8f, wgoScaleFor(100f), eps)

    @Test fun textScaleClamps() {
        assertEquals(0.9f, wgoTextScaleFor(0.8f), eps)
        assertEquals(0.9f, wgoTextScaleFor(0.846f), eps)
        assertEquals(0.991f, wgoTextScaleFor(0.991f), eps)
        assertEquals(1.1f, wgoTextScaleFor(1.25f), eps)
    }

    @Test fun minTouchIs48RealDp() {
        assertEquals(48f, wgoMinTouchRefDp(1f), eps)
        assertEquals(56.7f, wgoMinTouchRefDp(0.846f), 0.05f)
    }

    /** The round-dial fit rule is scale invariant: scaling w, h and y by the same factor scales the result too. */
    @Test fun fitRuleIsScaleInvariant() {
        fun fit(w: Float, h: Float, y: Float) = sqrt((w / 2 - h / 2) * (w / 2 - h / 2) + y * y) + h / 2
        val s = wgoScaleFor(192f)
        val reference = fit(104f, 32f, 78.5f)
        val scaled = fit(104f * s, 32f * s, 78.5f * s)
        assertEquals(reference * s, scaled, eps)
        // the same pill passes the limit at both sizes (limit 104 reference dp = 104 * s real dp)
        assert(reference <= 104f && scaled <= 104f * s)
    }
}

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

    /** The runtime helper reproduces the fit values written in the spec. */
    @Test fun fitHelperMatchesSpecPlacements() {
        assertEquals(102.97f, wgoFit(96f, 18f, 19f), 0.01f) // VOICE ON badge @19
        assertEquals(102.36f, wgoFit(104f, 32f, 176f), 0.01f) // CANCEL chip @176
        assert(wgoFits(96f, 18f, 19f))
        assert(!wgoFits(140f, 40f, 180f))
        // a box taller than wide is a circle: fit = |y| + h/2
        assertEquals(40f + 24f, wgoFit(20f, 48f, 113.5f + 40f - 24f), eps)
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

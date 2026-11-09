package com.wgo.navigator.ui.screens

import com.wgo.navigator.ui.screens.IntroTimeline.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntroTimelineTest {
    private val tl = IntroTimeline

    @Test fun phaseBoundaries() {
        assertEquals(Phase.Waiting, tl.phaseAt(0))
        assertEquals(Phase.Waiting, tl.phaseAt(149))
        assertEquals(Phase.Reveal, tl.phaseAt(150))
        assertEquals(Phase.Reveal, tl.phaseAt(1899))
        assertEquals(Phase.Merge, tl.phaseAt(1900))
        assertEquals(Phase.Merge, tl.phaseAt(tl.LOCKUP - 1))
        assertEquals(Phase.Hold, tl.phaseAt(tl.LOCKUP))
        assertEquals(Phase.Done, tl.phaseAt(tl.EXIT_AT))
    }

    @Test fun progressClampsAtBothEnds() {
        assertEquals(0f, tl.progress(-50, 100, 200), 0f)
        assertEquals(0f, tl.progress(100, 100, 200), 0f)
        assertEquals(0.5f, tl.progress(200, 100, 200), 1e-6f)
        assertEquals(1f, tl.progress(300, 100, 200), 0f)
        assertEquals(1f, tl.progress(Long.MAX_VALUE / 2, 100, 200), 0f)
    }

    @Test fun wordsAreOneAtATimeAndSpacedPerSpec() {
        val starts = listOf(tl.wordStart(0), tl.wordStart(1), tl.wordStart(2))
        assertEquals(150L, starts[0])
        starts.zipWithNext { a, b -> assertTrue("spacing ${b - a}", b - a in 520L..560L) }
        // each word has finished its entrance before Phase B begins, the "?" included
        assertTrue(starts.last() + tl.WORD_IN_MS <= tl.PHASE_A_END)
        assertTrue(tl.MARK_IN > tl.GO_IN && tl.MARK_IN + tl.MARK_FADE_MS <= tl.PHASE_A_END)
    }

    @Test fun phaseBAndCFitTheBudget() {
        assertTrue(tl.LOCKUP in 1900L..2600L)
        assertEquals(250L, tl.HOLD_MS)
        assertTrue(tl.RING_START + tl.RING_MS <= tl.EXIT_AT)
        assertTrue(tl.TAGLINE_START + tl.TAGLINE_MS <= tl.LOCKUP)
        assertTrue("total ${tl.TOTAL}", tl.TOTAL <= 3000L)
        assertTrue(tl.SKIP_DISSOLVE_MS == 160)
        assertEquals(tl.EXIT_AT, tl.STATIC_T)
    }

    @Test fun popScaleStartsAtZeroOvershootsAndSettles() {
        assertEquals(0f, tl.popScale(tl.MARK_IN), 0f)
        assertEquals(0f, tl.popScale(0), 0f)
        val peak = (tl.MARK_IN..tl.MARK_IN + 400).maxOf { tl.popScale(it) }
        assertTrue("peak $peak", peak > 1.0f && peak < 1.15f)
        assertEquals(1f, tl.popScale(tl.MARK_IN + 600), 0.01f)
        // settled enough before Phase B takes the mark away
        assertEquals(1f, tl.popScale(tl.PHASE_A_END), 0.05f)
    }
}

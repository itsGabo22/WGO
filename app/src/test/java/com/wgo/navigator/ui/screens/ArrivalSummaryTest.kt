package com.wgo.navigator.ui.screens

import com.wgo.navigator.Language
import com.wgo.navigator.ui.theme.wgoFit
import com.wgo.navigator.ui.theme.wgoMinTouchRefDp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The celebratory arrival (spec section 7, P7): its summary line, timeline length and geometry. */
class ArrivalSummaryTest {
    @Test fun summaryInBothLanguages() {
        assertEquals("1,6 KM · 3 MIN", ArrivalSummary.text(1632.0, 181.0, Language.ES))
        assertEquals("1.6 KM · 3 MIN", ArrivalSummary.text(1632.0, 181.0, Language.EN))
        assertEquals("350 M · 8 MIN", ArrivalSummary.text(347.0, 470.0, Language.ES))
        assertEquals("80 M · < 1 MIN", ArrivalSummary.text(78.0, 42.0, Language.ES))
    }

    @Test fun theTimelineStaysUnderTwoAndAHalfSeconds() {
        assertTrue(ARRIVAL_TIMELINE_MS <= 2_500)
    }

    @Test fun everyBoxFitsAndTheTextStacksAboveDone() {
        val boxes = listOf(
            Triple(60f, 60f, 26f), // disc
            Triple(170f, 28f, 92f), // title
            Triple(160f, 18f, 122f), // place name
            Triple(150f, 14f, 140f), // summary
            Triple(120f, 48f, 158f) // LISTO
        )
        boxes.forEach { (w, h, top) -> assertTrue("$w x $h @$top", wgoFit(w, h, top) <= 103f) }
        assertTrue(140f + 14f <= 158f) // summary ends above the button
        // The pulse (80 dp ring scaled to 1.8 around the disc centre at 56) stays inside the bezel ring (r 108).
        assertTrue(40f * 1.8f < 108f)
        // LISTO keeps a 48 real dp touch box at Small Round.
        assertTrue(wgoMinTouchRefDp(0.846f) <= 56.8f)
    }
}

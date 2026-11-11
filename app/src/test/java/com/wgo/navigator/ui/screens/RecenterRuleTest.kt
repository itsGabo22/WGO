package com.wgo.navigator.ui.screens

import com.wgo.navigator.ui.theme.wgoFit
import com.wgo.navigator.ui.theme.wgoMinTouchRefDp
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The recenter button never covers the user's chevron halo (spec section 7, P7). */
class RecenterRuleTest {
    @Test fun hiddenWhileTheChevronIsUnderIt() {
        assertFalse(RecenterRule.visible(113.5f, 108f))
        assertFalse(RecenterRule.visible(113.5f + 30f, 108f + 20f)) // 36 dp away: the halo would touch it
        assertTrue(RecenterRule.visible(113.5f, 150f)) // the follow anchor: 42 dp below
        assertTrue(RecenterRule.visible(40f, 60f))
        assertTrue(RecenterRule.visible(null, null))
    }

    @Test fun theSlotFitsAndClearsTheCompactPillAndTheAttribution() {
        assertTrue(wgoFit(40f, 40f, RecenterRule.CENTER_Y_DP - 20f) <= 103f)
        // Compact pill 112x36 @22: its 48 real dp touch box ends above the button's at Small Round.
        val touch = wgoMinTouchRefDp(0.846f)
        assertTrue(22f + 18f + touch / 2 <= RecenterRule.CENTER_Y_DP - touch / 2)
        // Attribution pill 150x28 @58 ends at 86, above the 40 dp circle (88..128).
        assertTrue(58f + 28f <= RecenterRule.CENTER_Y_DP - 20f)
    }
}

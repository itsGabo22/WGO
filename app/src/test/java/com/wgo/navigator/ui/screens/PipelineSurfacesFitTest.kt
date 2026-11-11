package com.wgo.navigator.ui.screens

import com.wgo.navigator.ui.theme.WGO_FIT_LIMIT_GW5P
import com.wgo.navigator.ui.theme.wgoFit
import com.wgo.navigator.ui.theme.wgoMinTouchRefDp
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fit rule (spec section 4) for every box of the pipeline surfaces, in reference dp, against the Galaxy Watch5 Pro
 * limit (103). Badge widths are the widest label measured with label-sm tracking (about 96 dp for "DESTINATION").
 */
class PipelineSurfacesFitTest {
    private data class Box(val name: String, val w: Float, val h: Float, val top: Float)

    private val working = listOf(
        Box("working badge", 80f, 18f, 16f),
        Box("working stage label", 130f, 14f, 38f),
        Box("working detail", 150f, 18f, 54f),
        Box("working orb", 64f, 64f, 92f),
        Box("working cancel", 104f, 32f, 176f)
    )

    private val confirm = listOf(
        // P6-B: no badge; the name moves up and narrows, the subtitle takes two lines (distance / neighbourhood).
        Box("confirm name", 130f, 40f, 28f),
        Box("confirm subtitle (2 lines)", 150f, 36f, 70f),
        Box("confirm GO (s=1)", 120f, 52f, 134f - 26f),
        Box("confirm GO (s=0.846)", 120f, wgoMinTouchRefDp(0.846f), 134f - wgoMinTouchRefDp(0.846f) / 2),
        Box("confirm attribution", 130f, 12f, 164f),
        Box("confirm other", 104f, 32f, 176f)
    )

    private val error = listOf(
        Box("error icon", 40f, 40f, 30f),
        Box("error title", 150f, 40f, 80f),
        Box("error hint", 160f, 36f, 124f),
        Box("error retry", 120f, 32f, 170f)
    )

    @Test fun everyBoxFits() {
        val report = (working + confirm + error).map { b -> b to wgoFit(b.w, b.h, b.top) }
        println(report.joinToString("\n") { (b, f) -> "%-24s %5.1f".format(b.name, f) })
        val bad = report.filter { it.second > WGO_FIT_LIMIT_GW5P }
        assertTrue("over the limit: $bad", bad.isEmpty())
    }

    @Test fun theConfirmTextStacksAboveGo() {
        val nameBottom = 28f + 40f
        val subtitleTop = 70f
        val subtitleBottom = 70f + 36f
        val goTop = 134f - 26f
        assertTrue("name ends $nameBottom, subtitle starts $subtitleTop", nameBottom <= subtitleTop)
        assertTrue("subtitle ends $subtitleBottom, GO starts $goTop", subtitleBottom <= goTop)
    }

    @Test fun goAndOtherHitBoxesDoNotOverlapOnSmallRound() {
        val touch = wgoMinTouchRefDp(0.846f)
        val goBottom = 134f + touch / 2
        val otherTop = 192f - touch / 2
        assertTrue("GO ends $goBottom, OTHER starts $otherTop", goBottom <= otherTop)
    }
}

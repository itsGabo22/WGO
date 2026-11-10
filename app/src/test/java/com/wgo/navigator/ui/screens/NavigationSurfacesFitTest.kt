package com.wgo.navigator.ui.screens

import com.wgo.navigator.ui.theme.WGO_FIT_LIMIT_GW5P
import com.wgo.navigator.ui.theme.wgoFit
import com.wgo.navigator.ui.theme.wgoMinTouchRefDp
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fit rule (spec section 4) for every box of the live-navigation surfaces, in reference dp, against the Galaxy Watch5
 * Pro limit (103). Text boxes use their full layout width; badges the widest label measured (about 96 dp).
 */
class NavigationSurfacesFitTest {
    private data class Box(val name: String, val w: Float, val h: Float, val top: Float)

    private val touchSmall = wgoMinTouchRefDp(0.846f)

    private val searching = listOf(
        Box("searching badge", 80f, 18f, 16f),
        Box("searching label", 130f, 14f, 38f),
        Box("searching hint", 150f, 18f, 54f),
        Box("searching disc", 64f, 64f, 92f),
        Box("searching cancel", 104f, 32f, 176f)
    )

    private val gpsLost = listOf(
        // "EN PAUSA" / "PAUSED" (8 / 6 characters) measure about 74 dp; at the 96 dp of the longest badges @18 would be 103.9.
        Box("lost badge", 80f, 18f, 18f),
        Box("lost icon box", 40f, 40f, 64f),
        Box("lost title", 170f, 24f, 112f),
        Box("lost hint", 170f, 18f, 138f),
        Box("lost retry", 120f, 40f, 166f)
    )

    private val question = listOf(
        Box("question badge", 96f, 18f, 22f),
        Box("question title", 150f, 40f, 44f),
        Box("question subtitle", 160f, 18f, 86f),
        Box("question primary (s=1)", 120f, 52f, 134f - 26f),
        Box("question primary (s=0.846)", 120f, touchSmall, 134f - touchSmall / 2),
        Box("question secondary", 104f, 32f, 176f)
    )

    private val arrived = listOf(
        Box("arrived disc", 60f, 60f, 36f),
        Box("arrived title", 170f, 28f, 104f),
        Box("arrived destination", 160f, 18f, 134f),
        Box("arrived finish", 120f, 48f, 158f)
    )

    @Test fun everyBoxFits() {
        val report = (searching + gpsLost + question + arrived).map { b -> b to wgoFit(b.w, b.h, b.top) }
        println(report.joinToString("\n") { (b, f) -> "%-28s %5.1f".format(b.name, f) })
        val bad = report.filter { it.second > WGO_FIT_LIMIT_GW5P }
        assertTrue("over the limit: $bad", bad.isEmpty())
    }

    @Test fun theSpecArrivalButtonPlacementIsOverTheWatch5ProLimit() {
        // Spec section 7 places FINALIZAR 120x48 @160; on the Watch5 Pro that measures 103.2, so it moved to @158.
        assertTrue(wgoFit(120f, 48f, 160f) > WGO_FIT_LIMIT_GW5P)
    }

    @Test fun primaryAndSecondaryHitBoxesDoNotOverlapOnSmallRound() {
        val primaryBottom = 134f + touchSmall / 2
        val secondaryTop = 192f - touchSmall / 2
        assertTrue("primary ends $primaryBottom, secondary starts $secondaryTop", primaryBottom <= secondaryTop)
    }
}

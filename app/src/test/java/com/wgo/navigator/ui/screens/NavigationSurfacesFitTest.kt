package com.wgo.navigator.ui.screens

import com.wgo.navigator.ui.theme.WGO_FIT_LIMIT_GW5P
import com.wgo.navigator.ui.theme.wgoFit
import com.wgo.navigator.ui.theme.wgoMinTouchRefDp
import org.junit.Assert.assertEquals
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
        Box("question primary (s=1)", 150f, 52f, 134f - 26f),
        Box("question primary (s=0.846)", 150f, touchSmall, 134f - touchSmall / 2),
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

    /** Corner-arc fit of a rounded rectangle (spec section 4): the farthest corner arc from the dial centre. */
    private fun roundedRectFit(w: Float, h: Float, top: Float, r: Float, scale: Float = 1f): Float {
        val cy = top + h / 2f - 113.5f // card centre relative to the dial centre
        val dx = (w / 2f - r) * scale
        val topArc = (cy - (h / 2f - r) * scale)
        val bottomArc = (cy + (h / 2f - r) * scale)
        return maxOf(kotlin.math.hypot(dx, topArc), kotlin.math.hypot(dx, bottomArc)) + r * scale
    }

    @Test fun theHudFitsInBothStates() {
        val card = roundedRectFit(150f, 90f, 30f, 32f)
        val cardPulse = roundedRectFit(150f, 90f, 30f, 32f, scale = 1.04f)
        val pill = wgoFit(112f, 36f, 22f)
        println("HUD expanded %.1f (pulse %.1f), compact %.1f".format(card, cardPulse, pill))
        assertTrue("expanded $card", card <= WGO_FIT_LIMIT_GW5P)
        assertTrue("expanded pulse $cardPulse", cardPulse <= WGO_FIT_LIMIT_GW5P)
        assertTrue("compact $pill", pill <= WGO_FIT_LIMIT_GW5P)
        assertTrue("pill visual height <= 36", com.wgo.navigator.ui.components.NavHudGeometry.CompactHeight.value <= 36f)
        assertEquals(150f, com.wgo.navigator.ui.components.NavHudGeometry.ExpandedWidth.value, 0f)
        assertEquals(90f, com.wgo.navigator.ui.components.NavHudGeometry.ExpandedHeight.value, 0f)
        assertEquals(30f, com.wgo.navigator.ui.components.NavHudGeometry.ExpandedTop.value, 0f)
        assertEquals(22f, com.wgo.navigator.ui.components.NavHudGeometry.CompactTop.value, 0f)
    }

    @Test fun theHudNeverCoversTheUserChevron() {
        // Chevron at (113.5, 150) with a 15 dp halo: its top is 135.
        val haloTop = 150f - 15f
        assertTrue(30f + 90f < haloTop)
        assertTrue(22f + 36f < haloTop)
        // The compact pill's 48 real dp touch box stays above it too, even at s = 0.846.
        assertTrue(22f + 18f + touchSmall / 2 < haloTop)
    }

    @Test fun theMapKeepsMoreThanHalfOfTheDialInCompact() {
        // Opaque overlays inside the dial with the compact HUD: pill, two 40 dp zoom buttons, the 86x32 CANCELAR.
        fun stadium(w: Float, h: Float) = (w - h) * h + Math.PI.toFloat() * (h / 2) * (h / 2)
        val circle = Math.PI.toFloat() * 20f * 20f
        val dial = Math.PI.toFloat() * 113.5f * 113.5f
        val covered = stadium(112f, 36f) + 2 * circle + stadium(86f, 32f)
        val visible = 1f - covered / dial
        println("map visible with the compact HUD: %.1f%%".format(visible * 100))
        assertTrue("visible $visible", visible >= 0.55f)
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

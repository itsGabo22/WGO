package com.wgo.navigator.ui.map

import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot

/**
 * Where an alternative route's time-difference pill goes (P11, docs/design "Best route, alternatives and live
 * reroute"), in reference dp of the 227 dp canvas: at the alternative's label point, pulled inside a [MAX_RADIUS_DP]
 * circle around the dial centre, and hidden (null) when it would touch the HUD or a control ([blocked]) or the user's
 * chevron. Pure, so the rule is unit-tested.
 */
object AltLabelRules {
    const val WIDTH_DP = 44f
    const val HEIGHT_DP = 16f
    const val MAX_RADIUS_DP = 78f
    private const val CENTRE_DP = 113.5f

    /** The user's chevron while following (the radar anchor, 150/227 down the canvas). */
    private const val USER_X_DP = 113.5f
    private const val USER_Y_DP = 150f
    private const val USER_CLEARANCE_DP = 18f
    private const val GAP_DP = 2f

    fun place(xDp: Float, yDp: Float, blocked: List<LabelBlock>): Offset? {
        var x = xDp
        var y = yDp
        val r = hypot(x - CENTRE_DP, y - CENTRE_DP)
        if (r > MAX_RADIUS_DP) {
            x = CENTRE_DP + (x - CENTRE_DP) * MAX_RADIUS_DP / r
            y = CENTRE_DP + (y - CENTRE_DP) * MAX_RADIUS_DP / r
        }
        val left = x - WIDTH_DP / 2 - GAP_DP
        val right = x + WIDTH_DP / 2 + GAP_DP
        val top = y - HEIGHT_DP / 2 - GAP_DP
        val bottom = y + HEIGHT_DP / 2 + GAP_DP
        if (blocked.any { left < it.right && right > it.left && top < it.bottom && bottom > it.top }) return null
        if (hypot(x - USER_X_DP, y - USER_Y_DP) < USER_CLEARANCE_DP + HEIGHT_DP / 2) return null
        return Offset(x, y)
    }

    /**
     * The label's spot: [candidates] (reference dp) in order, the first that [place] accepts; after the first one, only
     * points already inside the dial (a label never jumps to a clamped spot far from its route).
     */
    fun firstFree(candidates: List<Offset>, blocked: List<LabelBlock>): Offset? {
        candidates.firstOrNull()?.let { first -> place(first.x, first.y, blocked)?.let { return it } }
        for (c in candidates.drop(1)) {
            if (hypot(c.x - CENTRE_DP, c.y - CENTRE_DP) > MAX_RADIUS_DP) continue
            place(c.x, c.y, blocked)?.let { return it }
        }
        return null
    }
}

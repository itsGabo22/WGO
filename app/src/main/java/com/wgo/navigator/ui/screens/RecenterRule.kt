package com.wgo.navigator.ui.screens

import kotlin.math.hypot

/**
 * Where the explore-mode recenter button sits and when it hides (spec section 7, P7): centre (113.5, 108) on the
 * 227 dp canvas; hidden while the user's chevron (with its 15 dp halo) is closer than [CLEARANCE_DP] to it, so the
 * button never covers the user. A tap on the HUD and the 10 s timer still recenter.
 */
object RecenterRule {
    const val CENTER_X_DP = 113.5f
    const val CENTER_Y_DP = 108f

    /** Button radius 20 + halo radius 15 + 4 dp of air. */
    const val CLEARANCE_DP = 39f

    /** True when the button may be shown with the chevron at ([userXDp], [userYDp]) (null: position unknown). */
    fun visible(userXDp: Float?, userYDp: Float?): Boolean {
        if (userXDp == null || userYDp == null) return true
        return hypot(userXDp - CENTER_X_DP, userYDp - CENTER_Y_DP) >= CLEARANCE_DP
    }
}

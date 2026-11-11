package com.wgo.navigator.ui.screens

import com.wgo.navigator.ui.screens.HudMode.Compact
import com.wgo.navigator.ui.screens.HudMode.Expanded
import org.junit.Assert.assertEquals
import org.junit.Test

class HudRulesTest {
    private var now = 0L
    private var maneuver: Any = "turn-1"
    private var rerouting = false
    private var exploring = false

    private fun input(d: Double?) = HudRules.Input(d, maneuver, rerouting, exploring, now)

    /** Feeds distances in order and returns the states shown. */
    private fun drive(vararg d: Double, start: HudRules.State? = null): Pair<List<HudMode>, HudRules.State> {
        var state = start
        val shown = d.map { m ->
            val i = input(m)
            state = state?.let { HudRules.step(it, i) } ?: HudRules.initial(i)
            state!!.shown
        }
        return shown to state!!
    }

    @Test fun aTripStartsCompactUnlessTheFirstManeuverIsNear() {
        assertEquals(Compact, HudRules.initial(input(220.0)).shown)
        assertEquals(Compact, HudRules.initial(input(900.0)).shown)
        assertEquals(Expanded, HudRules.initial(input(200.0)).shown)
    }

    @Test fun expandsAt200AndCollapsesOnlyAbove240() {
        val (shown, _) = drive(500.0, 241.0, 220.0, 201.0, 200.0, 150.0, 210.0, 239.0, 240.0, 240.5, 230.0)
        assertEquals(
            listOf(Compact, Compact, Compact, Compact, Expanded, Expanded, Expanded, Expanded, Expanded, Compact, Compact),
            shown
        )
    }

    @Test fun gpsJitterAroundTheThresholdDoesNotFlicker() {
        val (shown, _) = drive(205.0, 199.0, 203.0, 198.0, 215.0, 235.0)
        assertEquals(listOf(Compact, Expanded, Expanded, Expanded, Expanded, Expanded), shown)
    }

    @Test fun aTapForcesTheOtherStateUntilTheManeuverChanges() {
        var (_, state) = drive(800.0)
        state = HudRules.toggle(state, input(800.0))
        assertEquals(Expanded, state.shown)
        // Approaching and passing the threshold keeps the user's choice for this maneuver.
        state = HudRules.step(state, input(150.0))
        state = HudRules.toggle(state, input(150.0))
        assertEquals(Compact, state.shown)
        state = HudRules.step(state, input(40.0))
        assertEquals(Compact, state.shown)
        // The next maneuver: the automatic rule is back (far away: COMPACT; near: EXPANDED).
        maneuver = "turn-2"
        state = HudRules.step(state, input(600.0))
        assertEquals(Compact, state.shown)
        assertEquals(null, state.override)
        state = HudRules.step(state, input(180.0))
        assertEquals(Expanded, state.shown)
    }

    @Test fun reroutingAndFourSecondsAfterTheNewRouteAreExpanded() {
        var (_, state) = drive(900.0)
        rerouting = true
        now = 10_000
        state = HudRules.step(state, input(900.0))
        assertEquals(Expanded, state.shown)
        rerouting = false
        maneuver = "new-route-turn-1"
        now = 12_000
        state = HudRules.step(state, input(700.0))
        assertEquals(Expanded, state.shown)
        assertEquals(12_000L, state.newRouteAtMs)
        now = 15_999
        state = HudRules.step(state, input(690.0))
        assertEquals(Expanded, state.shown)
        assertEquals(1L, HudRules.rerouteWindowLeftMs(state, now))
        now = 16_000
        state = HudRules.step(state, input(680.0))
        assertEquals(Compact, state.shown)
        assertEquals(null, state.newRouteAtMs)
    }

    @Test fun aTapWinsOverTheRerouteWindow() {
        var (_, state) = drive(900.0)
        rerouting = true
        state = HudRules.step(state, input(900.0))
        rerouting = false
        maneuver = "new"
        now = 1_000
        state = HudRules.step(state, input(700.0))
        assertEquals(Expanded, state.shown)
        state = HudRules.toggle(state, input(700.0))
        assertEquals(Compact, state.shown)
    }

    @Test fun exploringIsAlwaysCompact() {
        var (_, state) = drive(100.0)
        assertEquals(Expanded, state.shown)
        exploring = true
        state = HudRules.step(state, input(90.0))
        assertEquals(Compact, state.shown)
        rerouting = true
        state = HudRules.step(state, input(90.0))
        assertEquals(Compact, state.shown)
        // Leaving explore restores the rule.
        exploring = false
        state = HudRules.step(state, input(80.0))
        assertEquals(Expanded, state.shown)
    }

    @Test fun stepIsIdempotent() {
        var (_, state) = drive(900.0)
        rerouting = true
        state = HudRules.step(state, input(900.0))
        rerouting = false
        now = 5_000
        val once = HudRules.step(state, input(800.0))
        val twice = HudRules.step(once, input(800.0))
        assertEquals(once, twice)
    }

    @Test fun noGuidanceKeepsTheState() {
        val (shown, _) = drive(100.0)
        assertEquals(Expanded, shown.last())
        assertEquals(Expanded, HudRules.automatic(null, Expanded))
        assertEquals(Compact, HudRules.automatic(null, Compact))
    }
}

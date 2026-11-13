package com.wgo.navigator.ui.screens

/** The two HUD states of the navigation screen (spec section 7, ScreenNavigation). */
enum class HudMode { Expanded, Compact }

/**
 * The adaptive HUD rule, pure (spec section 7): which state the HUD shows, from the distance to the next maneuver, the
 * manual override, the explore flag and the reroute time. Precedence: (1) exploring -> COMPACT; (2) a manual override
 * for the current maneuver; (3) OFF ROUTE / REROUTING, or less than [REROUTE_EXPAND_MS] since the guidance came back
 * after them -> EXPANDED; (4) automatic with hysteresis: EXPANDED at <= [EXPAND_AT_M], COMPACT above
 * [COLLAPSE_ABOVE_M], the previous automatic state in between.
 *
 * [step] is idempotent (the same input twice gives the same state), so it may run during composition.
 */
object HudRules {
    const val EXPAND_AT_M = 200.0
    const val COLLAPSE_ABOVE_M = 240.0
    const val REROUTE_EXPAND_MS = 4_000L

    /** What the screen knows now. [maneuver] identifies the next maneuver (a new step or a new route changes it). */
    data class Input(
        val distanceToNextM: Double?,
        val maneuver: Any?,
        /** OFF ROUTE or REROUTING: the HUD shows the status line. */
        val rerouting: Boolean,
        val exploring: Boolean,
        val nowMs: Long,
        /** P11: the active route's identity: a switch to another known route (no OFF ROUTE before it) opens the window too. */
        val routeKey: Any? = null
    )

    /** The user's tap: [mode] for as long as the next maneuver is [maneuver]. */
    data class Override(val maneuver: Any?, val mode: HudMode)

    data class State(
        /** The automatic (hysteresis) state, kept apart from what is shown. */
        val auto: HudMode,
        val override: Override?,
        val wasRerouting: Boolean,
        /** When the guidance came back after OFF ROUTE / REROUTING (null: no reroute window). */
        val newRouteAtMs: Long?,
        val shown: HudMode,
        /** P11: the route the last step saw. */
        val routeKey: Any? = null
    )

    /** A trip starts COMPACT unless the first maneuver is within [EXPAND_AT_M]. */
    fun initial(input: Input): State = step(State(HudMode.Compact, null, false, null, HudMode.Compact), input)

    fun automatic(distanceM: Double?, previous: HudMode): HudMode = when {
        distanceM == null -> previous
        distanceM <= EXPAND_AT_M -> HudMode.Expanded
        distanceM > COLLAPSE_ABOVE_M -> HudMode.Compact
        else -> previous
    }

    fun step(state: State, input: Input): State {
        val auto = automatic(input.distanceToNextM, state.auto)
        val override = state.override?.takeIf { it.maneuver == input.maneuver }
        val switched = state.routeKey != null && input.routeKey != null && input.routeKey != state.routeKey
        val newRouteAt = when {
            input.rerouting -> null
            state.wasRerouting || switched -> input.nowMs
            else -> state.newRouteAtMs?.takeIf { input.nowMs - it < REROUTE_EXPAND_MS }
        }
        val shown = when {
            input.exploring -> HudMode.Compact
            override != null -> override.mode
            input.rerouting || newRouteAt != null -> HudMode.Expanded
            else -> auto
        }
        return State(auto, override, input.rerouting, newRouteAt, shown, input.routeKey ?: state.routeKey)
    }

    /** A tap on the HUD (not while exploring: there it recenters): the other state until the maneuver changes. */
    fun toggle(state: State, input: Input): State {
        val other = if (state.shown == HudMode.Expanded) HudMode.Compact else HudMode.Expanded
        return step(state.copy(override = Override(input.maneuver, other)), input)
    }

    /** Milliseconds until the reroute window closes (null: no window open), so the screen can schedule a refresh. */
    fun rerouteWindowLeftMs(state: State, nowMs: Long): Long? =
        state.newRouteAtMs?.let { (REROUTE_EXPAND_MS - (nowMs - it)).coerceAtLeast(0L) }
}

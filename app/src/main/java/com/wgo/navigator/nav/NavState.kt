package com.wgo.navigator.nav

import com.wgo.navigator.core.RouteStep
import com.wgo.navigator.ui.map.GeoCoord

/** Monotonic time for the engine (elapsed-realtime milliseconds on the device; a fake in tests). */
fun interface NavClock {
    fun elapsedMs(): Long
}

/** What the navigation engine says about the trip. Every non-terminal state after the first fix keeps the guidance. */
sealed interface NavState {
    /** No usable fix yet (none at all, or only coarse ones). */
    data object WaitingForGps : NavState

    /** On the route: progress, what comes next, and how far the last fix was from the route. */
    data class Navigating(
        val progressM: Double,
        val remainingM: Double,
        val etaS: Double,
        /** The next maneuver (a turn, a "continue", or the arrival); null only on a route with no steps. */
        val next: RouteStep?,
        val distanceToNextM: Double,
        /** The maneuver after [next], if any. */
        val then: RouteStep?,
        val distanceFromRouteM: Double,
        /** The user's position snapped onto the route. */
        val snapped: GeoCoord
    ) : NavState

    /**
     * Off the route for 3 usable fixes in a row (or going the wrong way, P11); guidance stays on the old route until a
     * new one arrives. [offline] (P11): the last reroute could not be sent for lack of a network; the route is not
     * updated until one is back.
     */
    data class OffRoute(val last: Navigating, val offline: Boolean = false) : NavState

    /** A new route has been requested (attempt 1..3 since the user was last on the route). */
    data class Rerouting(val last: Navigating, val attempt: Int) : NavState

    /** Three reroutes in a row did not bring the user back on a route: ask (retry / end) instead of looping. */
    data class LostRoute(val last: Navigating) : NavState

    /** No usable fix for 10 s after having had one; returns to navigating by itself when fixes come back. */
    data class GpsLost(val last: Navigating?) : NavState

    /** Terminal: within the arrival radius of the destination, or past the end of the route. */
    data object Arrived : NavState
}

/** One-off things that happened, for haptics and announcements (the UI layer maps them; the engine plays nothing). */
sealed interface NavEvent {
    /** The next turn is within the approach distance (spec section 8: 200 m). Once per maneuver. */
    data class ApproachingTurn(val step: RouteStep, val distanceM: Double) : NavEvent

    /** The next turn is within the "now" distance (spec section 8: 50 m). Once per maneuver. */
    data class AtTurn(val step: RouteStep, val distanceM: Double) : NavEvent
    data object OffRouteDetected : NavEvent
    data object Rerouted : NavEvent

    /** P11: the user drove onto a known alternative, now the active route (followed by [Rerouted]). */
    data object AlternativeTaken : NavEvent
    data object GpsLost : NavEvent
    data object GpsRecovered : NavEvent
    data object Arrived : NavEvent
}

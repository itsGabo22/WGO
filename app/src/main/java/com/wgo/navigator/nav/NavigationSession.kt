package com.wgo.navigator.nav

import com.wgo.navigator.core.Fix
import com.wgo.navigator.core.Route
import com.wgo.navigator.core.Router
import com.wgo.navigator.core.valueOrNull
import com.wgo.navigator.ui.map.GeoCoord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * One trip: hosts a [NavigationEngine] and does the work the engine only asks for (reroute requests through [router],
 * the once-per-second tick). Framework-free (coroutines only, no Android, Compose, Context or lifecycle), so the
 * ViewModel owns it today and a foreground service can own it later unchanged.
 *
 * The owner feeds fixes with [onFix] and says when fixes stop on purpose ([pause]) and come back ([resume]). Everything
 * runs on [scope]'s dispatcher (one thread at a time, as the engine requires).
 */
class NavigationSession(
    initialRoute: Route,
    private val destination: GeoCoord,
    private val router: Router,
    private val scope: CoroutineScope,
    private val clock: NavClock,
    private val tickMs: Long = 1_000L,
    /** P11: the other routes OSRM offered at the start (each with its steps). */
    initialAlternatives: List<Route> = emptyList(),
    /** P11: a network exists now (no listener; checked before each reroute request). */
    private val online: () -> Boolean = { true }
) {
    private val _state = MutableStateFlow<NavState>(NavState.WaitingForGps)
    val state: StateFlow<NavState> = _state.asStateFlow()

    private val _route = MutableStateFlow(initialRoute)

    /** The route being followed (replaced after a successful reroute or a switch to an alternative). */
    val route: StateFlow<Route> = _route.asStateFlow()

    private val _alternatives = MutableStateFlow(initialAlternatives)

    /** P11: the known routes besides [route], best first (empty without alternatives). */
    val alternatives: StateFlow<List<Route>> = _alternatives.asStateFlow()

    private val _remaining = MutableStateFlow(initialRoute.polyline)

    /**
     * The route still ahead of the user, for the radar: updated only when progress moved at least
     * [REMAINING_STEP_M] (or the route changed), so a stationary user causes no re-projection.
     */
    val remaining: StateFlow<List<GeoCoord>> = _remaining.asStateFlow()

    private val _events = MutableSharedFlow<NavEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<NavEvent> = _events.asSharedFlow()

    /** Reroute requests sent (for the budget counters). */
    var rerouteRequests = 0
        private set

    private val engine = NavigationEngine(initialRoute, clock, { _events.tryEmit(it) }, ::requestReroute, initialAlternatives)

    init {
        _alternatives.value = engine.alternatives
    }
    private var tickJob: Job? = null
    private var rerouteJob: Job? = null
    private var remainingAlong = 0.0

    val geometry: RouteGeometry get() = engine.geometry

    /** Starts the tick (GPS-lost and reroute timing). Idempotent. */
    fun start() {
        if (tickJob?.isActive == true) return
        tickJob = scope.launch {
            while (isActive) {
                delay(tickMs)
                engine.tick()
                publish()
            }
        }
    }

    fun onFix(fix: Fix) {
        engine.onFix(fix)
        publish()
    }

    /** Fixes stop on purpose (app in the background, screen off): no tick, so no false GPS-lost. */
    fun pause() {
        tickJob?.cancel()
        tickJob = null
    }

    /** Fixes resume: restart the GPS-lost timer from now, then tick again. */
    fun resume() {
        engine.onResumed()
        start()
    }

    /** The location request's current interval (0 = normal): widens the GPS-lost rule for slow screen-off requests. */
    fun setExpectedFixInterval(ms: Long) {
        engine.expectedFixIntervalMs = ms
    }

    /** P11: the user picked the known route [index] of [alternatives] (no request). */
    fun selectRoute(index: Int) {
        if (engine.selectRoute(index)) publish(routeChanged = true)
    }

    /**
     * P11 RUTA chip: the next route in a stable order (fastest first, as OSRM gave them) becomes active, with no request.
     * The order is renewed when a reroute brings new routes.
     */
    fun selectNext() {
        val order = routeOrder
        if (order.size < 2) return
        val next = order[(order.indexOfFirst { it === _route.value } + 1).mod(order.size)]
        val index = engine.alternatives.indexOfFirst { it === next }
        if (index >= 0) selectRoute(index)
    }

    /** P11: the known routes in a stable order (the active one is one of them). */
    private var routeOrder: List<Route> = listOf(initialRoute) + engine.alternatives

    private val _choice = MutableStateFlow(RouteChoice(0, routeOrder.size))

    /** P11: which of the known routes is active (1-based on the chip: "RUTA 2/3") and how many there are. */
    val choice: StateFlow<RouteChoice> = _choice.asStateFlow()

    /** LOST ROUTE -> retry. */
    fun retryReroute() {
        engine.retryReroute()
        publish()
    }

    /** Ends the trip: no more ticks or requests. */
    fun stop() {
        tickJob?.cancel()
        rerouteJob?.cancel()
        tickJob = null
        rerouteJob = null
    }

    private fun requestReroute(from: GeoCoord, bearingDeg: Double?) {
        val mode = _route.value.mode
        rerouteJob = scope.launch {
            // P11: no network -> nothing is sent; the engine keeps the old route and asks again later.
            if (!online()) {
                engine.onRerouteResult(null, offline = true)
                publish()
                return@launch
            }
            rerouteRequests++
            // P11: from the current position, the course as a hint, and the new route's own alternatives (one request).
            val result = router.routes(from, destination, mode, bearingDeg).valueOrNull()
            engine.onRerouteResult(result?.firstOrNull(), result?.drop(1).orEmpty())
            publish()
        }
    }

    private fun publish(routeChanged: Boolean = false) {
        var changed = routeChanged
        // A reroute or a switch to an alternative replaced the active route.
        val active = engine.geometry.route
        if (active !== _route.value) {
            _route.value = active
            changed = true
        }
        if (engine.alternatives != _alternatives.value) _alternatives.value = engine.alternatives
        if (routeOrder.none { it === active }) routeOrder = listOf(active) + engine.alternatives // a reroute's routes
        val choice = RouteChoice(routeOrder.indexOfFirst { it === active }.coerceAtLeast(0), routeOrder.size)
        if (choice != _choice.value) _choice.value = choice
        val s = engine.state
        _state.value = s
        val nav = when (s) {
            is NavState.Navigating -> s
            is NavState.OffRoute -> s.last
            is NavState.Rerouting -> s.last
            is NavState.LostRoute -> s.last
            is NavState.GpsLost -> s.last
            else -> null
        } ?: return
        if (changed || kotlin.math.abs(nav.progressM - remainingAlong) >= REMAINING_STEP_M) {
            remainingAlong = nav.progressM
            _remaining.value = engine.geometry.remainingFrom(nav.progressM)
        }
    }

    /** P11: the active route's place among the known routes ([index] 0-based) and their [count]. */
    data class RouteChoice(val index: Int, val count: Int)

    companion object {
        /** The radar's remaining route is cut again only after this much progress. */
        const val REMAINING_STEP_M = 5.0
    }
}

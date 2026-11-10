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
    private val tickMs: Long = 1_000L
) {
    private val _state = MutableStateFlow<NavState>(NavState.WaitingForGps)
    val state: StateFlow<NavState> = _state.asStateFlow()

    private val _route = MutableStateFlow(initialRoute)

    /** The route being followed (replaced after a successful reroute). */
    val route: StateFlow<Route> = _route.asStateFlow()

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

    private val engine = NavigationEngine(initialRoute, clock, { _events.tryEmit(it) }, ::requestReroute)
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

    private fun requestReroute(from: GeoCoord) {
        rerouteRequests++
        val mode = _route.value.mode
        rerouteJob = scope.launch {
            val result = router.route(from, destination, mode).valueOrNull()
            if (engine.onRerouteResult(result) && result != null) _route.value = result
            publish(routeChanged = result != null)
        }
    }

    private fun publish(routeChanged: Boolean = false) {
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
        if (routeChanged || kotlin.math.abs(nav.progressM - remainingAlong) >= REMAINING_STEP_M) {
            remainingAlong = nav.progressM
            _remaining.value = engine.geometry.remainingFrom(nav.progressM)
        }
    }

    companion object {
        /** The radar's remaining route is cut again only after this much progress. */
        const val REMAINING_STEP_M = 5.0
    }
}

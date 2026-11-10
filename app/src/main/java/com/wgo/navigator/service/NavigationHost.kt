package com.wgo.navigator.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.wgo.navigator.Language
import com.wgo.navigator.SettingsRepository
import com.wgo.navigator.core.Fix
import com.wgo.navigator.core.LocationProfile
import com.wgo.navigator.core.LocationSource
import com.wgo.navigator.core.Maneuver
import com.wgo.navigator.core.Place
import com.wgo.navigator.core.Route
import com.wgo.navigator.location.LocationSources
import com.wgo.navigator.metrics.WgoMetrics
import com.wgo.navigator.nav.NavClock
import com.wgo.navigator.nav.NavEvent
import com.wgo.navigator.nav.NavState
import com.wgo.navigator.nav.NavigationSession
import com.wgo.navigator.net.HttpClient
import com.wgo.navigator.net.WgoUserAgent
import com.wgo.navigator.route.OsrmRouter
import com.wgo.navigator.ui.map.GeoCoord
import com.wgo.navigator.ui.map.LiveRadarSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Why a trip ended. */
enum class TripEnd {
    /** END on the confirm or the lost-route surface, CANCEL while searching, or Back out of the trip. */
    Ended,
    Arrived,

    /** The foreground service could not keep running (start refused, location permission gone). */
    ServiceLost,

    /** The app was swiped away from Recents. */
    TaskRemoved
}

/**
 * Process-wide owner of the live trip: the [NavigationSession] (framework-free engine + reroutes), the trip's single
 * location request, the radar data and the trip's events. It is the Android-side adapter around the engine: the
 * [NavigationService] keeps the process in the foreground while a trip runs, and the Activity/ViewModel only observes
 * the flows here and sends commands ([start], [end], [retryReroute], [retryGps], [resumeFixes]). Nothing here runs
 * without a trip: no location request, no tick, no network.
 *
 * Fixes are collected while the service runs (screen on or off, app visible or not) or, if the service could not
 * start, while the app is visible (the pre-P6 behaviour). Main thread only.
 */
class NavigationHost private constructor(context: Context) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** The process's only location source (the ViewModel uses it for the ORIGIN one-shot). */
    val locationSource: LocationSource = LocationSources.create(app)
    private val router = OsrmRouter(HttpClient(WgoUserAgent.identifying))
    private val settings = SettingsRepository(app)

    private val _active = MutableStateFlow(false)

    /** True from [start] until the trip ends. */
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _navState = MutableStateFlow<NavState>(NavState.WaitingForGps)
    val navState: StateFlow<NavState> = _navState.asStateFlow()

    private val _radar = MutableStateFlow<LiveRadarSource?>(null)
    val radar: StateFlow<LiveRadarSource?> = _radar.asStateFlow()

    private val _destinationName = MutableStateFlow("")
    val destinationName: StateFlow<String> = _destinationName.asStateFlow()

    private val _events = MutableSharedFlow<NavEvent>(extraBufferCapacity = 16)

    /** The trip's engine events (turn cues, GPS lost, ...), as they happen. */
    val events: SharedFlow<NavEvent> = _events.asSharedFlow()

    /** How the last trip ended; null while a trip runs or after the UI acknowledged it ([acknowledgeEnd]). */
    private val _lastEnd = MutableStateFlow<TripEnd?>(null)
    val lastEnd: StateFlow<TripEnd?> = _lastEnd.asStateFlow()

    /** The UI language (for the notification); follows the saved setting. */
    @Volatile
    var language: Language = Language.ES
        private set

    private var session: NavigationSession? = null
    private var tripJob: Job? = null
    private var fixJob: Job? = null

    /** The Activity is started (visible, maybe ambient). */
    private var visible = false

    /** The navigation service is in the foreground. */
    private var serviceRunning = false

    // ---- commands ----

    /** Starts a trip on [route] from the fresh [origin] fix. Must be called while the app is in the foreground. */
    fun start(route: Route, place: Place, origin: Fix) {
        stopTrip()
        val s = NavigationSession(route, place.coord, router, scope, NavClock { SystemClock.elapsedRealtime() })
        session = s
        _lastEnd.value = null
        _destinationName.value = place.name
        _navState.value = NavState.WaitingForGps
        val radar = LiveRadarSource(route.mode, GeoCoord(origin.lat, origin.lon), route.polyline, route.steps.getOrNull(1)?.location)
        _radar.value = radar
        _active.value = true
        tripJob = scope.launch {
            launch { settings.settings.collect { language = it.language } }
            // Debug builds only: the active route as `lat,lon` lines (tools/sim_drive.ps1 -FromLogcat replays it).
            launch { s.route.collect { WgoMetrics.route(it.polyline) } }
            launch { s.state.collect(::onNavState) }
            launch { s.events.collect { WgoMetrics.event("nav event=${it.javaClass.simpleName}"); _events.tryEmit(it) } }
            // The radar draws only the remaining route (cut by the session every 5 m of progress) and the next maneuver.
            launch {
                combine(s.remaining, s.state) { remaining, state -> remaining to nextManeuverOf(state) }
                    .collect { (remaining, maneuver) -> radar.setRoute(remaining, maneuver) }
            }
        }
        locationSource.setProfile(LocationProfile.NAVIGATING)
        // The fresh ORIGIN fix starts the engine at once (an old cached fallback is ignored as stale).
        s.onFix(origin)
        radar.onFix(origin)
        startService()
        updateCollection()
        WgoMetrics.event("nav start mode=${route.mode} points=${route.polyline.size} steps=${route.steps.size}")
    }

    /** Ends the trip now: engine, ticks and the location request stop, and the service and its notification go away. */
    fun end(reason: TripEnd = TripEnd.Ended) {
        if (session == null) return
        stopTrip()
        _lastEnd.value = reason
        WgoMetrics.event("nav end reason=$reason")
    }

    /** The UI showed the end of the trip (arrival surface closed, home shown): forget it. */
    fun acknowledgeEnd() {
        _lastEnd.value = null
        if (session == null) _destinationName.value = ""
    }

    /** LOST ROUTE -> retry. */
    fun retryReroute() {
        session?.retryReroute()
    }

    /** RETRY on the GPS-lost overlay, or the location permission granted again: reopen the location request. */
    fun retryGps() {
        if (session == null) return
        locationSource.setProfile(LocationProfile.NAVIGATING)
        fixJob?.cancel()
        fixJob = null
        updateCollection()
    }

    /** Same as [retryGps] when no request is open (permission granted again from the settings). */
    fun resumeFixes() {
        if (fixJob?.isActive != true) retryGps()
    }

    /** Activity onStart / onStop. */
    fun setVisible(value: Boolean) {
        visible = value
        updateCollection()
    }

    // ---- service hooks ----

    internal fun onServiceStarted() {
        serviceRunning = true
        updateCollection()
    }

    internal fun onServiceStopped() {
        serviceRunning = false
        updateCollection()
    }

    // ---- internals ----

    private fun startService() {
        try {
            ContextCompat.startForegroundService(app, Intent(app, NavigationService::class.java))
        } catch (e: RuntimeException) {
            // ForegroundServiceStartNotAllowedException (app not in the foreground) or a missing prerequisite: the trip
            // still runs while the app is visible, as before P6.
            WgoMetrics.event("nav service_start_failed=${e.javaClass.simpleName}")
        }
    }

    /** Collect fixes while the service keeps us in the foreground, or while the app is visible. */
    private fun updateCollection() {
        val s = session
        val want = s != null && (serviceRunning || visible)
        if (want && fixJob?.isActive != true) {
            s!!.resume()
            fixJob = scope.launch {
                locationSource.fixes.collect { fix ->
                    val t0 = System.nanoTime()
                    s.onFix(fix)
                    WgoMetrics.engineFix(System.nanoTime() - t0)
                    _radar.value?.onFix(fix)
                }
            }
        } else if (!want && fixJob != null) {
            // Fixes stop on purpose: no tick, so the gap is not reported as a GPS loss.
            fixJob?.cancel()
            fixJob = null
            s?.pause()
        }
    }

    private fun onNavState(state: NavState) {
        if (state is NavState.Rerouting && _navState.value !is NavState.Rerouting) WgoMetrics.inc(WgoMetrics.Counter.REROUTE)
        _navState.value = state
        val s = session ?: return
        when (state) {
            NavState.Arrived -> end(TripEnd.Arrived)
            is NavState.Navigating ->
                locationSource.setNavigationHint(state.distanceToNextM, s.geometry.onLongStraight(state.progressM, LONG_STRAIGHT_M))
            else -> Unit
        }
    }

    /** Where the next maneuver is (null for the arrival: the route's end already shows it). */
    private fun nextManeuverOf(state: NavState): GeoCoord? {
        val nav = state.guidance() ?: return null
        return nav.next?.takeIf { it.maneuver != Maneuver.ARRIVE }?.location
    }

    private fun stopTrip() {
        val had = session != null
        session?.stop()
        session = null
        tripJob?.cancel()
        tripJob = null
        fixJob?.cancel()
        fixJob = null
        _radar.value = null
        _active.value = false
        locationSource.setProfile(LocationProfile.IDLE)
        locationSource.setNavigationHint(null, false)
        if (had) {
            scope.launch { settings.clearTrip() }
            WgoMetrics.event("nav stop snapshot=${WgoMetrics.snapshot()}")
        }
    }

    companion object {
        /** A straight run this long ahead lets the NAVIGATING request slow to 3 s above 20 km/h. */
        const val LONG_STRAIGHT_M = 300.0

        // Holds only the application context, which lives as long as the process.
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: NavigationHost? = null

        fun get(context: Context): NavigationHost =
            instance ?: synchronized(this) { instance ?: NavigationHost(context).also { instance = it } }
    }
}

/** The guidance kept by every non-terminal state (null while waiting for GPS or after arrival). */
fun NavState.guidance(): NavState.Navigating? = when (this) {
    is NavState.Navigating -> this
    is NavState.OffRoute -> last
    is NavState.Rerouting -> last
    is NavState.LostRoute -> last
    is NavState.GpsLost -> last
    else -> null
}

package com.wgo.navigator

import android.app.Application
import android.os.SystemClock
import com.wgo.navigator.nav.NavClock
import com.wgo.navigator.nav.NavEvent
import com.wgo.navigator.nav.NavState
import com.wgo.navigator.nav.NavigationSession
import com.wgo.navigator.ui.map.LiveRadarSource
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wgo.navigator.core.Fix
import com.wgo.navigator.core.LocationProfile
import com.wgo.navigator.core.LocationSource
import com.wgo.navigator.core.Outcome
import com.wgo.navigator.core.permissions.PermissionBroker
import com.wgo.navigator.core.permissions.PermissionStatus
import com.wgo.navigator.core.permissions.WgoPermission
import com.wgo.navigator.haptics.HapticEvent
import com.wgo.navigator.haptics.HapticSource
import com.wgo.navigator.location.LocationSources
import com.wgo.navigator.core.VoiceEvent
import com.wgo.navigator.core.WgoFailure
import com.wgo.navigator.metrics.WgoMetrics
import com.wgo.navigator.ui.components.MicState
import com.wgo.navigator.voice.DebugUtterances
import com.wgo.navigator.voice.SystemVoiceDialog
import com.wgo.navigator.voice.VoiceInputChain
import com.wgo.navigator.voice.VoiceLevel
import com.wgo.navigator.voice.VoiceStrategy
import com.wgo.navigator.ai.TieredInterpreter
import com.wgo.navigator.core.IntentKind
import com.wgo.navigator.core.Place
import com.wgo.navigator.core.Route
import com.wgo.navigator.geo.NominatimGeocoder
import com.wgo.navigator.net.HttpClient
import com.wgo.navigator.net.WgoUserAgent
import com.wgo.navigator.route.OsrmRouter
import com.wgo.navigator.ui.map.GeoCoord
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import com.wgo.navigator.core.Maneuver
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class TravelMode { Walk, Drive }

/**
 * Owns the screen state machine and the haptic events tied to its state changes.
 * A mic tap runs one real voice session ([VoiceInputChain]); the final transcript is handed to [onTranscript].
 */
class AppViewModel(application: Application) : AndroidViewModel(application), HapticSource {
    // The intro plays once per process: a configuration change keeps this ViewModel, and a relaunch in the same
    // process (after Back left the app, or returning from the background) finds the flag already consumed.
    private val _screen = MutableStateFlow(if (consumeColdStart()) ScreenState.Intro else ScreenState.Listening)
    val screen: StateFlow<ScreenState> = _screen.asStateFlow()

    /** True once the user skipped the intro (tap or Back): the intro draws the static lockup and dissolves fast. */
    private val _introSkipped = MutableStateFlow(false)
    val introSkipped: StateFlow<Boolean> = _introSkipped.asStateFlow()

    private var introLockupFired = false

    private val _micState = MutableStateFlow(MicState.Idle)
    val micState: StateFlow<MicState> = _micState.asStateFlow()

    private val _selectedMode = MutableStateFlow<TravelMode?>(null)
    val selectedMode: StateFlow<TravelMode?> = _selectedMode.asStateFlow()

    private val _haptics = MutableSharedFlow<HapticEvent>(extraBufferCapacity = 8)
    override val haptics: SharedFlow<HapticEvent> = _haptics.asSharedFlow()

    private var listenJob: Job? = null

    /** The WGO lockup formed: one Click, never one per word. */
    fun onIntroLockup() {
        if (introLockupFired) return
        introLockupFired = true
        _haptics.tryEmit(HapticEvent.Click)
    }

    /** The intro timeline ended (or the reduce-motion hold elapsed): show screen 01. */
    fun finishIntro() {
        if (_screen.value == ScreenState.Intro) _screen.value = ScreenState.Listening
    }

    /** Test builds only: replays the intro in a warm process so it can be measured without cold-start noise. */
    fun replayIntroForDebug() {
        if (!BuildConfig.TEST_HOOKS) return
        introLockupFired = false
        _introSkipped.value = false
        _screen.value = ScreenState.Intro
    }

    /** Tap or Back during the intro: jump to the lockup, then straight to screen 01. */
    fun skipIntro() {
        if (_screen.value != ScreenState.Intro) return
        _introSkipped.value = true
        onIntroLockup()
        _screen.value = ScreenState.Listening
    }

    // ---- Voice (one session per tap; created on demand and destroyed on every end) ----

    private val permissions = PermissionBroker.get(application)
    private val voice = VoiceInputChain(application)

    /** True while a recognizer session is running (from start until its flow completes). */
    @Volatile
    private var sessionActive = false

    /** The system speech dialog requests, launched by the Activity (fallback strategy). */
    val voiceDialog: SystemVoiceDialog get() = voice.dialog

    /** Live voice level, written here and read only in the MicOrb draw phase. */
    val voiceLevel = VoiceLevel()

    private val _transcript = MutableStateFlow("")
    val transcript: StateFlow<String> = _transcript.asStateFlow()

    val micPermission: StateFlow<PermissionStatus> = permissions.status
        .map { it.getValue(WgoPermission.MICROPHONE) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, permissions.status.value.getValue(WgoPermission.MICROPHONE))

    /** True while the microphone-denied surface replaces screen 01. */
    private val _micDenied = MutableStateFlow(false)
    val micDenied: StateFlow<Boolean> = _micDenied.asStateFlow()

    /** The UI language, provided by the Activity (owned by the settings). */
    var language: () -> Language = { Language.ES }

    fun onMicTapped() {
        if (_micState.value != MicState.Idle || listenJob?.isActive == true) return
        _transcript.value = ""
        listenJob = viewModelScope.launch {
            // RECORD_AUDIO is asked lazily, and only for the in-app recognizer (the system dialog records by itself).
            if (voice.strategy() == VoiceStrategy.InApp) {
                val status = permissions.ensure(WgoPermission.MICROPHONE)
                if (status != PermissionStatus.Granted) {
                    _micDenied.value = true
                    return@launch
                }
            }
            _micDenied.value = false
            sessionActive = true
            try {
                voice.listen(language()).collect(::onVoiceEvent)
            } finally {
                sessionActive = false
            }
        }
    }

    private fun onVoiceEvent(event: VoiceEvent) {
        when (event) {
            VoiceEvent.Ready -> {
                _micState.value = MicState.Listening
                _haptics.tryEmit(HapticEvent.Tick) // spec section 8: starts listening
            }
            VoiceEvent.AwaitingSystem -> {
                _micState.value = MicState.Waiting
                _haptics.tryEmit(HapticEvent.Tick)
            }
            is VoiceEvent.Level -> voiceLevel.value = event.value
            is VoiceEvent.Partial -> _transcript.value = event.text
            is VoiceEvent.Final -> onTranscript(event.text, null)
            is VoiceEvent.Failed -> {
                voiceLevel.value = 0f
                _micState.value = MicState.Idle
                when (event.failure) {
                    WgoFailure.Cancelled -> Unit
                    WgoFailure.NoPermission -> _micDenied.value = true
                    WgoFailure.Unavailable -> fail(PipelineError.VoiceUnavailable, Step.Listen)
                    WgoFailure.Offline -> fail(PipelineError.Offline, Step.Listen)
                    else -> fail(PipelineError.NotUnderstood, Step.Listen)
                }
            }
        }
    }

    /**
     * A final transcript (from the microphone or, in debug builds, injected): the hand-off to the pipeline.
     * [spokenLanguage] overrides the UI language for interpretation (debug injection only).
     */
    fun onTranscript(text: String, spokenLanguage: Language?) {
        voiceLevel.value = 0f
        _micState.value = MicState.Idle
        _transcript.value = text
        pipelineLanguage = spokenLanguage ?: language()
        runStep(Step.Interpret)
    }

    // ---- Voice-to-route pipeline: Interpreting -> Resolving -> ConfirmPlace -> (ModeSelection) -> Routing ----

    /** Pipeline steps, for retry and for Back ("one step back"). */
    private enum class Step { Listen, Interpret, Resolve, Route }

    private val tripStore = SettingsRepository(application)
    private val interpreter = TieredInterpreter.create(application, WgoUserAgent.plain)
    private val geocoder = NominatimGeocoder(HttpClient(WgoUserAgent.identifying))
    private val router = OsrmRouter(HttpClient(WgoUserAgent.identifying))

    private var pipelineJob: Job? = null
    private var pipelineLanguage = Language.ES
    private var placeQuery: String? = null
    private var failedStep = Step.Listen

    /** True when the travel mode came from the utterance: mode selection is skipped. */
    private var modeSpoken = false

    private val _stage = MutableStateFlow(WorkStage.Interpreting)
    val stage: StateFlow<WorkStage> = _stage.asStateFlow()

    private val _error = MutableStateFlow<PipelineError?>(null)
    val error: StateFlow<PipelineError?> = _error.asStateFlow()

    /** Up to three geocoding results and the one shown (OTHER cycles locally: no extra request). */
    private val _places = MutableStateFlow<List<Place>>(emptyList())
    val places: StateFlow<List<Place>> = _places.asStateFlow()
    private val _placeIndex = MutableStateFlow(0)
    val placeIndex: StateFlow<Int> = _placeIndex.asStateFlow()

    /** The fix the place distances were measured from (the last known one; null when there is none). */
    private val _near = MutableStateFlow<GeoCoord?>(null)
    val near: StateFlow<GeoCoord?> = _near.asStateFlow()

    private val _route = MutableStateFlow<Route?>(null)
    val route: StateFlow<Route?> = _route.asStateFlow()

    val currentPlace: Place? get() = _places.value.getOrNull(_placeIndex.value)

    private fun runStep(step: Step) {
        pipelineJob?.cancel()
        _error.value = null
        pipelineJob = viewModelScope.launch {
            when (step) {
                Step.Listen -> {
                    _screen.value = ScreenState.Listening
                    onMicTapped()
                }
                Step.Interpret -> interpret()
                Step.Resolve -> placeQuery?.let { resolve(it) } ?: fail(PipelineError.NotUnderstood, Step.Listen)
                Step.Route -> {
                    // (A plain null check: `StateFlow.value?.let {}` crashes the Compose lint detector on Kotlin 2.1.)
                    val mode = _selectedMode.value
                    if (mode != null) routeTo(mode) else _screen.value = ScreenState.ModeSelection
                }
            }
        }
    }

    private suspend fun interpret() {
        _stage.value = WorkStage.Interpreting
        _screen.value = ScreenState.Working
        val text = _transcript.value
        val result = interpreter.interpret(text, pipelineLanguage)
        WgoMetrics.event("pipeline interpreted tier=${interpreter.lastTier} kind=${result.kind} mode=${result.mode}")
        when (result.kind) {
            IntentKind.Navigate -> {
                val query = result.placeQuery ?: return fail(PipelineError.NotUnderstood, Step.Listen)
                placeQuery = query
                modeSpoken = result.mode != null
                _selectedMode.value = result.mode
                resolve(query)
            }
            IntentKind.Cancel, IntentKind.Repeat -> goHome()
            IntentKind.ChangeMode, IntentKind.Unknown ->
                fail(if (interpreter.lastRemoteFailed) PipelineError.AiUnavailable else PipelineError.NotUnderstood, Step.Listen)
        }
    }

    private suspend fun resolve(query: String) {
        _stage.value = WorkStage.Resolving
        _screen.value = ScreenState.Working
        val near = locationSource.lastKnown()?.let { GeoCoord(it.lat, it.lon) }
        _near.value = near
        when (val found = geocoder.search(query, near, pipelineLanguage, PLACE_RESULTS)) {
            is Outcome.Ok -> {
                _places.value = found.value
                _placeIndex.value = 0
                _screen.value = ScreenState.ConfirmPlace
                _haptics.tryEmit(HapticEvent.Click) // spec section 8: destination understood
            }
            is Outcome.Err -> fail(
                // A server error keeps the "no internet" message it always had; only a read timeout is "too slow".
                if (found.failure == WgoFailure.NotFound) PipelineError.PlaceNotFound
                else PipelineError.forNetwork(found.failure, PipelineError.Offline),
                Step.Resolve
            )
        }
    }

    /** OTHER: the next of the (up to three) results, no request. */
    fun onOtherPlace() {
        val n = _places.value.size
        if (n > 1) _placeIndex.value = (_placeIndex.value + 1) % n
    }

    /** GO on the confirm surface: route at once when the utterance gave the mode, else ask for it. */
    fun onGo() {
        val mode = _selectedMode.value
        if (modeSpoken && mode != null) {
            _haptics.tryEmit(HapticEvent.HeavyClick) // spec section 8: mode chosen
            runStep(Step.Route)
        } else {
            _screen.value = ScreenState.ModeSelection
        }
    }

    private suspend fun routeTo(mode: TravelMode) {
        val place = currentPlace ?: return goHome()
        _stage.value = WorkStage.Routing
        _screen.value = ScreenState.Working
        if (permissions.ensure(WgoPermission.LOCATION) != PermissionStatus.Granted) {
            return fail(PipelineError.LocationPermission, Step.Route)
        }
        locationSource.setProfile(LocationProfile.ORIGIN)
        val originStart = System.nanoTime()
        val fresh = locationSource.once(ORIGIN_TIMEOUT_MS)
        val fix = (fresh as? Outcome.Ok)?.value ?: locationSource.lastKnown()
        WgoMetrics.event(
            "origin fresh=${fresh is Outcome.Ok} fallback_last=${fresh !is Outcome.Ok && fix != null} " +
                "ms=${(System.nanoTime() - originStart) / 1_000_000}"
        )
        if (fix == null) {
            locationSource.setProfile(LocationProfile.IDLE)
            return fail(PipelineError.RouteUnavailable, Step.Route)
        }
        when (val r = router.route(GeoCoord(fix.lat, fix.lon), place.coord, mode)) {
            is Outcome.Ok -> {
                _route.value = r.value
                WgoMetrics.event(
                    "pipeline routed mode=$mode m=${r.value.distanceM.toInt()} s=${r.value.durationS.toInt()} " +
                        "steps=${r.value.steps.size} points=${r.value.polyline.size} snapshot=${WgoMetrics.snapshot()}"
                )
                tripStore.saveTrip(place.name, place.coord.lat, place.coord.lon, mode)
                startNavigation(r.value, place, fix)
            }
            is Outcome.Err -> {
                locationSource.setProfile(LocationProfile.IDLE)
                fail(PipelineError.forNetwork(r.failure, PipelineError.RouteUnavailable), Step.Route)
            }
        }
    }

    private fun fail(error: PipelineError, step: Step) {
        failedStep = step
        _error.value = error
        _screen.value = ScreenState.Error
        WgoMetrics.event("pipeline error=$error step=$step")
    }

    /** The single retry action of the error surface. */
    fun retry() {
        when (_error.value) {
            PipelineError.MicPermission, PipelineError.VoiceUnavailable, PipelineError.NotUnderstood,
            PipelineError.PlaceNotFound, PipelineError.AiUnavailable, null -> runStep(Step.Listen)
            PipelineError.LocationPermission, PipelineError.RouteUnavailable -> runStep(Step.Route)
            PipelineError.Offline, PipelineError.Timeout -> runStep(failedStep)
        }
    }

    /**
     * Back inside the pipeline: Working cancels the in-flight request and goes one step back (routing returns to the
     * place or mode choice, earlier stages to screen 01); ConfirmPlace and Error go to screen 01; ModeSelection returns
     * to ConfirmPlace.
     */
    fun backInPipeline() {
        when (_screen.value) {
            ScreenState.Working -> {
                pipelineJob?.cancel()
                if (_stage.value == WorkStage.Routing) {
                    locationSource.setProfile(LocationProfile.IDLE)
                    _screen.value = if (modeSpoken) ScreenState.ConfirmPlace else ScreenState.ModeSelection
                } else goHome()
            }
            ScreenState.ModeSelection -> _screen.value = ScreenState.ConfirmPlace
            else -> goHome()
        }
    }

    private fun goHome() {
        pipelineJob?.cancel()
        _error.value = null
        _transcript.value = ""
        _places.value = emptyList()
        _selectedMode.value = null
        modeSpoken = false
        _screen.value = ScreenState.Listening
    }

    /** CANCEL, Back, or the app going to the background: ends the session now and releases the microphone. */
    fun cancelListening() {
        listenJob?.cancel()
        voice.cancel()
        voiceLevel.value = 0f
        _transcript.value = ""
        _micState.value = MicState.Idle
    }

    /** Back from the microphone-denied surface. */
    fun dismissMicDenied() {
        _micDenied.value = false
    }

    /**
     * Activity onStop: never keep the microphone in the background. Not while the system speech dialog is showing (it
     * is what stopped us), and not while the permission dialog is up (no session has started yet).
     */
    fun onAppStopped() {
        if (sessionActive && _micState.value != MicState.Waiting) cancelListening()
        // No location in the background or with the screen off (until the P6 foreground service): the request goes
        // away with the collection, and the engine pauses so the gap is not reported as a GPS loss.
        appStarted = false
        fixJob?.cancel()
        fixJob = null
        session?.pause()
    }

    fun selectMode(mode: TravelMode) {
        _selectedMode.value = mode
        _haptics.tryEmit(HapticEvent.HeavyClick) // spec section 8: mode chosen
        runStep(Step.Route)
    }

    /** Back from settings (and any other screen with nothing to confirm): ends a trip if one runs, then home. */
    fun backToListening() {
        stopNavigation()
        goHome()
    }

    // ---- Live navigation: one NavigationSession per trip, fed by the one location request of the trip ----

    private var session: NavigationSession? = null
    private var sessionJob: Job? = null
    private var fixJob: Job? = null

    /** True between the Activity's onStart and onStop: fixes are only requested while the app is visible. */
    private var appStarted = false

    private val _navState = MutableStateFlow<NavState>(NavState.WaitingForGps)
    val navState: StateFlow<NavState> = _navState.asStateFlow()

    /** True while the END NAVIGATION? confirm covers the navigation screen. */
    private val _endConfirm = MutableStateFlow(false)
    val endConfirm: StateFlow<Boolean> = _endConfirm.asStateFlow()

    /** The radar's data for the current trip (null when there is no trip). */
    private val _radar = MutableStateFlow<LiveRadarSource?>(null)
    val radar: StateFlow<LiveRadarSource?> = _radar.asStateFlow()

    /** Name of the trip's destination (kept for the arrival surface after the trip ended). */
    private val _destinationName = MutableStateFlow("")
    val destinationName: StateFlow<String> = _destinationName.asStateFlow()

    private fun startNavigation(route: Route, place: Place, origin: Fix) {
        stopNavigation(clearTrip = false)
        val s = NavigationSession(route, place.coord, router, viewModelScope, NavClock { SystemClock.elapsedRealtime() })
        session = s
        _destinationName.value = place.name
        _endConfirm.value = false
        _navState.value = NavState.WaitingForGps
        val radar = LiveRadarSource(route.mode, GeoCoord(origin.lat, origin.lon), route.polyline, route.steps.getOrNull(1)?.location)
        _radar.value = radar
        sessionJob = viewModelScope.launch {
            launch { s.state.collect(::onNavState) }
            launch { s.events.collect(::onNavEvent) }
            // The radar draws only the remaining route (cut by the session every 5 m of progress) and the next maneuver.
            launch {
                combine(s.remaining, s.state) { remaining, state -> remaining to nextManeuverOf(state) }
                    .collect { (remaining, maneuver) -> radar.setRoute(remaining, maneuver) }
            }
        }
        _screen.value = ScreenState.Navigation
        locationSource.setProfile(LocationProfile.NAVIGATING)
        // The fresh ORIGIN fix starts the engine at once (an old cached fallback is ignored as stale).
        s.onFix(origin)
        radar.onFix(origin)
        if (appStarted) {
            s.start()
            startFixes()
        }
        WgoMetrics.event("nav start mode=${route.mode} points=${route.polyline.size} steps=${route.steps.size}")
    }

    /** The trip's only continuous location request, while the app is visible. Feeds the engine and the radar. */
    private fun startFixes() {
        val s = session ?: return
        fixJob?.cancel()
        fixJob = viewModelScope.launch {
            locationSource.fixes.collect { fix ->
                s.onFix(fix)
                _radar.value?.onFix(fix)
            }
        }
    }

    private fun onNavState(state: NavState) {
        if (state is NavState.Rerouting && _navState.value !is NavState.Rerouting) WgoMetrics.inc(WgoMetrics.Counter.REROUTE)
        _navState.value = state
        val s = session ?: return
        when (state) {
            NavState.Arrived -> arrive()
            is NavState.Navigating ->
                locationSource.setNavigationHint(state.distanceToNextM, s.geometry.onLongStraight(state.progressM, LONG_STRAIGHT_M))
            else -> Unit
        }
    }

    /** Where the next maneuver is (null for the arrival: the route's end already shows it). */
    private fun nextManeuverOf(state: NavState): GeoCoord? {
        val nav = when (state) {
            is NavState.Navigating -> state
            is NavState.OffRoute -> state.last
            is NavState.Rerouting -> state.last
            is NavState.LostRoute -> state.last
            is NavState.GpsLost -> state.last
            else -> null
        } ?: return null
        return nav.next?.takeIf { it.maneuver != Maneuver.ARRIVE }?.location
    }

    /**
     * Engine events -> haptics, exactly as spec section 8 lists them: turn at 200 m = Tick, turn at 50 m = DoubleClick,
     * GPS lost = HeavyClick, arrival = DoubleClick (played by [arrive]). The spec has no row for off route, rerouted,
     * GPS recovered or lost route: those stay silent.
     */
    private fun onNavEvent(event: NavEvent) {
        WgoMetrics.event("nav event=${event.javaClass.simpleName}")
        when (event) {
            is NavEvent.ApproachingTurn -> _haptics.tryEmit(HapticEvent.Tick)
            is NavEvent.AtTurn -> _haptics.tryEmit(HapticEvent.DoubleClick)
            NavEvent.GpsLost -> _haptics.tryEmit(HapticEvent.HeavyClick)
            NavEvent.Arrived, NavEvent.OffRouteDetected, NavEvent.Rerouted, NavEvent.GpsRecovered -> Unit
        }
    }

    private fun arrive() {
        // Played here, not from the event: ending the trip cancels the event collector.
        _haptics.tryEmit(HapticEvent.DoubleClick)
        stopNavigation()
        _endConfirm.value = false
        _screen.value = ScreenState.Arrived
    }

    /** Ends the trip: engine, ticks and the location request stop now, and the saved destination is cleared. */
    private fun stopNavigation(clearTrip: Boolean = true) {
        val had = session != null
        session?.stop()
        session = null
        sessionJob?.cancel()
        fixJob?.cancel()
        sessionJob = null
        fixJob = null
        _radar.value = null
        _route.value = null
        stopLocation()
        if (clearTrip && had) viewModelScope.launch { tripStore.clearTrip() }
        if (had) WgoMetrics.event("nav stop snapshot=${WgoMetrics.snapshot()}")
    }

    /** Back or CANCEL while navigating: ask first (END NAVIGATION?). */
    fun requestEndNavigation() {
        if (_screen.value == ScreenState.Navigation) _endConfirm.value = true
    }

    /** KEEP (or Back on the confirm). */
    fun keepNavigating() {
        _endConfirm.value = false
    }

    /** END on the confirm, END on the lost-route surface, or CANCEL while still searching for GPS. */
    fun endNavigation() {
        _endConfirm.value = false
        stopNavigation()
        goHome()
    }

    /** FINALIZAR on the arrival surface (or Back there). */
    fun finishArrival() {
        _destinationName.value = ""
        goHome()
    }

    /** RETRY on the lost-route surface: a fresh series of reroutes. */
    fun retryReroute() {
        session?.retryReroute()
    }

    /** RETRY on the GPS-lost overlay: reopen the location request. */
    fun retryGps() {
        if (session != null && appStarted) startFixes()
    }

    /** Activity onStart: fixes resume (and the GPS-lost timer restarts from now). */
    fun onAppStarted() {
        appStarted = true
        val s = session ?: return
        s.resume()
        startFixes()
    }

    // ---- Location (lazy permission, ORIGIN one-shot, then NAVIGATING; IDLE everywhere else) ----

    /** Real source: fused when Play services is present, else the platform LocationManager. */
    val locationSource: LocationSource = LocationSources.create(application)

    val locationPermission: StateFlow<PermissionStatus> = permissions.status
        .map { it.getValue(WgoPermission.LOCATION) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, permissions.status.value.getValue(WgoPermission.LOCATION))

    private var locationJob: Job? = null

    init {
        // Test builds only: utterances injected through adb enter the pipeline exactly like a final transcript.
        if (BuildConfig.TEST_HOOKS) viewModelScope.launch {
            DebugUtterances.utterances.collect { u ->
                if (_screen.value !in INJECTABLE_SCREENS) return@collect
                cancelListening()
                _micDenied.value = false
                WgoMetrics.event("voice_strategy=Injected")
                onTranscript(u.text, u.language)
            }
        }
        // A permission granted again outside the dialog (system settings) arrives through the broker's refresh on
        // start: reopen the trip's location request if a trip is running without one.
        viewModelScope.launch {
            locationPermission.collect { status ->
                if (status == PermissionStatus.Granted && session != null && appStarted && fixJob?.isActive != true) {
                    locationSource.setProfile(LocationProfile.NAVIGATING)
                    startFixes()
                }
            }
        }
    }

    /** Retry chip of the location-denied surface (permission revoked mid-trip): ask again, then resume the trip. */
    fun retryLocation() {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            if (permissions.ensure(WgoPermission.LOCATION) != PermissionStatus.Granted) return@launch
            if (session != null && appStarted) {
                locationSource.setProfile(LocationProfile.NAVIGATING)
                startFixes()
            }
        }
    }

    private fun stopLocation() {
        locationJob?.cancel()
        locationSource.setProfile(LocationProfile.IDLE)
        locationSource.setNavigationHint(null, false)
    }

    /** Debug builds only: force a location profile (to measure AMBIENT, which has no ambient-mode trigger yet). */
    fun debugSetLocationProfile(profile: LocationProfile) {
        if (BuildConfig.DEBUG) locationSource.setProfile(profile)
    }

    override fun onCleared() {
        session?.stop()
        locationSource.setProfile(LocationProfile.IDLE)
    }

    fun openSettings() {
        _screen.value = ScreenState.Settings
    }

    fun openMapPicker() {
        _screen.value = ScreenState.MapPicker
    }

    /** Back from the map picker to Settings (the style is unchanged unless USAR committed it). */
    fun backToSettings() {
        _screen.value = ScreenState.Settings
    }

    private companion object {
        const val ORIGIN_TIMEOUT_MS = 8_000L
        const val PLACE_RESULTS = 3

        /** A straight run this long ahead lets the NAVIGATING request slow to 3 s above 20 km/h. */
        const val LONG_STRAIGHT_M = 300.0

        /** Debug injections are accepted where a new request makes sense (never mid-navigation or in settings). */
        val INJECTABLE_SCREENS = setOf(
            ScreenState.Listening, ScreenState.Working, ScreenState.ConfirmPlace, ScreenState.ModeSelection, ScreenState.Error
        )

        /** Process-wide: true until the first AppViewModel of this process consumes it. */
        @Volatile
        private var coldStart = true

        @Synchronized
        fun consumeColdStart(): Boolean = coldStart.also { coldStart = false }
    }
}

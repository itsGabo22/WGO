package com.wgo.navigator

import android.app.Application
import android.os.SystemClock
import com.wgo.navigator.nav.NavState
import com.wgo.navigator.service.NavigationHost
import com.wgo.navigator.service.TripEnd
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
import com.wgo.navigator.core.VoiceEvent
import com.wgo.navigator.core.WgoFailure
import com.wgo.navigator.metrics.WgoMetrics
import com.wgo.navigator.ui.components.MicState
import com.wgo.navigator.voice.DebugUtterances
import com.wgo.navigator.voice.SpeakNowCue
import com.wgo.navigator.voice.SystemVoiceDialog
import com.wgo.navigator.voice.VoiceInputChain
import com.wgo.navigator.voice.VoiceLevel
import com.wgo.navigator.voice.VoiceStrategy
import com.wgo.navigator.ai.TieredInterpreter
import com.wgo.navigator.core.IntentKind
import com.wgo.navigator.core.Place
import com.wgo.navigator.core.Route
import com.wgo.navigator.geo.NominatimGeocoder
import com.wgo.navigator.geo.OnlinePlaceSearch
import com.wgo.navigator.geo.PlaceRequest
import com.wgo.navigator.geo.PlaceResolver
import com.wgo.navigator.geo.Resolution
import com.wgo.navigator.places.MyPlaces
import com.wgo.navigator.places.PlaceIndex
import com.wgo.navigator.net.HttpClient
import com.wgo.navigator.net.WgoUserAgent
import com.wgo.navigator.route.OsrmRouter
import com.wgo.navigator.ui.map.GeoCoord
import kotlinx.coroutines.Job
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import com.wgo.navigator.ui.screens.AmbientSnapshot
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
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

    /** The speak-now cue: a second Tick when the recognizer gets ready after a slow start (> 600 ms). */
    private val speakNow = SpeakNowCue { SystemClock.elapsedRealtime() }

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

    /**
     * Mic pressed (pointer-down on the orb, its click, a retry): the preparing state and the "starts listening" Tick
     * come at once, in the same frame as the press; the live listening state follows when the recognizer is ready
     * (a cold recognizer process takes about 4 s). Repeated calls during a session are ignored.
     */
    fun onMicTapped() {
        if (_micState.value != MicState.Idle || listenJob?.isActive == true) return
        _transcript.value = ""
        speakNow.onPress()
        WgoMetrics.event("voice_press at=${SystemClock.elapsedRealtime()}")
        _micState.value = MicState.Preparing
        _haptics.tryEmit(HapticEvent.Tick) // spec section 8: starts listening (the press is the start)
        listenJob = viewModelScope.launch {
            // Let the preparing frame draw first: the recognizer setup below runs on the main thread. The first frame
            // callback can run before Compose draws the new state, so wait for the next one too (~16-32 ms).
            awaitFrame()
            awaitFrame()
            // RECORD_AUDIO is asked lazily, and only for the in-app recognizer (the system dialog records by itself).
            if (voice.strategy() == VoiceStrategy.InApp) {
                val status = permissions.ensure(WgoPermission.MICROPHONE)
                if (status != PermissionStatus.Granted) {
                    _micState.value = MicState.Idle
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
                // A session that ended without any event never leaves the preparing state hanging.
                if (_micState.value == MicState.Preparing) _micState.value = MicState.Idle
            }
        }
    }

    private fun onVoiceEvent(event: VoiceEvent) {
        when (event) {
            // (The Tick already came with the press.)
            VoiceEvent.Ready -> {
                val preparedMs = speakNow.preparingMs()
                val cue = speakNow.onReady()
                _micState.value = MicState.Listening
                if (cue) _haptics.tryEmit(HapticEvent.Tick) // spec section 8: mic ready after a slow start
                // The offline places index loads while the user speaks (once per process). Started at ready, not at the
                // press: on the watch the load competed with the recognizer's cold start (P7, measured).
                viewModelScope.launch { placeResolver.prewarm() }
                WgoMetrics.event("voice_listening ms_from_press=$preparedMs speak_now_tick=$cue")
            }
            VoiceEvent.Restarting -> {
                // The ready was false (the recognizer is being recreated): back to preparing, cue armed again.
                _micState.value = MicState.Preparing
                voiceLevel.value = 0f
                speakNow.onPress()
                WgoMetrics.event("voice_back_to_preparing")
            }
            VoiceEvent.AwaitingSystem -> _micState.value = MicState.Waiting
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
                    WgoFailure.NotStarted -> fail(PipelineError.MicStart, Step.Listen)
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

    /** My places, then the offline index (loaded on the first search), then Nominatim (docs/PLACE_SEARCH.md). */
    private val placeResolver = PlaceResolver(
        index = PlaceIndex.fromAssets(application),
        online = OnlinePlaceSearch { q, center, bounded -> geocoder.once(q, center, pipelineLanguage, ONLINE_RESULTS, bounded) },
        privatePlaces = MyPlaces.fromAssets(application)
    )
    private val router = OsrmRouter(HttpClient(WgoUserAgent.identifying))

    private var pipelineJob: Job? = null
    private var pipelineLanguage = Language.ES
    private var placeRequest: PlaceRequest? = null
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
                Step.Resolve -> placeRequest?.let { resolve(it) } ?: fail(PipelineError.NotUnderstood, Step.Listen)
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
                // After "DIME LA CALLE" the new words locate the name that was not found (they only re-rank).
                val retryName = hintRetryName
                hintRetryName = null
                hintRetryWords = if (retryName != null) listOfNotNull(query, result.placeHint).joinToString(" ") else null
                val request = if (retryName != null) PlaceRequest(retryName, hintRetryWords)
                else PlaceRequest(query, result.placeHint, result.placeCategory, fromAlias = result.placeFromAlias)
                placeRequest = request
                modeSpoken = result.mode != null
                _selectedMode.value = result.mode
                resolve(request)
            }
            IntentKind.Cancel, IntentKind.Repeat -> goHome()
            IntentKind.ChangeMode, IntentKind.Unknown ->
                fail(if (interpreter.lastRemoteFailed) PipelineError.AiUnavailable else PipelineError.NotUnderstood, Step.Listen)
        }
    }

    private suspend fun resolve(request: PlaceRequest) {
        _notFound.value = null
        _stage.value = WorkStage.Resolving
        _screen.value = ScreenState.Working
        val near = locationSource.lastKnown()?.let { GeoCoord(it.lat, it.lon) }
        _near.value = near
        val start = System.nanoTime()
        val found = placeResolver.resolve(request, near, pipelineLanguage)
        WgoMetrics.event(
            "places latency_ms=${(System.nanoTime() - start) / 1_000_000} requests=${placeResolver.lastRequests} " +
                "result=${found::class.simpleName}"
        )
        when (found) {
            is Resolution.Found -> {
                _places.value = found.places
                _placeIndex.value = 0
                _screen.value = ScreenState.ConfirmPlace
                _haptics.tryEmit(HapticEvent.Click) // spec section 8: destination understood
            }
            is Resolution.NotFound -> {
                // A street / neighbourhood retry that still found nothing: search the words themselves.
                val street = hintRetryWords
                hintRetryWords = null
                if (street != null) return resolve(PlaceRequest(street))
                _notFound.value = NotFoundInfo(request.name, found.suggestions)
                fail(PipelineError.PlaceNotFound, Step.Resolve)
            }
            // A server error keeps the "no internet" message it always had; only a read timeout is "too slow".
            is Resolution.Failed -> fail(PipelineError.forNetwork(found.failure, PipelineError.Offline), Step.Resolve)
        }
    }

    /** What the place-not-found surface shows (null for every other error). */
    data class NotFoundInfo(val name: String, val suggestions: List<Place>)

    private val _notFound = MutableStateFlow<NotFoundInfo?>(null)
    val notFound: StateFlow<NotFoundInfo?> = _notFound.asStateFlow()

    /** Set by "DIME LA CALLE": the name the next utterance locates. */
    private var hintRetryName: String? = null

    /** The retry's own words, searched as a place when the name still has no hit. */
    private var hintRetryWords: String? = null

    /** The suggestion pill: Confirm place with the one or two partial matches (OTRO cycles them, no request). */
    fun onNotFoundSuggestion() {
        val s = _notFound.value?.suggestions.orEmpty()
        if (s.isEmpty()) return
        _places.value = s
        _placeIndex.value = 0
        _error.value = null
        _screen.value = ScreenState.ConfirmPlace
        _haptics.tryEmit(HapticEvent.Click) // spec section 8: destination understood
    }

    /** "DIME LA CALLE": listen again; the words become the hint for the name that was not found. */
    fun onSayStreet() {
        hintRetryName = _notFound.value?.name
        runStep(Step.Listen)
    }

    /** OTHER: the next of the (up to two) results, no request. */
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
        // API 33+: the trip's notification (ongoing activity) needs POST_NOTIFICATIONS. Asked once, the first time a
        // route is requested; whatever the answer, the trip runs (without it the notification is only hidden).
        if (permissions.status.value[WgoPermission.NOTIFICATIONS] == PermissionStatus.NotAsked) {
            WgoMetrics.event("notifications=${permissions.ensure(WgoPermission.NOTIFICATIONS)}")
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
            PipelineError.PlaceNotFound, PipelineError.AiUnavailable, PipelineError.MicStart, null -> runStep(Step.Listen)
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
        _notFound.value = null
        hintRetryName = null
        hintRetryWords = null
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
        // A running trip goes on in the navigation service; without the service it pauses until the app is back.
        host.setVisible(false)
    }

    fun selectMode(mode: TravelMode) {
        _selectedMode.value = mode
        _haptics.tryEmit(HapticEvent.HeavyClick) // spec section 8: mode chosen
        runStep(Step.Route)
    }

    /** Back from settings (and any other screen with nothing to confirm): ends a trip if one runs, then home. */
    fun backToListening() {
        host.end(TripEnd.Ended)
        host.acknowledgeEnd()
        goHome()
    }

    // ---- Live navigation: the trip lives in the process-wide NavigationHost (kept alive by the foreground navigation
    // service); this ViewModel only observes it and sends commands, whether the Activity is visible or not ----

    private val host = NavigationHost.get(application)

    val navState: StateFlow<NavState> = host.navState

    /** The radar's data for the current trip (null when there is no trip). */
    val radar: StateFlow<LiveRadarSource?> = host.radar

    /** Name of the trip's destination (kept for the arrival surface after the trip ended). */
    val destinationName: StateFlow<String> = host.destinationName

    /** Distance and duration of the trip that just arrived (memory only; the arrival surface's summary line). */
    val tripSummary: StateFlow<com.wgo.navigator.service.TripSummary?> = host.tripSummary

    /** True while the END NAVIGATION? confirm covers the navigation screen. */
    private val _endConfirm = MutableStateFlow(false)
    val endConfirm: StateFlow<Boolean> = _endConfirm.asStateFlow()

    init {
        // A reopened Activity (new ViewModel, same process) reconnects to the live trip, or to its unread arrival.
        when {
            host.active.value -> _screen.value = ScreenState.Navigation
            host.lastEnd.value == TripEnd.Arrived -> _screen.value = ScreenState.Arrived
        }
        // Trip haptics (turns, GPS lost, arrival) are played by the host, never here: one collector, screen on or off.
        // Only ends that happen from now on (the current value was handled above).
        viewModelScope.launch { host.lastEnd.drop(1).collect { it?.let(::onTripEnded) } }
    }

    private fun startNavigation(route: Route, place: Place, origin: Fix) {
        _endConfirm.value = false
        host.start(route, place, origin)
        _screen.value = ScreenState.Navigation
    }

    /** The trip ended inside the host (arrival, service lost, task removed); END from the UI goes home by itself. */
    private fun onTripEnded(reason: TripEnd) {
        _endConfirm.value = false
        when (reason) {
            TripEnd.Arrived -> if (_screen.value == ScreenState.Navigation) _screen.value = ScreenState.Arrived
            TripEnd.ServiceLost, TripEnd.TaskRemoved -> if (_screen.value == ScreenState.Navigation) {
                host.acknowledgeEnd()
                goHome()
            }
            TripEnd.Ended -> Unit
        }
    }

    // ---- Ambient (always-on) ----

    private val ambientTick = MutableStateFlow(0)

    /**
     * What the ambient navigation screen shows: the trip state, refreshed only on the ambient tick or when the maneuver
     * or the guidance state changes (AmbientSnapshot), so fixes do not redraw the low-power screen.
     */
    // (No flow operators inside `let {}`: that crashes Compose's lint flow-operator detector on Kotlin 2.1 metadata.)
    private val ambientSnapshot = AmbientSnapshot(host.navState.value)
    val ambientNavState: StateFlow<NavState> =
        combine(host.navState, ambientTick) { state, tick -> ambientSnapshot.update(state, tick); ambientSnapshot.shown }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), ambientSnapshot.shown)

    /**
     * The system entered or left ambient. The trip (if any) keeps running in the host with the slower screen-off
     * location policy; a voice session never stays open in ambient (same rule as the app going to the background).
     */
    fun onAmbientChanged(ambient: Boolean) {
        host.setAmbient(ambient)
        if (ambient && sessionActive && _micState.value != MicState.Waiting) cancelListening()
        WgoMetrics.event("ambient=$ambient screen=${_screen.value}")
    }

    /** The system's ambient update (about once a minute). */
    fun onAmbientTick() {
        ambientTick.value++
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
        host.end(TripEnd.Ended)
        host.acknowledgeEnd()
        goHome()
    }

    /** FINALIZAR on the arrival surface (or Back there). */
    fun finishArrival() {
        host.acknowledgeEnd()
        goHome()
    }

    /** RETRY on the lost-route surface: a fresh series of reroutes. */
    fun retryReroute() {
        host.retryReroute()
    }

    /** RETRY on the GPS-lost overlay: reopen the location request. */
    fun retryGps() {
        host.retryGps()
    }

    /** Activity onStart: a trip without its service resumes its fixes (and the GPS-lost timer restarts from now). */
    fun onAppStarted() {
        host.setVisible(true)
    }

    // ---- Location (lazy permission, ORIGIN one-shot, then NAVIGATING in the host; IDLE everywhere else) ----

    /** The process's only location source (owned by the host): fused when Play services is present, else platform. */
    val locationSource: LocationSource = host.locationSource

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
                if (status == PermissionStatus.Granted && host.active.value) host.resumeFixes()
            }
        }
    }

    /** Retry chip of the location-denied surface (permission revoked mid-trip): ask again, then resume the trip. */
    fun retryLocation() {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            if (permissions.ensure(WgoPermission.LOCATION) != PermissionStatus.Granted) return@launch
            if (host.active.value) host.resumeFixes()
        }
    }
    /** Debug builds only: force a location profile (to measure AMBIENT, which has no ambient-mode trigger yet). */
    fun debugSetLocationProfile(profile: LocationProfile) {
        if (BuildConfig.DEBUG) locationSource.setProfile(profile)
    }

    override fun onCleared() {
        // A running trip belongs to the host and its service, not to this screen: it goes on (a reopened Activity
        // reconnects). Without a trip, make sure no ORIGIN request is left behind.
        if (!host.active.value) locationSource.setProfile(LocationProfile.IDLE)
    }

    fun openSettings() {
        _screen.value = ScreenState.Settings
    }

    fun openMapPicker() {
        _screen.value = ScreenState.MapPicker
    }

    fun openVoicePicker() {
        _screen.value = ScreenState.VoicePicker
    }

    fun openCredits() {
        _screen.value = ScreenState.Credits
    }

    /** Back from the map picker to Settings (the style is unchanged unless USAR committed it). */
    fun backToSettings() {
        _screen.value = ScreenState.Settings
    }

    private companion object {
        const val ORIGIN_TIMEOUT_MS = 8_000L
        /** Hits asked from Nominatim per request; the resolver keeps at most two candidates. */
        const val ONLINE_RESULTS = 3

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

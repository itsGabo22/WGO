package com.wgo.navigator

import android.app.Application
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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

    /** Debug builds only: replays the intro in a warm process so it can be measured without cold-start noise. */
    fun replayIntroForDebug() {
        if (!BuildConfig.DEBUG) return
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

    /** Why the last voice session failed (shown on screen 01); null when there is nothing to report. */
    private val _voiceFailure = MutableStateFlow<WgoFailure?>(null)
    val voiceFailure: StateFlow<WgoFailure?> = _voiceFailure.asStateFlow()

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
        _voiceFailure.value = null
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
                    else -> _voiceFailure.value = event.failure
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
        _transcript.value = text
        _micState.value = MicState.Processing
        listenJob = viewModelScope.launch {
            delay(PROCESSING_MS)
            _micState.value = MicState.Idle
            _screen.value = ScreenState.ModeSelection
            _haptics.tryEmit(HapticEvent.Click) // destination understood
        }
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
    }

    fun selectMode(mode: TravelMode) {
        _selectedMode.value = mode
        _haptics.tryEmit(HapticEvent.HeavyClick)
        _screen.value = ScreenState.Navigation
        startLocation()
    }

    /** Back/cancel from mode selection or navigation. */
    fun backToListening() {
        stopLocation()
        _selectedMode.value = null
        _screen.value = ScreenState.Listening
    }

    // ---- Location (lazy permission, ORIGIN one-shot, then NAVIGATING; IDLE everywhere else) ----

    /** Real source: fused when Play services is present, else the platform LocationManager. */
    val locationSource: LocationSource = LocationSources.create(application)

    val locationPermission: StateFlow<PermissionStatus> = permissions.status
        .map { it.getValue(WgoPermission.LOCATION) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, permissions.status.value.getValue(WgoPermission.LOCATION))

    /** The one-shot ORIGIN fix of the current trip (null until it arrives). */
    private val _origin = MutableStateFlow<Fix?>(null)
    val origin: StateFlow<Fix?> = _origin.asStateFlow()

    private var locationJob: Job? = null

    /** True once the current trip reached NAVIGATING (cleared when leaving navigation). */
    private var navigatingStarted = false

    init {
        // Debug builds only: utterances injected through adb enter the pipeline exactly like a final transcript.
        if (BuildConfig.DEBUG) viewModelScope.launch {
            DebugUtterances.utterances.collect { u ->
                if (_screen.value != ScreenState.Listening) return@collect
                cancelListening()
                _micDenied.value = false
                _voiceFailure.value = null
                WgoMetrics.event("voice_strategy=Injected")
                onTranscript(u.text, u.language)
            }
        }
        // A permission granted outside the dialog (system settings after "don't ask again") arrives through the
        // broker's refresh on start: restart the trip's location if navigation is showing and never got going.
        viewModelScope.launch {
            locationPermission.collect { status ->
                val idleTrip = !navigatingStarted && locationJob?.isActive != true
                if (status == PermissionStatus.Granted && _screen.value == ScreenState.Navigation && idleTrip) startLocation()
            }
        }
    }

    /**
     * The user committed to a trip: ask for location the first time (system dialog, never during the intro), take one
     * ORIGIN fix, then keep NAVIGATING updates while the navigation screen collects them.
     */
    private fun startLocation() {
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            if (permissions.ensure(WgoPermission.LOCATION) != PermissionStatus.Granted) return@launch
            locationSource.setProfile(LocationProfile.ORIGIN)
            val origin = locationSource.once(ORIGIN_TIMEOUT_MS)
            if (origin is Outcome.Ok) _origin.value = origin.value
            if (_screen.value == ScreenState.Navigation) {
                locationSource.setProfile(LocationProfile.NAVIGATING)
                navigatingStarted = true
            }
        }
    }

    /** Retry chip of the denied surface. */
    fun retryLocation() = startLocation()

    private fun stopLocation() {
        locationJob?.cancel()
        navigatingStarted = false
        locationSource.setProfile(LocationProfile.IDLE)
        _origin.value = null
    }

    /** Debug builds only: force a location profile (to measure AMBIENT, which has no ambient-mode trigger yet). */
    fun debugSetLocationProfile(profile: LocationProfile) {
        if (BuildConfig.DEBUG) locationSource.setProfile(profile)
    }

    override fun onCleared() {
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
        const val PROCESSING_MS = 800L
        const val ORIGIN_TIMEOUT_MS = 8_000L

        /** Process-wide: true until the first AppViewModel of this process consumes it. */
        @Volatile
        private var coldStart = true

        @Synchronized
        fun consumeColdStart(): Boolean = coldStart.also { coldStart = false }
    }
}

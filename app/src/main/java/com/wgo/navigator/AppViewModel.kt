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
import com.wgo.navigator.ui.components.MicState
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
 * Speech recognition is still simulated: a tap listens, then "routes", and after the same 2 s as before
 * the destination is understood and the mode selection opens.
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

    fun onMicTapped() {
        if (_micState.value != MicState.Idle) return
        _micState.value = MicState.Listening
        _haptics.tryEmit(HapticEvent.Tick)
        listenJob = viewModelScope.launch {
            delay(LISTENING_MS)
            _micState.value = MicState.Processing
            delay(PROCESSING_MS)
            _micState.value = MicState.Idle
            _screen.value = ScreenState.ModeSelection
            _haptics.tryEmit(HapticEvent.Click) // destination understood
        }
    }

    fun cancelListening() {
        listenJob?.cancel()
        _micState.value = MicState.Idle
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

    private val permissions = PermissionBroker.get(application)

    /** Real source: fused when Play services is present, else the platform LocationManager. */
    val locationSource: LocationSource = LocationSources.create(application)

    val locationPermission: StateFlow<PermissionStatus> = permissions.status
        .map { it.getValue(WgoPermission.LOCATION) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, permissions.status.value.getValue(WgoPermission.LOCATION))

    /** The one-shot ORIGIN fix of the current trip (null until it arrives). */
    private val _origin = MutableStateFlow<Fix?>(null)
    val origin: StateFlow<Fix?> = _origin.asStateFlow()

    private var locationJob: Job? = null

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
            if (_screen.value == ScreenState.Navigation) locationSource.setProfile(LocationProfile.NAVIGATING)
        }
    }

    /** Retry chip of the denied surface. */
    fun retryLocation() = startLocation()

    private fun stopLocation() {
        locationJob?.cancel()
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
        const val LISTENING_MS = 1200L
        const val PROCESSING_MS = 800L // listening + processing = the previous 2000 ms
        const val ORIGIN_TIMEOUT_MS = 8_000L

        /** Process-wide: true until the first AppViewModel of this process consumes it. */
        @Volatile
        private var coldStart = true

        @Synchronized
        fun consumeColdStart(): Boolean = coldStart.also { coldStart = false }
    }
}

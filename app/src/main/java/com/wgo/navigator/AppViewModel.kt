package com.wgo.navigator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wgo.navigator.haptics.HapticEvent
import com.wgo.navigator.haptics.HapticSource
import com.wgo.navigator.ui.components.MicState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class TravelMode { Walk, Drive }

/**
 * Owns the screen state machine and the haptic events tied to its state changes.
 * Speech recognition is still simulated: a tap listens, then "routes", and after the same 2 s as before
 * the destination is understood and the mode selection opens.
 */
class AppViewModel : ViewModel(), HapticSource {
    private val _screen = MutableStateFlow(ScreenState.Listening)
    val screen: StateFlow<ScreenState> = _screen.asStateFlow()

    private val _micState = MutableStateFlow(MicState.Idle)
    val micState: StateFlow<MicState> = _micState.asStateFlow()

    private val _selectedMode = MutableStateFlow<TravelMode?>(null)
    val selectedMode: StateFlow<TravelMode?> = _selectedMode.asStateFlow()

    private val _haptics = MutableSharedFlow<HapticEvent>(extraBufferCapacity = 8)
    override val haptics: SharedFlow<HapticEvent> = _haptics.asSharedFlow()

    private var listenJob: Job? = null

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
    }

    /** Back/cancel from mode selection or navigation. */
    fun backToListening() {
        _selectedMode.value = null
        _screen.value = ScreenState.Listening
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
    }
}

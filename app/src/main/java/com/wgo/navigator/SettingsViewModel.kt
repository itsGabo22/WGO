package com.wgo.navigator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.wgo.navigator.haptics.HapticEvent
import com.wgo.navigator.haptics.HapticSource
import com.wgo.navigator.ui.theme.MapStyleId
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class Language {
    ES, EN
}

/**
 * Holds the settings as StateFlows seeded with [initial] (already loaded before the first frame)
 * and writes every change through [repository].
 */
class SettingsViewModel(
    private val repository: SettingsRepository,
    initial: WgoSettings
) : ViewModel(), HapticSource {
    private val _haptics = MutableSharedFlow<HapticEvent>(extraBufferCapacity = 4)
    override val haptics: SharedFlow<HapticEvent> = _haptics.asSharedFlow()

    private val _isDarkMode = MutableStateFlow(initial.isDarkMode)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _language = MutableStateFlow(initial.language)
    val language: StateFlow<Language> = _language.asStateFlow()

    private val _mapStyle = MutableStateFlow(initial.mapStyle)
    val mapStyle: StateFlow<MapStyleId> = _mapStyle.asStateFlow()

    fun toggleTheme() {
        val value = !_isDarkMode.value
        _isDarkMode.value = value
        viewModelScope.launch { repository.setDarkMode(value) }
    }

    fun setLanguage(lang: Language) {
        _language.value = lang
        viewModelScope.launch { repository.setLanguage(lang) }
    }

    /** ES <-> EN, applied immediately and persisted. */
    fun toggleLanguage() {
        setLanguage(if (_language.value == Language.ES) Language.EN else Language.ES)
        _haptics.tryEmit(HapticEvent.Click)
    }

    /** One Tick per style step in the map picker. */
    fun onMapPickerStep() {
        _haptics.tryEmit(HapticEvent.Tick)
    }

    /** USAR in the map picker: persist the style and confirm with a Click. */
    fun commitMapStyle(style: MapStyleId) {
        setMapStyle(style)
        _haptics.tryEmit(HapticEvent.Click)
    }

    fun setMapStyle(style: MapStyleId) {
        _mapStyle.value = style
        viewModelScope.launch { repository.setMapStyle(style) }
    }

    // ---- spoken guidance (spec section 8, P7) ----

    private val _voiceMode = MutableStateFlow(initial.voiceMode)
    val voiceMode: StateFlow<VoiceMode> = _voiceMode.asStateFlow()

    private val _voiceName = MutableStateFlow(initial.voiceName)
    val voiceName: StateFlow<String?> = _voiceName.asStateFlow()

    /** The voice guidance row: OFF -> AUTO -> ALWAYS -> OFF, persisted at once (silent, like the theme toggle). */
    fun cycleVoiceMode() {
        val next = when (_voiceMode.value) {
            VoiceMode.Off -> VoiceMode.Auto
            VoiceMode.Auto -> VoiceMode.Always
            VoiceMode.Always -> VoiceMode.Off
        }
        _voiceMode.value = next
        viewModelScope.launch { repository.setVoiceMode(next) }
    }

    private val _ttsEngine = MutableStateFlow(initial.ttsEngine)

    /** The chosen voice's engine (package); null = the system's default engine. */
    val ttsEngine: StateFlow<String?> = _ttsEngine.asStateFlow()

    private val _voicePitch = MutableStateFlow(initial.voicePitch)
    val voicePitch: StateFlow<com.wgo.navigator.voice.VoicePitch> = _voicePitch.asStateFlow()

    private val _voiceRate = MutableStateFlow(initial.voiceRate)
    val voiceRate: StateFlow<com.wgo.navigator.voice.VoiceRate> = _voiceRate.asStateFlow()

    /** The "Tono" row: LOW -> NORMAL -> HIGH, persisted at once (silent). */
    fun cycleVoicePitch() {
        val next = _voicePitch.value.next()
        _voicePitch.value = next
        viewModelScope.launch { repository.setVoicePitch(next) }
    }

    /** The "Velocidad" row: SLOW -> NORMAL -> FAST, persisted at once (silent). */
    fun cycleVoiceRate() {
        val next = _voiceRate.value.next()
        _voiceRate.value = next
        viewModelScope.launch { repository.setVoiceRate(next) }
    }

    /** A voice chosen in the picker, with its engine (null name = automatic for that engine). */
    fun setVoice(name: String?, engine: String?) {
        _voiceName.value = name
        _ttsEngine.value = engine
        viewModelScope.launch { repository.setVoice(name, engine) }
    }

    class Factory(
        private val repository: SettingsRepository,
        private val initial: WgoSettings
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(repository, initial) as T
    }
}

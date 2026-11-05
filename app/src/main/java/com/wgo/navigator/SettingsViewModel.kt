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

    fun setMapStyle(style: MapStyleId) {
        _mapStyle.value = style
        viewModelScope.launch { repository.setMapStyle(style) }
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

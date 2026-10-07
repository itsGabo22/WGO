package com.wgo.navigator

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Language {
    ES, EN
}

class SettingsViewModel : ViewModel() {
    private val _isDarkMode = MutableStateFlow(false)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _language = MutableStateFlow(Language.ES)
    val language: StateFlow<Language> = _language.asStateFlow()

    fun toggleTheme() {
        _isDarkMode.value = !_isDarkMode.value
    }

    fun setLanguage(lang: Language) {
        _language.value = lang
    }
}

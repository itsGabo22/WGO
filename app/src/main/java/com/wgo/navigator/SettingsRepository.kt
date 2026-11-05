package com.wgo.navigator

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wgo.navigator.ui.theme.MapStyleId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class WgoSettings(
    val isDarkMode: Boolean = false,
    val language: Language = Language.ES,
    val mapStyle: MapStyleId = MapStyleId.COBALTO
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "wgo_settings")

/** Persists user settings. Defaults: Daylight, ES, COBALTO. */
class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsDataStore

    val settings: Flow<WgoSettings> = store.data.map { prefs ->
        WgoSettings(
            isDarkMode = prefs[DARK_MODE] ?: false,
            language = prefs[LANGUAGE].toEnumOrDefault(Language.ES),
            mapStyle = prefs[MAP_STYLE].toEnumOrDefault(MapStyleId.COBALTO)
        )
    }

    /** One-shot read, meant to be called (blocking) before the first frame is drawn. */
    suspend fun load(): WgoSettings = settings.first()

    suspend fun setDarkMode(value: Boolean) {
        store.edit { it[DARK_MODE] = value }
    }

    suspend fun setLanguage(value: Language) {
        store.edit { it[LANGUAGE] = value.name }
    }

    suspend fun setMapStyle(value: MapStyleId) {
        store.edit { it[MAP_STYLE] = value.name }
    }

    private companion object {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val MAP_STYLE = stringPreferencesKey("map_style")
    }
}

private inline fun <reified E : Enum<E>> String?.toEnumOrDefault(default: E): E =
    enumValues<E>().firstOrNull { it.name == this } ?: default

package com.wgo.navigator

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wgo.navigator.ui.theme.MapStyleId
import com.wgo.navigator.voice.VoicePitch
import com.wgo.navigator.voice.VoiceRate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Spoken guidance (spec section 8, P7): AUTO speaks only with a headset or Bluetooth audio output. */
enum class VoiceMode { Off, Auto, Always }

data class WgoSettings(
    val isDarkMode: Boolean = false,
    val language: Language = Language.ES,
    val mapStyle: MapStyleId = MapStyleId.COBALTO,
    val voiceMode: VoiceMode = VoiceMode.Auto,
    /** The text-to-speech voice the user picked (engine voice name); null = the automatic choice. */
    val voiceName: String? = null,
    /** The engine of that voice (package, P8); null = the system's default engine. */
    val ttsEngine: String? = null,
    /** Pitch and speed of every phrase (P8). */
    val voicePitch: VoicePitch = VoicePitch.Normal,
    val voiceRate: VoiceRate = VoiceRate.Normal
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "wgo_settings")

/** Persists user settings. Defaults: Daylight, ES, COBALTO, voice guidance AUTO with the automatic voice. */
class SettingsRepository(context: Context) {
    private val store = context.applicationContext.settingsDataStore

    val settings: Flow<WgoSettings> = store.data.map { prefs ->
        WgoSettings(
            isDarkMode = prefs[DARK_MODE] ?: false,
            language = prefs[LANGUAGE].toEnumOrDefault(Language.ES),
            mapStyle = prefs[MAP_STYLE].toEnumOrDefault(MapStyleId.COBALTO),
            voiceMode = prefs[VOICE_MODE].toEnumOrDefault(VoiceMode.Auto),
            voiceName = prefs[VOICE_NAME],
            ttsEngine = prefs[TTS_ENGINE],
            voicePitch = prefs[VOICE_PITCH].toEnumOrDefault(VoicePitch.Normal),
            voiceRate = prefs[VOICE_RATE].toEnumOrDefault(VoiceRate.Normal)
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

    suspend fun setVoiceMode(value: VoiceMode) {
        store.edit { it[VOICE_MODE] = value.name }
    }

    suspend fun setVoicePitch(value: VoicePitch) {
        store.edit { it[VOICE_PITCH] = value.name }
    }

    suspend fun setVoiceRate(value: VoiceRate) {
        store.edit { it[VOICE_RATE] = value.name }
    }

    /** The voice and its engine together (null voice = automatic for [engine]; null engine = the default one). */
    suspend fun setVoice(name: String?, engine: String?) {
        store.edit {
            if (name == null) it.remove(VOICE_NAME) else it[VOICE_NAME] = name
            if (engine == null) it.remove(TTS_ENGINE) else it[TTS_ENGINE] = engine
        }
    }

    /**
     * The active destination and mode (the language is already persisted above). Written when navigation starts and
     * cleared when it ends; never read at launch, so nothing auto-resumes.
     */
    suspend fun saveTrip(name: String, lat: Double, lon: Double, mode: TravelMode) {
        store.edit {
            it[TRIP_NAME] = name
            it[TRIP_LAT] = lat
            it[TRIP_LON] = lon
            it[TRIP_MODE] = mode.name
        }
    }

    suspend fun clearTrip() {
        store.edit {
            it.remove(TRIP_NAME)
            it.remove(TRIP_LAT)
            it.remove(TRIP_LON)
            it.remove(TRIP_MODE)
        }
    }

    private companion object {
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val LANGUAGE = stringPreferencesKey("language")
        val MAP_STYLE = stringPreferencesKey("map_style")
        val VOICE_MODE = stringPreferencesKey("voice_mode")
        val VOICE_NAME = stringPreferencesKey("voice_name")
        val TTS_ENGINE = stringPreferencesKey("tts_engine")
        val VOICE_PITCH = stringPreferencesKey("voice_pitch")
        val VOICE_RATE = stringPreferencesKey("voice_rate")
        val TRIP_NAME = stringPreferencesKey("trip_name")
        val TRIP_LAT = doublePreferencesKey("trip_lat")
        val TRIP_LON = doublePreferencesKey("trip_lon")
        val TRIP_MODE = stringPreferencesKey("trip_mode")
    }
}

private inline fun <reified E : Enum<E>> String?.toEnumOrDefault(default: E): E =
    enumValues<E>().firstOrNull { it.name == this } ?: default

package com.wgo.navigator.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.wgo.navigator.Language
import com.wgo.navigator.metrics.WgoMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/** An installed text-to-speech voice, as the chooser and the picker see it. */
data class VoiceInfo(val name: String, val languageTag: String, val network: Boolean, val installed: Boolean = true)

/** Picks the voice (spec section 8): the user's choice if installed, else the locale order, offline before network. */
object VoiceChooser {
    val ES_ORDER = listOf("es-CO", "es-419", "es-US", "es-MX", "es-ES")
    val EN_ORDER = listOf("en-US", "en-GB")

    /** The candidates for [language], best first (at most [limit]): locale order, then offline, then name. */
    fun ranked(voices: List<VoiceInfo>, language: Language, limit: Int = Int.MAX_VALUE): List<VoiceInfo> {
        val order = if (language == Language.ES) ES_ORDER else EN_ORDER
        val prefix = if (language == Language.ES) "es" else "en"
        fun rank(v: VoiceInfo): Int = order.indexOfFirst { v.languageTag.equals(it, ignoreCase = true) }.let { if (it < 0) order.size else it }
        return voices
            .filter { it.installed && it.languageTag.lowercase(Locale.ROOT).startsWith(prefix) }
            .sortedWith(compareBy<VoiceInfo> { rank(it) }.thenBy { it.network }.thenBy { it.name })
            .take(limit)
    }

    private val NAME_TAG = Regex("^([a-zA-Z]{2,3})[-_]([a-zA-Z]{2}|\\d{3})(?:[-_]|$)")

    /**
     * A "language-REGION" tag for a voice. Engines differ (measured on the Galaxy Watch5 Pro, 2026-11-11): Samsung TTS
     * reports ISO-3 locales without the region ("spa-x-lvariant-f00") but names its voices "es-US-SMTf00"; Google TTS
     * names them "es-us-x-sfb-local". The name's prefix wins; otherwise the locale, with an ISO-3 language mapped to
     * its 2-letter code.
     */
    fun tagOf(name: String, locale: Locale): String {
        NAME_TAG.find(name)?.let { m ->
            return iso2(m.groupValues[1].lowercase(Locale.ROOT)) + "-" + m.groupValues[2].uppercase(Locale.ROOT)
        }
        val lang = iso2(locale.language)
        return if (locale.country.isNullOrEmpty()) lang else "$lang-${locale.country}"
    }

    private fun iso2(code: String): String {
        if (code.length != 3) return code
        return Locale.getISOLanguages().firstOrNull { runCatching { Locale(it).isO3Language }.getOrNull() == code } ?: code
    }

    fun choose(voices: List<VoiceInfo>, language: Language, preferredName: String?): VoiceInfo? {
        val ranked = ranked(voices, language)
        return ranked.firstOrNull { it.name == preferredName } ?: ranked.firstOrNull()
    }
}

/**
 * One text-to-speech engine for a trip or for the voice picker (spec section 8, spoken guidance): created on demand,
 * shut down when its owner is done. Navigation-guidance audio attributes; transient audio focus with ducking, taken
 * for each phrase and given back when the queue is empty. No engine or no voice data: [available] stays false and
 * every call is a no-op (haptics carry the guidance).
 */
class SpeechEngine(context: Context, private val language: Language, private val preferredVoice: String?) {
    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .build()
    private val pending = AtomicInteger(0)
    private val startedAt = SystemClock.elapsedRealtime()
    private var ids = 0

    @Volatile
    var available: Boolean = false
        private set

    @Volatile
    var voices: List<VoiceInfo> = emptyList()
        private set

    @Volatile
    var voiceName: String? = null
        private set

    private val _ready = MutableStateFlow(false)

    /** True once the engine answered (with or without a usable voice). */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /** Phrases asked for before the engine was ready (the newest few; an urgent one replaces them). */
    private val early = ArrayList<Phrase>()
    private var onDrained: (() -> Unit)? = null

    private val tts: TextToSpeech = TextToSpeech(app) { status -> onInit(status) }

    private fun onInit(status: Int) {
        val ok = status == TextToSpeech.SUCCESS
        if (ok) {
            tts.setAudioAttributes(attributes)
            tts.setOnUtteranceProgressListener(listener)
            voices = runCatching { tts.voices.orEmpty().map(::info) }.getOrDefault(emptyList())
            // Debug builds: which voices the device really has (names and locales only).
            voices.forEach { WgoMetrics.event("tts voice name=${it.name} tag=${it.languageTag} net=${it.network} installed=${it.installed}") }
            val pick = VoiceChooser.choose(voices, language, preferredVoice)
            val voice = pick?.let { p -> tts.voices?.firstOrNull { it.name == p.name } }
            if (voice != null) tts.voice = voice
            else tts.language = if (language == Language.ES) Locale("es", "CO") else Locale.US
            voiceName = voice?.name
            available = voice != null || tts.isLanguageAvailable(tts.language ?: Locale.ROOT) >= TextToSpeech.LANG_AVAILABLE
        }
        WgoMetrics.event(
            "tts init ok=$ok available=$available voices=${voices.size} voice=${voiceName ?: "-"} " +
                "ms=${SystemClock.elapsedRealtime() - startedAt}"
        )
        _ready.value = true
        val queued = synchronized(early) { early.toList().also { early.clear() } }
        if (available) queued.forEach(::speak)
    }

    /** Switches to the installed voice [name] (the picker's choice). */
    fun useVoice(name: String) {
        if (!_ready.value) return
        tts.voices?.firstOrNull { it.name == name }?.let {
            tts.voice = it
            voiceName = it.name
        }
    }

    /** True while a headset or Bluetooth audio output is connected (the AUTO mode's condition). */
    fun headsetConnected(): Boolean = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in headsetTypes }

    fun speak(phrase: Phrase) {
        if (!available) {
            synchronized(early) {
                if (phrase.urgent) early.clear()
                if (early.size < 3) early += phrase
            }
            return
        }
        if (pending.get() == 0) audio.requestAudioFocus(focus)
        pending.incrementAndGet()
        val queue = if (phrase.urgent) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        if (phrase.urgent) pending.set(1)
        val id = "wgo-${ids++}"
        val result = tts.speak(phrase.text, queue, Bundle(), id)
        WgoMetrics.event("tts speak urgent=${phrase.urgent} chars=${phrase.text.length} result=$result")
        if (result != TextToSpeech.SUCCESS) done()
    }

    /** Lets the queue finish (at most [timeoutMs]), then shuts down: the arrival phrase is not cut. */
    fun shutdownAfterQueue(timeoutMs: Long, schedule: (Long, () -> Unit) -> Unit) {
        if (pending.get() == 0) return shutdown()
        onDrained = ::shutdown
        schedule(timeoutMs) { shutdown() }
    }

    fun shutdown() {
        onDrained = null
        runCatching { tts.stop() }
        runCatching { tts.shutdown() }
        audio.abandonAudioFocusRequest(focus)
        available = false
        WgoMetrics.event("tts shutdown")
    }

    private fun done() {
        if (pending.decrementAndGet() <= 0) {
            pending.set(0)
            audio.abandonAudioFocusRequest(focus)
            onDrained?.invoke()
        }
    }

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit
        override fun onDone(utteranceId: String?) = done()
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = done()
        override fun onError(utteranceId: String?, errorCode: Int) = done()
        override fun onStop(utteranceId: String?, interrupted: Boolean) = Unit
    }

    companion object {
        /** Wired, USB and Bluetooth headsets; BLE headsets (type 26) exist from API 31. */
        private val headsetTypes: Set<Int> = buildSet {
            add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
            add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
            add(AudioDeviceInfo.TYPE_USB_HEADSET)
            add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            add(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(TYPE_BLE_HEADSET)
        }
        private const val TYPE_BLE_HEADSET = 26

        fun info(v: Voice): VoiceInfo = VoiceInfo(
            name = v.name,
            languageTag = VoiceChooser.tagOf(v.name, v.locale),
            network = v.isNetworkConnectionRequired,
            installed = TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in v.features.orEmpty()
        )
    }
}

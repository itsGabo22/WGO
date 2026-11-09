package com.wgo.navigator.voice

import com.wgo.navigator.BuildConfig
import com.wgo.navigator.Language
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Debug builds only: utterances injected from adb (the debug-source-set receiver for
 * `<applicationId>.debug.UTTERANCE`), so the whole pipeline can be tested with no microphone. In release builds
 * [inject] is a no-op and nothing is ever emitted.
 */
object DebugUtterances {
    data class Utterance(val text: String, val language: Language?)

    private val _utterances = MutableSharedFlow<Utterance>(extraBufferCapacity = 4)
    val utterances: SharedFlow<Utterance> = _utterances.asSharedFlow()

    fun inject(text: String, lang: String?) {
        if (!BuildConfig.DEBUG) return
        val clean = text.trim().take(MAX_CHARS)
        if (clean.isEmpty()) return
        val language = when (lang?.lowercase()?.take(2)) {
            "es" -> Language.ES
            "en" -> Language.EN
            else -> null
        }
        _utterances.tryEmit(Utterance(clean, language))
    }

    private const val MAX_CHARS = 200
}

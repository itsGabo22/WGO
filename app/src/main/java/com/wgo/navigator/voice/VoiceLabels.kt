package com.wgo.navigator.voice

import com.wgo.navigator.Language
import java.util.Locale

/** Human names for engine voices ("Español (Colombia) 2"), in the UI language. Pure. */
object VoiceLabels {
    private fun ui(language: Language) = if (language == Language.ES) Locale("es") else Locale.ENGLISH

    /** The language and region of a locale tag, capitalised: "es-CO" -> "Español (Colombia)". */
    fun localeName(tag: String, language: Language): String {
        val name = Locale.forLanguageTag(tag).getDisplayName(ui(language))
        return name.replaceFirstChar { it.titlecase(ui(language)) }
    }

    /** "Español (Colombia) 2" for the [index]-th voice of that locale (1-based). */
    fun label(voice: VoiceInfo, index: Int, language: Language): String = "${localeName(voice.languageTag, language)} $index"

    /**
     * A short name from an engine voice name alone (Settings row): engines start names with the locale ("es-us-x-sfb",
     * "es-CO-..."); otherwise the raw name.
     */
    fun shortName(voiceName: String, language: Language): String {
        val parts = voiceName.split('-', '_')
        if (parts.size >= 2 && parts[0].length == 2 && parts[1].length in 2..3) {
            return localeName("${parts[0]}-${parts[1].uppercase(Locale.ROOT)}", language)
        }
        return voiceName
    }
}

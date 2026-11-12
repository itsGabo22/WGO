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

    /**
     * "Colombia 2" for the [index]-th voice of that locale (1-based): the picker lists only voices of the UI language,
     * so the region names the voice (the full "Español (Estados Unidos) 1" did not fit the 156 dp pill on the watch).
     * Without a region, the language name.
     */
    fun label(voice: VoiceInfo, index: Int, language: Language): String {
        val locale = Locale.forLanguageTag(voice.languageTag)
        val region = locale.getDisplayCountry(ui(language)).ifEmpty { localeName(voice.languageTag, language) }
        return "${region.replaceFirstChar { it.titlecase(ui(language)) }} $index"
    }

    /**
     * A short name from an engine voice name alone (Settings row): engines start names with the locale ("es-us-x-sfb",
     * "es-CO-..."); otherwise the raw name.
     */
    fun shortName(voiceName: String, language: Language): String {
        val parts = voiceName.split('-', '_')
        if (parts.size >= 2 && parts[0].length == 2 && parts[1].length in 2..3) {
            // The region alone, like the picker: "Español (Estados Unidos)" did not fit the 156 dp row on the watch.
            val tag = "${parts[0]}-${parts[1].uppercase(Locale.ROOT)}"
            val region = Locale.forLanguageTag(tag).getDisplayCountry(ui(language))
            return region.ifEmpty { localeName(tag, language) }.replaceFirstChar { it.titlecase(ui(language)) }
        }
        return voiceName
    }
}

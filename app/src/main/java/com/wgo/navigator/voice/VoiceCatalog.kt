package com.wgo.navigator.voice

import com.wgo.navigator.Language
import java.util.Locale

enum class VoiceGender { Female, Male }

/** One picker row: a real, distinct voice; [gender] when the engine states it, else [number] within its engine+region. */
data class VoiceEntry(val voice: VoiceInfo, val gender: VoiceGender?, val number: Int)

/**
 * The voice picker's rules (spec section 7, "ScreenVoicePicker and voice settings, P8"). Pure, for unit tests.
 *
 * Measured on the Galaxy Watch5 Pro: Samsung lists each locale twice ("es-US-SMTf00" and its alias "es-US-default"),
 * Google lists each speaker as "-local" and "-network" plus a "<tag>-language" alias. Only distinct, installed speakers
 * are offered.
 */
object VoiceCatalog {
    const val MAX_ENTRIES = 6
    const val MAX_PER_ENGINE = 4

    private const val ALIAS_FEATURE = "legacySetLanguageVoice"

    /** The gender when the engine states it: a `gender=` feature, or Samsung's `SMTf` / `SMTm` voice names. */
    fun gender(v: VoiceInfo): VoiceGender? {
        val feature = v.features.firstOrNull { it.startsWith("gender=") }?.substringAfter('=')?.lowercase(Locale.ROOT)
        when (feature) {
            "female" -> return VoiceGender.Female
            "male" -> return VoiceGender.Male
        }
        val n = v.name
        return when {
            n.contains("-SMTf") -> VoiceGender.Female
            n.contains("-SMTm") -> VoiceGender.Male
            else -> null
        }
    }

    /** The speaker behind a voice name: Google's "-local" / "-network" twins are one speaker. */
    fun speaker(v: VoiceInfo): String = v.name.lowercase(Locale.ROOT).removeSuffix("-local").removeSuffix("-network")

    /** An alias of another voice of the same engine and locale (Samsung "-default", Google "-language"). */
    fun isAlias(v: VoiceInfo, all: List<VoiceInfo>): Boolean {
        if (ALIAS_FEATURE in v.features) return true
        if (!v.name.endsWith("-default")) return false
        return all.any { it !== v && it.engine == v.engine && it.languageTag == v.languageTag && !it.name.endsWith("-default") }
    }

    /** Installed, non-alias voices with one entry per speaker (the offline twin kept) and no exact duplicates. */
    fun distinct(voices: List<VoiceInfo>): List<VoiceInfo> {
        val installed = voices.filter { it.installed }
        val real = installed.filterNot { isAlias(it, installed) }
        val perSpeaker = real.groupBy { it.engine to speaker(it) }.values.map { twins -> twins.minWith(compareBy({ it.network }, { it.name })) }
        return perSpeaker.distinctBy { listOf(it.engine, it.languageTag, speaker(it), it.quality, it.network, it.features.sorted()) }
    }

    /**
     * The picker rows for [language]: distinct voices ranked like the automatic choice (locale order, offline first),
     * at most [MAX_PER_ENGINE] per engine and [MAX_ENTRIES] in total, grouped by engine (engines in the order of their
     * best voice), numbered per engine and region when there is no gender.
     */
    fun entries(voices: List<VoiceInfo>, language: Language): List<VoiceEntry> {
        val ranked = VoiceChooser.ranked(distinct(voices), language)
        val picked = ArrayList<VoiceInfo>()
        for (v in ranked) {
            if (picked.size == MAX_ENTRIES) break
            if (picked.count { it.engine == v.engine } < MAX_PER_ENGINE) picked += v
        }
        val engineOrder = picked.map { it.engine }.distinct()
        val grouped = picked.sortedBy { engineOrder.indexOf(it.engine) }
        val counters = HashMap<Pair<String, String>, Int>()
        return grouped.map { v ->
            val g = gender(v)
            val key = v.engine to v.languageTag
            val n = if (g == null) (counters[key] ?: 0) + 1 else 0
            if (g == null) counters[key] = n
            VoiceEntry(v, g, n)
        }
    }

    /** A short engine name for headers and the engine row. */
    fun engineName(pkg: String, label: String = pkg): String = when {
        pkg.startsWith("com.samsung") -> "Samsung"
        pkg.startsWith("com.google") -> "Google"
        else -> label
    }
}

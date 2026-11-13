package com.wgo.navigator.voice

import android.content.Context
import android.content.SharedPreferences
import com.wgo.navigator.Language
import java.util.concurrent.ConcurrentHashMap

/**
 * What the app learnt about the device's recognizer, per language: that the offline model is missing (later sessions
 * go online at once) and which language tag heard speech last (later sessions start with it).
 */
interface RecognizerMemory {
    fun noOfflineModel(language: Language): Boolean
    fun markNoOfflineModel(language: Language)
    fun workingTag(language: Language): Int?
    fun setWorkingTag(language: Language, index: Int)
}

/** Forgotten with the instance (tests). */
class InMemoryRecognizerMemory : RecognizerMemory {
    private val noOffline: MutableSet<Language> = ConcurrentHashMap.newKeySet()
    private val tags = ConcurrentHashMap<Language, Int>()
    override fun noOfflineModel(language: Language) = language in noOffline
    override fun markNoOfflineModel(language: Language) {
        noOffline += language
    }
    override fun workingTag(language: Language): Int? = tags[language]
    override fun setWorkingTag(language: Language, index: Int) {
        tags[language] = index
    }
}

/**
 * Kept across app restarts in a tiny SharedPreferences file: an offline engine without the language pack reports
 * "ready" and only then fails (measured on the Galaxy Watch5 Pro), so asking it again in every new process would show
 * a false "speak now" once per process. Online sessions still let the service use an offline pack installed later.
 */
class PrefsRecognizerMemory private constructor(private val prefs: SharedPreferences) : RecognizerMemory {
    private val cache = InMemoryRecognizerMemory().also { mem ->
        Language.entries.forEach { language ->
            if (prefs.getBoolean(noOfflineKey(language), false)) mem.markNoOfflineModel(language)
            val tag = prefs.getInt(tagKey(language), -1)
            if (tag >= 0) mem.setWorkingTag(language, tag)
        }
    }

    override fun noOfflineModel(language: Language) = cache.noOfflineModel(language)
    override fun markNoOfflineModel(language: Language) {
        if (cache.noOfflineModel(language)) return
        cache.markNoOfflineModel(language)
        prefs.edit().putBoolean(noOfflineKey(language), true).apply()
    }
    override fun workingTag(language: Language) = cache.workingTag(language)
    override fun setWorkingTag(language: Language, index: Int) {
        if (cache.workingTag(language) == index) return
        cache.setWorkingTag(language, index)
        prefs.edit().putInt(tagKey(language), index).apply()
    }

    companion object {
        @Volatile
        private var instance: PrefsRecognizerMemory? = null

        fun get(context: Context): PrefsRecognizerMemory = instance ?: synchronized(this) {
            instance ?: PrefsRecognizerMemory(
                context.applicationContext.getSharedPreferences("wgo_recognizer", Context.MODE_PRIVATE)
            ).also { instance = it }
        }

        private fun noOfflineKey(language: Language) = "no_offline_${language.name}"
        private fun tagKey(language: Language) = "tag_${language.name}"
    }
}

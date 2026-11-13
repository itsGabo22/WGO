package com.wgo.navigator.voice

import com.wgo.navigator.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The P8 voice picker rules, on the voice list the Galaxy Watch5 Pro reported (WgoVoice dump, 2026-11-12). */
class VoicePickerRulesTest {
    private val samsung = "com.samsung.SMT"
    private val google = "com.google.android.tts"

    private fun s(name: String, tag: String, female: Boolean = true) = VoiceInfo(
        name, tag, network = false, engine = samsung, quality = 300, latency = 300,
        features = if (name.endsWith("-default")) emptySet() else setOf("gender=${if (female) "female" else "male"}", "variant=f00")
    )

    private fun g(name: String, tag: String, installed: Boolean = true) = VoiceInfo(
        name, tag, network = name.endsWith("-network"), installed = installed, engine = google, quality = 400, latency = 200,
        features = buildSet {
            add("networkTimeoutMs")
            if (name.endsWith("-language")) add("legacySetLanguageVoice")
        }
    )

    private val watch = listOf(
        s("es-US-SMTf00", "es-US"), s("es-US-default", "es-US"), s("es-ES-SMTf00", "es-ES"), s("es-ES-default", "es-ES"),
        s("en-US-SMTf00", "en-US"), s("en-US-default", "en-US"),
        g("es-us-x-esc-local", "es-US"), g("es-us-x-esc-network", "es-US"), g("es-us-x-esd-local", "es-US"),
        g("es-us-x-esd-network", "es-US"), g("es-us-x-esf-local", "es-US"), g("es-us-x-esf-network", "es-US"),
        g("es-us-x-sfb-local", "es-US"), g("es-us-x-sfb-network", "es-US"), g("es-US-language", "es-US"),
        g("es-es-x-eea-local", "es-ES"), g("es-es-x-eec-local", "es-ES"), g("es-ES-language", "es-ES"),
        g("es-mx-x-xyz-local", "es-MX", installed = false)
    )

    @Test fun aliasesTwinsAndMissingVoicesAreRemoved() {
        val names = VoiceCatalog.distinct(watch).map { it.name }.toSet()
        // Samsung's "-default" is the same speaker as its named voice; Google's "-language" is an alias.
        assertTrue(names.none { it.endsWith("-default") || it.endsWith("-language") })
        // One entry per Google speaker, the offline twin.
        assertTrue(names.none { it.endsWith("-network") })
        assertTrue("es-us-x-esc-local" in names && "es-us-x-sfb-local" in names)
        assertTrue("es-mx-x-xyz-local" !in names)
    }

    @Test fun aDefaultWithoutANamedVoiceIsKept() {
        val alone = listOf(s("vi-VN-default", "vi-VN"))
        assertEquals(alone, VoiceCatalog.distinct(alone))
    }

    @Test fun exactDuplicatesCollapse() {
        val a = g("es-us-x-esc-local", "es-US")
        assertEquals(1, VoiceCatalog.distinct(listOf(a, a.copy())).size)
    }

    @Test fun theSpanishPickerHasSixDistinctVoicesGroupedByEngine() {
        val entries = VoiceCatalog.entries(watch, Language.ES)
        assertEquals(VoiceCatalog.MAX_ENTRIES, entries.size)
        assertEquals(entries.map { it.voice.name }.distinct(), entries.map { it.voice.name })
        // Grouped: every engine's rows are together; at most 4 per engine.
        val engines = entries.map { it.voice.engine }
        assertEquals(engines.distinct(), engines.zipWithNext().filter { it.first != it.second }.map { it.second }.let { listOf(engines.first()) + it })
        assertTrue(engines.groupingBy { it }.eachCount().values.all { it <= VoiceCatalog.MAX_PER_ENGINE })
        // Locale order: the es-US voices come before es-ES inside each engine.
        val samsungTags = entries.filter { it.voice.engine == samsung }.map { it.voice.languageTag }
        assertEquals(listOf("es-US", "es-ES"), samsungTags)
    }

    @Test fun genderOnlyWhenTheEngineStatesIt() {
        assertEquals(VoiceGender.Female, VoiceCatalog.gender(s("es-US-SMTf00", "es-US")))
        assertEquals(VoiceGender.Male, VoiceCatalog.gender(VoiceInfo("es-US-SMTm00", "es-US", false)))
        assertEquals(VoiceGender.Male, VoiceCatalog.gender(VoiceInfo("x", "es-US", false, features = setOf("gender=male"))))
        assertNull(VoiceCatalog.gender(g("es-us-x-esc-local", "es-US")))
    }

    @Test fun labelsShowRegionAndGenderOrANumber() {
        val entries = VoiceCatalog.entries(watch, Language.ES)
        val labels = entries.map { VoiceLabels.entryLabel(it, Language.ES, "mujer", "hombre") }
        assertTrue(labels.toString(), "Estados Unidos · mujer" in labels && "España · mujer" in labels)
        assertTrue(labels.toString(), "Estados Unidos 1" in labels && "Estados Unidos 4" in labels)
        assertEquals("Spain · female", VoiceLabels.entryLabel(VoiceEntry(s("es-ES-SMTf00", "es-ES"), VoiceGender.Female, 0), Language.EN, "female", "male"))
    }

    @Test fun pitchAndSpeedMapToTheSpecValuesAndCycle() {
        assertEquals(listOf(0.85f, 1.0f, 1.15f), VoicePitch.entries.map { it.value })
        assertEquals(listOf(0.85f, 1.0f, 1.2f), VoiceRate.entries.map { it.value })
        assertEquals(VoicePitch.High, VoicePitch.Normal.next())
        assertEquals(VoicePitch.Low, VoicePitch.High.next())
        assertEquals(VoiceRate.Slow, VoiceRate.Fast.next())
    }

    @Test fun persistedNamesRoundTrip() {
        // SettingsRepository stores the enum names; they must read back to the same value.
        VoicePitch.entries.forEach { assertEquals(it, VoicePitch.valueOf(it.name)) }
        VoiceRate.entries.forEach { assertEquals(it, VoiceRate.valueOf(it.name)) }
    }

    @Test fun engineRowRules() {
        val installed = listOf(samsung, google)
        assertEquals(samsung, VoiceSettingsRules.defaultEngine(null, installed))
        assertEquals(google, VoiceSettingsRules.defaultEngine(google, installed))
        assertEquals(samsung, VoiceSettingsRules.defaultEngine("com.removed.engine", installed))
        assertEquals(google, VoiceSettingsRules.nextEngine(samsung, installed))
        assertEquals(samsung, VoiceSettingsRules.nextEngine(google, installed))
        assertEquals(samsung, VoiceSettingsRules.nextEngine(null, installed))
        assertNull(VoiceSettingsRules.nextEngine(samsung, emptyList()))
    }
}

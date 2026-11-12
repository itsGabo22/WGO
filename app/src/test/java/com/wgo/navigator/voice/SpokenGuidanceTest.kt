package com.wgo.navigator.voice

import com.wgo.navigator.Language
import com.wgo.navigator.VoiceMode
import com.wgo.navigator.core.Maneuver
import com.wgo.navigator.core.RouteStep
import com.wgo.navigator.ui.map.GeoCoord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spoken guidance rules (spec section 8, P7): phrases, numbers, the policy, dedupe and the voice choice. */
class SpokenGuidanceTest {
    private val right = RouteStep(Maneuver.RIGHT, "Carrera 35", GeoCoord(1.21, -77.28), 120.0)
    private val leftNoStreet = RouteStep(Maneuver.LEFT, "", GeoCoord(1.22, -77.29), 80.0)
    private val roundabout = RouteStep(Maneuver.ROUNDABOUT, "Avenida Panamericana", GeoCoord(1.23, -77.29), 300.0, exit = 2)

    @Test fun theSpecPhrasesInSpanish() {
        assertEquals("En doscientos metros, gira a la derecha por Carrera 35", GuidancePhrases.approaching(right, 200.0, Language.ES).text)
        assertEquals("Gira a la derecha", GuidancePhrases.atTurn(right, Language.ES).text)
        assertEquals("Recalculando", GuidancePhrases.reroute(Language.ES).text)
        assertEquals("Has llegado a tu destino", GuidancePhrases.arrived(Language.ES).text)
        assertEquals("Perdí la señal del GPS", GuidancePhrases.gpsLost(Language.ES).text)
    }

    @Test fun theSpecPhrasesInEnglish() {
        assertEquals("In two hundred meters, turn right onto Carrera 35", GuidancePhrases.approaching(right, 200.0, Language.EN).text)
        assertEquals("Turn right", GuidancePhrases.atTurn(right, Language.EN).text)
        assertEquals("Recalculating", GuidancePhrases.reroute(Language.EN).text)
        assertEquals("You have arrived", GuidancePhrases.arrived(Language.EN).text)
    }

    @Test fun noStreetAndRoundabouts() {
        assertEquals("En ciento cincuenta metros, gira a la izquierda", GuidancePhrases.approaching(leftNoStreet, 148.0, Language.ES).text)
        assertEquals("En la glorieta, toma la salida número dos", GuidancePhrases.atTurn(roundabout, Language.ES).text)
        assertEquals("In two hundred meters, at the roundabout, take exit number two",
            GuidancePhrases.approaching(roundabout, 203.0, Language.EN).text)
    }

    @Test fun urgentPhrasesFlushTheQueue() {
        assertTrue(GuidancePhrases.atTurn(right, Language.ES).urgent)
        assertTrue(GuidancePhrases.arrived(Language.ES).urgent)
        assertFalse(GuidancePhrases.approaching(right, 200.0, Language.ES).urgent)
        assertFalse(GuidancePhrases.reroute(Language.ES).urgent)
        assertFalse(GuidancePhrases.gpsLost(Language.ES).urgent)
    }

    @Test fun numbersAsWords() {
        val es = mapOf(0 to "cero", 16 to "dieciséis", 21 to "veintiuno", 35 to "treinta y cinco", 100 to "cien",
            101 to "ciento uno", 150 to "ciento cincuenta", 200 to "doscientos", 500 to "quinientos", 990 to "novecientos noventa",
            1000 to "mil", 2000 to "dos mil", 21000 to "veintiún mil")
        es.forEach { (n, w) -> assertEquals("$n", w, NumberWords.of(n, Language.ES)) }
        val en = mapOf(0 to "zero", 15 to "fifteen", 42 to "forty-two", 200 to "two hundred", 250 to "two hundred fifty",
            1000 to "one thousand")
        en.forEach { (n, w) -> assertEquals("$n", w, NumberWords.of(n, Language.EN)) }
    }

    @Test fun distancesRoundToTensThenKilometres() {
        assertEquals("doscientos metros", GuidancePhrases.distance(203.0, Language.ES))
        assertEquals("diez metros", GuidancePhrases.distance(2.0, Language.ES))
        assertEquals("un kilómetro", GuidancePhrases.distance(1200.0, Language.ES))
        assertEquals("dos kilómetros", GuidancePhrases.distance(1600.0, Language.ES))
        assertEquals("one kilometer", GuidancePhrases.distance(999.0, Language.EN))
    }

    @Test fun thePolicyFollowsTheModeAndTheAudioRoute() {
        assertFalse(GuidancePolicy.shouldSpeak(VoiceMode.Off, headsetConnected = true))
        assertFalse(GuidancePolicy.shouldSpeak(VoiceMode.Auto, headsetConnected = false))
        assertTrue(GuidancePolicy.shouldSpeak(VoiceMode.Auto, headsetConnected = true))
        assertTrue(GuidancePolicy.shouldSpeak(VoiceMode.Always, headsetConnected = false))
    }

    @Test fun eachPhraseOncePerManeuverAndPerLoss() {
        val d = GuidanceDedupe()
        assertTrue(d.approaching(right))
        assertFalse(d.approaching(right))
        assertTrue(d.atTurn(right))
        assertFalse(d.atTurn(right))
        assertTrue(d.approaching(leftNoStreet))
        assertTrue(d.gpsLost())
        assertFalse(d.gpsLost())
        d.gpsRecovered()
        assertTrue(d.gpsLost())
        assertTrue(d.lostRoute())
        assertFalse(d.lostRoute())
        d.routeBack()
        assertTrue(d.lostRoute())
        d.newRoute()
        assertTrue(d.approaching(right))
    }

    @Test fun theVoiceChoiceFollowsTheLocaleOrderOfflineFirst() {
        val voices = listOf(
            VoiceInfo("es-es-x-a", "es-ES", network = false),
            VoiceInfo("es-us-x-net", "es-US", network = true),
            VoiceInfo("es-us-x-local", "es-US", network = false),
            VoiceInfo("es-mx-x-a", "es-MX", network = false),
            VoiceInfo("en-us-x-a", "en-US", network = false),
            VoiceInfo("es-co-x-missing", "es-CO", network = false, installed = false)
        )
        assertEquals("es-us-x-local", VoiceChooser.choose(voices, Language.ES, null)?.name)
        assertEquals(listOf("es-us-x-local", "es-us-x-net", "es-mx-x-a", "es-es-x-a"), VoiceChooser.ranked(voices, Language.ES, 4).map { it.name })
        assertEquals("es-es-x-a", VoiceChooser.choose(voices, Language.ES, "es-es-x-a")?.name)
        assertEquals("es-us-x-local", VoiceChooser.choose(voices, Language.ES, "gone")?.name)
        assertEquals("en-us-x-a", VoiceChooser.choose(voices, Language.EN, null)?.name)
        assertNull(VoiceChooser.choose(emptyList(), Language.ES, null))
    }

    @Test fun voiceTagsFromSamsungAndGoogleNames() {
        // Measured on the Galaxy Watch5 Pro: Samsung TTS reports "spa" without a region; the name carries it.
        assertEquals("es-US", VoiceChooser.tagOf("es-US-SMTf00", java.util.Locale("spa")))
        assertEquals("es-ES", VoiceChooser.tagOf("es-ES-default", java.util.Locale("spa")))
        assertEquals("es-US", VoiceChooser.tagOf("es-us-x-sfb-local", java.util.Locale("es", "US")))
        assertEquals("es", VoiceChooser.tagOf("voice7", java.util.Locale("spa")))
        val watch = listOf("es-ES-default", "es-US-SMTf00", "es-US-default", "es-ES-SMTf00", "en-US-SMTf00")
            .map { VoiceInfo(it, VoiceChooser.tagOf(it, java.util.Locale("spa")), network = false) }
        assertEquals(listOf("es-US-SMTf00", "es-US-default", "es-ES-SMTf00", "es-ES-default"),
            VoiceChooser.ranked(watch, Language.ES, 4).map { it.name })
    }

    @Test fun voiceLabels() {
        assertEquals("Colombia 1", VoiceLabels.label(VoiceInfo("x", "es-CO", false), 1, Language.ES))
        assertEquals("Estados Unidos 2", VoiceLabels.label(VoiceInfo("x", "es-US", false), 2, Language.ES))
        assertEquals("Español 1", VoiceLabels.label(VoiceInfo("x", "es", false), 1, Language.ES))
        assertEquals("United States", VoiceLabels.shortName("es-us-x-sfb-local", Language.EN))
        assertEquals("Estados Unidos", VoiceLabels.shortName("es-US-SMTf00", Language.ES))
    }
}

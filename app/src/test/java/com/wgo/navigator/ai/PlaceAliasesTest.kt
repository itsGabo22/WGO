package com.wgo.navigator.ai

import com.wgo.navigator.Language
import com.wgo.navigator.TravelMode
import com.wgo.navigator.core.IntentKind
import com.wgo.navigator.core.Interpretation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pasto nicknames resolve locally (confidence >= the accept threshold, so no remote call) to search strings. */
class PlaceAliasesTest {
    private data class Case(val text: String, val place: String, val mode: TravelMode? = null, val lang: Language = Language.ES)

    private val udenar = "Universidad de Nariño"

    private val resolved = listOf(
        Case("llévame a la UDENAR", udenar),
        Case("llévame a la UDENAR a pie", udenar, TravelMode.Walk),
        Case("Llevame a la udenar en carro por favor", udenar, TravelMode.Drive),
        Case("la sede de Udenar en Torobajo", udenar),
        Case("take me to Udenar", udenar, lang = Language.EN),
        Case("vamos a la Mariana", "Universidad Mariana"),
        Case("al terminal en bus", "Terminal de Transportes de Pasto", TravelMode.Drive),
        Case("take me to the bus terminal", "Terminal de Transportes de Pasto", lang = Language.EN),
        Case("quiero ir al hospital departamental", "Hospital Departamental de Nariño"),
        Case("HUDN", "Hospital Departamental de Nariño"),
        Case("llévame a la plaza", "Plaza de Nariño"),
        Case("Plaza de Narino", "Plaza de Nariño"),
        Case("navigate to Cesmag", "Universidad CESMAG", lang = Language.EN),
        Case("la UNAD", "UNAD"),
        Case("drive to the airport", "Aeropuerto Antonio Nariño", TravelMode.Drive, Language.EN),
        Case("vamos al estadio caminando", "Estadio Departamental Libertad", TravelMode.Walk)
    )

    @Test fun nicknamesResolveLocallyWithHighConfidence() {
        for (c in resolved) {
            val r = LocalIntentParser.parse(c.text, c.lang)
            assertEquals(c.text, IntentKind.Navigate, r.kind)
            assertEquals(c.text, c.place, r.placeQuery)
            assertEquals(c.text, c.mode, r.mode)
            assertTrue(c.text, r.confidence >= Interpretation.ACCEPT_CONFIDENCE)
        }
    }

    @Test fun genericWordsOnlyMatchAsTheWholePlace() {
        assertEquals("plaza de mercado", LocalIntentParser.parse("llévame a la plaza de mercado", Language.ES).placeQuery)
        assertEquals("terminal sur", LocalIntentParser.parse("vamos al terminal sur", Language.ES).placeQuery)
        // A word that merely contains an acronym is not it.
        assertEquals("unadilla", LocalIntentParser.parse("ir a unadilla", Language.ES).placeQuery)
    }

    @Test fun keysAreNormalizedAndAliasesNeverCarryCoordinates() {
        assertTrue(PlaceAliases.keys.none { k -> k != LocalIntentParser.normalize(k) })
        assertTrue(PlaceAliases.keys.size >= 12)
        for (k in PlaceAliases.keys) {
            val target = PlaceAliases.resolve(k.split(' '))!!
            assertTrue(target, target.none { it.isDigit() })
        }
    }
}

package com.wgo.navigator.places

import com.wgo.navigator.Language
import com.wgo.navigator.ai.LocalIntentParser
import com.wgo.navigator.geo.PlaceRequest
import com.wgo.navigator.geo.PlaceResolver
import com.wgo.navigator.geo.Resolution
import com.wgo.navigator.ui.map.GeoCoord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** P11 brand variants and speech errors (docs/PLACE_SEARCH.md, "Normalisation", "Fuzzy and phonetic matching"). */
class BrandVariantMatchingTest {
    companion object {
        private val index: PlaceIndex by lazy {
            PlaceIndex { File("src/main/assets/${PlaceIndex.ASSET}").inputStream() }.also { runBlocking { it.ensureLoaded() } }
        }
        private val home = GeoCoord(1.2170, -77.2885)
    }

    @Test fun spellingVariantsShareOneForm() {
        assertEquals(listOf("mister", "pollo"), PlaceText.tokens("Mr. Pollo"))
        assertEquals(listOf("mister", "pollo"), PlaceText.tokens("míster pollo"))
        assertEquals(listOf("mister", "pollo"), PlaceText.tokens("Señor Pollo"))
        assertEquals(listOf("calle", "18"), PlaceText.tokens("Cll 18"))
        assertEquals(listOf("carrera", "27"), PlaceText.tokens("Cra. 27"))
        assertEquals(listOf("avenida", "boyaca"), PlaceText.tokens("Av. Boyacá"))
        assertEquals(listOf("centro", "comercial", "unico"), PlaceText.tokens("C.C. Único"))
        assertEquals(listOf("centro", "comercial", "unico"), PlaceText.tokens("CC Único"))
    }

    @Test fun phoneticKeyMergesSpanishSoundAlikes() {
        assertEquals(PlaceText.phonetic("cocha"), PlaceText.phonetic("kocha"))
        assertEquals(PlaceText.phonetic("barber"), PlaceText.phonetic("barver"))
        assertEquals(PlaceText.phonetic("posgrados"), PlaceText.phonetic("pozgrados"))
        assertEquals(PlaceText.phonetic("parrilla"), PlaceText.phonetic("parriya"))
        assertEquals(PlaceText.phonetic("hotel"), PlaceText.phonetic("otel"))
        assertEquals(PlaceText.phonetic("gerardo"), PlaceText.phonetic("jerardo"))
        assertFalse(PlaceText.phonetic("gusto") == PlaceText.phonetic("justo"))
    }

    @Test fun qualityRanksEqualOverPrefixOverSpelling() {
        assertEquals(4, PlaceText.quality("catedral", "catedral"))
        assertEquals(3, PlaceText.quality("cated", "catedral"))
        assertEquals(3, PlaceText.quality("kocha", "cocha"))
        assertEquals(2, PlaceText.quality("catedral", "catedra"))
        assertEquals(2, PlaceText.quality("confesciones", "confecciones"))
        assertEquals(2, PlaceText.editDistance("pinasaso", "picasso", 2))
        assertEquals(1, PlaceText.quality("mi", "mister"))
        assertEquals(0, PlaceText.quality("mr", "king"))
        assertEquals(0, PlaceText.quality("pollo", "pizza"))
    }

    @Test fun regionWordsInANameKeepItExact() {
        assertEquals(3, PlaceIndex.tierOf(listOf("catedral"), listOf("catedral", "pasto")))
        assertEquals(2, PlaceIndex.tierOf(listOf("catedra"), listOf("catedral", "pasto")))
    }

    @Test fun mrDoesNotEndTheClauseAndNearestIsNotPartOfTheName() {
        assertEquals("Mr Pollo", LocalIntentParser.parse("Mr. Pollo", Language.ES).placeQuery)
        assertEquals("Mr Pollo", LocalIntentParser.parse("Llévame por favor a Mr. Pollo.", Language.ES).placeQuery)
        val near = LocalIntentParser.parse("llévame al Mr. Pollo más cercano", Language.ES)
        assertEquals("Mr Pollo", near.placeQuery)
        assertTrue(near.placeNearest)
        val near2 = LocalIntentParser.parse("llévame al dollarcity que quede más cerca", Language.ES)
        assertEquals("dollarcity", near2.placeQuery)
        assertTrue(near2.placeNearest)
        assertTrue(LocalIntentParser.parse("take me to the nearest subway", Language.EN).placeNearest)
        assertFalse(LocalIntentParser.parse("llévame a mr pollo", Language.ES).placeNearest)
        assertEquals("Centro Comercial Unicentro", LocalIntentParser.parse("llévame al C.C. Unicentro", Language.ES).placeQuery)
    }

    private fun resolve(text: String): Resolution {
        val i = LocalIntentParser.parse(text, Language.ES)
        return runBlocking {
            PlaceResolver(index, null).resolve(
                PlaceRequest(i.placeQuery!!, i.placeHint, i.placeCategory, i.placeFromAlias, text, vague = i.placeVague, nearest = i.placeNearest),
                home, Language.ES
            )
        }
    }

    @Test fun theOwnersSentenceFindsMisterPolloNotMrKingPollo() {
        for (text in listOf("llévame por favor a mr pollo", "Llévame por favor a Mr. Pollo.", "mr pollo", "míster pollo", "mi pollo")) {
            val r = resolve(text) as Resolution.Found
            val first = r.candidates.first().place.name
            assertTrue("$text -> $first", first.startsWith("Mister Pollo"))
            assertFalse(r.candidates.any { it.place.name == "Mr. King Pollo" && it == r.candidates.first() })
        }
    }

    @Test fun aNameThatDoesNotExistIsNeverPickedAutomatically() {
        val r = resolve("llévame a la pizzería galáctica de venus")
        assertTrue(r !is Resolution.Found || r.uncertain)
        assertNull((r as? Resolution.Found)?.candidates?.firstOrNull { it.place.name.contains("galáctica", ignoreCase = true) })
    }
}

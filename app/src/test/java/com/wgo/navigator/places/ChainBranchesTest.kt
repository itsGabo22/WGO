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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** P11 chains (docs/PLACE_SEARCH.md, "Brand variants and chains"): nearest branch first, real index, no network. */
class ChainBranchesTest {
    companion object {
        private val index: PlaceIndex by lazy {
            PlaceIndex { File("src/main/assets/${PlaceIndex.ASSET}").inputStream() }.also { runBlocking { it.ensureLoaded() } }
        }

        /** Near Mister Pollo Norte (Cra. 44B #19-56). */
        private val north = GeoCoord(1.2335, -77.2868)

        /** Near Mister Pollo Idema (Calle 18A #4-114). */
        private val east = GeoCoord(1.1995, -77.2680)
    }

    private fun resolve(text: String, fix: GeoCoord?): Resolution {
        val i = LocalIntentParser.parse(text, Language.ES)
        return runBlocking {
            PlaceResolver(index, null).resolve(
                PlaceRequest(i.placeQuery!!, i.placeHint, i.placeCategory, i.placeFromAlias, text, vague = i.placeVague, nearest = i.placeNearest),
                fix, Language.ES
            )
        }
    }

    @Test fun mrPolloOffersTheNearestBranchFirstAsThePickList() {
        val fromNorth = resolve("llévame por favor a mr pollo", north) as Resolution.Found
        assertTrue(fromNorth.uncertain)
        assertEquals("Mister Pollo Norte", fromNorth.places.first().name)
        assertEquals(3, fromNorth.candidates.size)
        assertTrue(fromNorth.candidates.zipWithNext().all { (a, b) -> a.distanceM <= b.distanceM })
        val fromEast = resolve("llévame por favor a mr pollo", east) as Resolution.Found
        assertEquals("Mister Pollo Idema", fromEast.places.first().name)
        assertTrue(fromEast.places.all { it.name.startsWith("Mister Pollo") })
    }

    @Test fun theNearestOneGoesStraightToConfirm() {
        val r = resolve("llévame al mr pollo más cercano", north) as Resolution.Found
        assertFalse(r.uncertain)
        assertEquals("Mister Pollo Norte", r.places.first().name)
        assertTrue(r.candidates.size >= 2) // OTRO cycles the other branches
    }

    @Test fun withoutAPositionTheMostConfidentBranchesComeFirst() {
        val r = resolve("llévame a mr pollo", null) as Resolution.Found
        assertTrue(r.candidates.zipWithNext().all { (a, b) -> a.confidence >= b.confidence })
    }

    @Test fun aBrandRowAtTheSameMallIsNotASecondBranch() {
        // Overture brands "Mister Pizza" as Mister Pollo, 15 m from the Unicentro branch: one place, the better name.
        val r = resolve("llévame a mr pollo", GeoCoord(1.2163, -77.2880)) as Resolution.Found
        assertEquals("Mister Pollo", r.places.first().name)
        assertFalse(r.places.contains(r.places.firstOrNull { it.name == "Mister Pizza" }))
    }

    @Test fun similarNamesThatAreNotAChainStayOnePlace() {
        // Exact whole name with no brand behind it: that place.
        val templo = resolve("llévame al templo de santiago", north) as Resolution.Found
        assertEquals("Templo de Santiago", templo.places.first().name)
        // "Max" is not "Maxiel": no chain, the hotel first.
        val hotel = resolve("llévame al hotel max", north) as Resolution.Found
        assertEquals("Hotel Max", hotel.places.first().name)
    }

    @Test fun chainsNeedNoLanguageModelCall() {
        val resolver = PlaceResolver(index, null)
        runBlocking { resolver.resolve(PlaceRequest("mr pollo", utterance = "llévame a mr pollo"), north, Language.ES) }
        assertEquals(0, resolver.lastAiCalls)
        assertEquals(0, resolver.lastRequests)
    }
}

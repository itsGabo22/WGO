package com.wgo.navigator.places

import com.wgo.navigator.ui.map.GeoCoord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The real Pasto asset (docs/THIRD_PARTY_DATA.md): the owner's places resolve offline. */
class PlaceIndexTest {
    private val index = PlaceIndex { File("src/main/assets/${PlaceIndex.ASSET}").inputStream() }
    private val home = GeoCoord(1.2170, -77.2885) // near the Panamericana (Unicentro)
    private val centre = GeoCoord(1.2136, -77.2811)

    private fun names(q: String, at: GeoCoord = home) = runBlocking {
        assertTrue(index.ensureLoaded())
        index.search(q, at, minTier = 2).map { it.place.name }
    }

    @Test fun theAssetNameSurvivesPackaging() {
        // The packager gunzips `.gz` assets and renames them; the app would then not find the index.
        assertFalse(PlaceIndex.ASSET.endsWith(".gz"))
        assertTrue(File("src/main/assets/${PlaceIndex.ASSET}").isFile)
    }

    @Test fun loadsTheAsset() = runBlocking {
        assertTrue(index.ensureLoaded())
        assertTrue(index.size > 7_500)
    }

    @Test fun indexV2KeepsBrandsAndCoversTheAirport() = runBlocking {
        assertTrue(index.ensureLoaded())
        // P11: the Mister Pollo branches carry their Overture brand.
        val branches = index.rows.filter { it.brand == "Mister Pollo MRP" }
        assertTrue(branches.size >= 6)
        // The real airport (Chachagüí, ~20 km north) is inside the v2 box.
        assertTrue(PlaceIndex.covers(GeoCoord(1.397185, -77.291843)))
        assertTrue(index.rows.any { it.category == "airport" && it.coord.lat > 1.39 })
    }

    @Test fun aVersionOneFileWithoutTheBrandColumnStillLoads() {
        val v1 = "# v1\nid\tname\tname_norm\talt_norm\tcategory\taddress\tlat\tlon\tconfidence\tsource\n" +
            "x1\tCubanitos\tcubanitos\t\trestaurant\tCalle 1\t1.2\t-77.28\t0.9\tmeta\n"
        val bytes = java.io.ByteArrayOutputStream().also { o -> java.util.zip.GZIPOutputStream(o).use { it.write(v1.toByteArray()) } }
        val rows = PlaceIndex.parse(bytes.toByteArray().inputStream())
        assertEquals(1, rows.size)
        assertEquals("", rows[0].brand)
        assertEquals("meta", rows[0].source)
    }

    @Test fun cubanitosExactFirstThenTheExpressBranchByFuzzyMatch() {
        val n = names("Cubanitos")
        assertEquals("Cubanitos", n[0])
        assertTrue(n.contains("Cuabanitos Express"))
    }

    @Test fun laMercedExactRestaurantNearHomeFirst() {
        assertEquals("La Merced", names("la Merced")[0])
    }

    @Test fun accentAndCaseInsensitive() {
        assertEquals(names("CUBANITOS"), names("cubanitós"))
    }

    @Test fun categoryWordIsABoostNotPartOfTheName() {
        val hits = runBlocking { index.ensureLoaded(); index.search("restaurante la merced", centre, minTier = 3) }
        assertTrue(hits.isNotEmpty())
        assertTrue(hits.first().categoryBoost)
        assertTrue(hits.all { PlaceText.tokens(it.place.name).contains("merced") })
    }

    @Test fun tokenRules() {
        assertTrue(PlaceText.withinOneEdit("cubanitos", "cuabanitos"))
        assertFalse(PlaceText.withinOneEdit("cubanitos", "cubanos"))
        assertTrue(PlaceText.tokenMatches("merc", "merced"))
        assertTrue(PlaceText.tokenMatches("bar", "barrio"))
        assertFalse(PlaceText.tokenMatches("ba", "barrio"))
        assertEquals(listOf("plaza", "narino"), PlaceText.tokens("la Plaza de Nariño"))
    }

    @Test fun nothingFartherThanTheCap() = runBlocking {
        index.ensureLoaded()
        val far = GeoCoord(4.711, -74.072) // Bogotá: the whole index is > 25 km away
        assertTrue(index.search("Cubanitos", far).isEmpty())
    }
}

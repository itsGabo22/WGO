package com.wgo.navigator.places

import com.wgo.navigator.Language
import com.wgo.navigator.ai.LlmInterpreter
import com.wgo.navigator.ai.LocalIntentParser
import com.wgo.navigator.core.IntentKind
import com.wgo.navigator.core.Outcome
import com.wgo.navigator.core.Place
import com.wgo.navigator.core.WgoFailure
import com.wgo.navigator.geo.OnlinePlaceSearch
import com.wgo.navigator.geo.PlaceRequest
import com.wgo.navigator.geo.PlaceResolver
import com.wgo.navigator.geo.PlaceSource
import com.wgo.navigator.geo.Resolution
import com.wgo.navigator.ui.map.GeoCoord
import com.wgo.navigator.ui.map.RadarProjection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/**
 * Golden utterances and resolution scenarios for docs/PLACE_SEARCH.md, with a fixture index (built like the real asset)
 * and a fake Nominatim that records every request.
 */
class PlaceGoldenTest {
    // ---- 1. Utterance -> (name, hint): 26 Spanish phrases ----

    private data class U(val text: String, val name: String, val hint: String?)

    private val utterances = listOf(
        U("llévame a la Merced de la Panamericana", "Merced", "Panamericana"),
        U("llévame a Cubanitos de la Panamericana", "Cubanitos", "Panamericana"),
        U("llévame a Cubanitos", "Cubanitos", null),
        U("LLÉVAME A CUBANITOS", "CUBANITOS", null),
        U("llevame a cubanitos de la panamericana", "cubanitos", "panamericana"),
        U("quiero ir a Cubanitos cerca de Unicentro", "Cubanitos", "Unicentro"),
        U("vamos a la droguería Pasteur por la Avenida Boyacá", "droguería Pasteur", "Avenida Boyacá"),
        U("llévame al Éxito de la Panamericana", "Éxito", "Panamericana"),
        U("llévame a la Merced del centro", "Merced", "centro"),
        U("llévame a Cubanitos en la Calle 20", "Cubanitos", "Calle 20"),
        U("ir a la panadería al lado del parque Infantil", "panadería", "parque Infantil"),
        U("llévame a Juan Valdez junto a la Plaza de Nariño", "Juan Valdez", "Plaza de Nariño"),
        U("llévame a la farmacia frente al Hospital Departamental", "farmacia", "Hospital Departamental"),
        U("como llego a Cuabanitos Express sobre la Calle 20", "Cuabanitos Express", "Calle 20"),
        U("llévame a la Universidad de Nariño", "Universidad de Nariño", null),
        U("llévame a la Plaza de Nariño", "Plaza de Nariño", null),
        U("llévame al terminal", "Terminal de Transportes de Pasto", null),
        U("llévame a la UDENAR", "Universidad de Nariño", null),
        U("llévame a Torobajo", "Universidad de Nariño", null),
        U("llévame a la Mariana a pie", "Universidad Mariana", null),
        U("llévame al Hospital Departamental de Nariño", "Hospital Departamental de Nariño", null),
        U("vamos a la Catedral de Pasto", "Catedral", null),
        U("llévame a la Merced en carro", "Merced", null),
        U("llévame a Cubanitos por la 27", "Cubanitos", "27"),
        U("quiero ir al restaurante La Merced de la Avenida Boyacá", "restaurante La Merced", "Avenida Boyacá"),
        U("llévame a la casa azul", "casa azul", null)
    )

    @Test fun twentySixUtterancesSplitIntoNameAndHint() {
        assertTrue(utterances.size >= 25)
        val bad = utterances.mapNotNull { u ->
            val r = LocalIntentParser.parse(u.text, Language.ES)
            val ok = r.kind == IntentKind.Navigate && r.placeQuery == u.name && r.placeHint == u.hint
            if (ok) null else "${u.text} -> ${r.placeQuery} / ${r.placeHint}"
        }
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    // ---- 2. Resolution with a fixture index and a fake Nominatim ----

    private val home = GeoCoord(1.2170, -77.2885)
    private val medellin = GeoCoord(6.2494, -75.6113)

    private fun row(name: String, cat: String, addr: String, lat: Double, lon: Double, conf: Double) =
        listOf("id", name, PlaceText.normalize(name), "", cat, addr, "$lat", "$lon", "$conf", "meta").joinToString("\t")

    private val fixture = listOf(
        row("Cubanitos", "food_and_drink", "Pan-American Highway", 1.218225, -77.288963, 0.95),
        row("Cuabanitos Express", "fast_food_restaurant", "Cl 20 #31B-25", 1.221356, -77.279829, 0.90),
        row("La Merced", "restaurant", "Carrera 36 12-14 San Ignacio", 1.217181, -77.289202, 0.98),
        row("Restaurante La Merced", "restaurant", "Av. Boyacá #20-1", 1.207079, -77.281552, 0.56),
        row("Restaurante La Merced", "restaurant", "Cra. 22 #17-37", 1.212144, -77.278382, 0.60),
        row("Restaurante La Merced", "restaurant", "Cra. 22 #17-40", 1.212200, -77.278400, 0.50), // duplicate < 150 m
        row("Hotel Boutique La Merced", "hotel", "Cl 18 #21B - 11", 1.21173, -77.277691, 0.97)
    )

    private fun index(): PlaceIndex {
        val tsv = "# fixture\nid\tname\tname_norm\talt_norm\tcategory\taddress\tlat\tlon\tconfidence\tsource\n" +
            fixture.joinToString("\n") + "\n"
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(tsv.toByteArray()) }
        val bytes = out.toByteArray()
        return PlaceIndex { ByteArrayInputStream(bytes) }
    }

    /** Fake Nominatim: answers by query, records each request. */
    private class FakeOnline(val answers: Map<String, List<Place>>) : OnlinePlaceSearch {
        val queries = ArrayList<String>()
        override suspend fun once(query: String, center: GeoCoord, bounded: Boolean): Outcome<List<Place>> {
            queries += "$query|bounded=$bounded"
            val hit = answers[query].orEmpty()
            return if (hit.isEmpty()) Outcome.Err(WgoFailure.NotFound) else Outcome.Ok(hit)
        }
    }

    private val myPlaces = MyPlaces {
        """[{"aliases":["la casa azul"],"name":"Casa Azul (ejemplo)","lat":1.2,"lon":-77.27,"note":"ficticia"}]"""
    }

    private fun resolve(req: PlaceRequest, online: FakeOnline = FakeOnline(emptyMap()), at: GeoCoord? = home) = runBlocking {
        PlaceResolver(index(), online, myPlaces).resolve(req, at, Language.ES)
    }

    private fun Resolution.names() = (this as Resolution.Found).places.map { it.name }

    @Test fun cubanitosResolvesOfflineToTheNearestRealOne() {
        val online = FakeOnline(mapOf("Cubanitos" to listOf(Place("Cubanitos", "Medellín", medellin))))
        val r = resolve(PlaceRequest("Cubanitos"), online)
        assertEquals(listOf("Cubanitos", "Cuabanitos Express"), r.names())
        assertTrue("no request when the index has a strong match", online.queries.isEmpty())
    }

    @Test fun theFarDuplicateIsNeverAutoSelected() {
        // Not in the index: only Nominatim knows it, once far (Medellín) and once near.
        val near = Place("Panadería Galeras", "Pasto", GeoCoord(1.214, -77.282))
        val online = FakeOnline(mapOf("Panadería Galeras" to listOf(Place("Panadería Galeras", "Medellín", medellin), near)))
        val r = resolve(PlaceRequest("Panadería Galeras"), online) as Resolution.Found
        assertEquals(near, r.places.first())
        val far = r.candidates.last()
        assertTrue(far.distanceM > PlaceResolver.FAR_KM * 1000)
    }

    @Test fun onlyFarHitsAreListedButFlaggedFar() {
        val online = FakeOnline(mapOf("Tienda Lejana" to listOf(Place("Tienda Lejana", "Medellín", medellin))))
        val r = resolve(PlaceRequest("Tienda Lejana"), online) as Resolution.Found
        assertTrue(RadarProjection.distanceMeters(r.places.first().coord, home) > PlaceResolver.FAR_KM * 1000)
        assertEquals(listOf("Tienda Lejana|bounded=false", "Tienda Lejana|bounded=true"), online.queries)
    }

    @Test fun aHintWithZeroHitsRetriesTheNameAlone() {
        val target = Place("Heladería Mimos", "Calle 18", GeoCoord(1.2142, -77.2812))
        val online = FakeOnline(mapOf("Heladería Mimos" to listOf(target)))
        val r = resolve(PlaceRequest("Heladería Mimos", hint = "Panamericana"), online)
        assertEquals(listOf("Heladería Mimos"), r.names())
        assertEquals(listOf("Heladería Mimos, Panamericana|bounded=false", "Heladería Mimos|bounded=true"), online.queries)
    }

    @Test fun theHintReranksSameTierCandidatesByAddress() {
        val r = resolve(PlaceRequest("restaurante La Merced", hint = "Avenida Boyacá"))
        assertEquals("Av. Boyacá #20-1", (r as Resolution.Found).places.first().address)
    }

    @Test fun thePanamericanaHintMatchesPanAmericanHighway() {
        val r = resolve(PlaceRequest("Cubanitos", hint = "Panamericana"), at = GeoCoord(1.2214, -77.2799)) as Resolution.Found
        assertEquals("Cubanitos", r.places.first().name)
        assertTrue(r.candidates.first().hintScore > 0.0)
    }

    @Test fun accentsAndCaseDoNotMatter() {
        assertEquals(resolve(PlaceRequest("Cubanitos")).names(), resolve(PlaceRequest("CUBANITÓS")).names())
        assertEquals("La Merced", resolve(PlaceRequest("la merced")).names().first())
    }

    @Test fun myPlacesAliasBeatsEverythingOffline() {
        val online = FakeOnline(emptyMap())
        val r = resolve(PlaceRequest("la Casa Azul"), online) as Resolution.Found
        assertEquals(PlaceSource.MyPlaces, r.candidates.single().source)
        assertTrue(online.queries.isEmpty())
    }

    @Test fun aCategoryWordTheUserDidNotSayIsNotAnExactMatch() {
        // Measured on the watch (P7): "Plaza de Nariño" picked "Drogueria Plaza De Nariño", a pharmacy.
        val pharmacy = listOf(row("Drogueria Plaza De Nariño", "pharmacy", "Cl 18", 1.2140, -77.2810, 0.9))
        val tsv = "# f\nid\tname\tname_norm\talt_norm\tcategory\taddress\tlat\tlon\tconfidence\tsource\n" + pharmacy.joinToString("\n") + "\n"
        val out = ByteArrayOutputStream().also { o -> GZIPOutputStream(o).use { it.write(tsv.toByteArray()) } }.toByteArray()
        val idx = PlaceIndex { ByteArrayInputStream(out) }
        val hits = runBlocking { idx.ensureLoaded(); idx.search("Plaza de Nariño", home) }
        assertTrue(hits.all { it.tier < 3 })
        val asked = runBlocking { idx.search("droguería Plaza de Nariño", home) }
        assertEquals(3, asked.first().tier)
    }

    @Test fun aNicknameGoesToTheVerifiedOnlineNameFirst() {
        val square = Place("Plaza de Nariño", "Centro", GeoCoord(1.2147, -77.2783))
        val online = FakeOnline(mapOf("Plaza de Nariño" to listOf(square)))
        val r = resolve(PlaceRequest("Plaza de Nariño", fromAlias = true), online) as Resolution.Found
        assertEquals(square, r.places.first())
        assertEquals("Plaza de Nariño|bounded=false", online.queries.first())
        // Offline, the nickname still resolves through the local index.
        val offline = OnlinePlaceSearch { _, _, _ -> Outcome.Err(WgoFailure.Offline) }
        val r2 = runBlocking { PlaceResolver(index(), offline).resolve(PlaceRequest("Cubanitos", fromAlias = true), home, Language.ES) }
        assertTrue(r2 is Resolution.Found)
        assertTrue(LocalIntentParser.parse("llévame a la plaza", Language.ES).placeFromAlias)
    }

    @Test fun aNicknameStillResolves() {
        val i = LocalIntentParser.parse("llévame a torobajo", Language.ES)
        assertEquals("Universidad de Nariño", i.placeQuery)
    }

    @Test fun duplicatesWithin150mCollapseAndAtMostThreeRemain() {
        // P10: up to three candidates (the pick list); duplicates within 150 m still collapse.
        val r = resolve(PlaceRequest("Restaurante La Merced"), at = GeoCoord(1.2122, -77.2784)) as Resolution.Found
        assertTrue(r.places.size in 2..PlaceResolver.MAX_CANDIDATES)
        assertEquals(r.places.map { PlaceText.normalize(it.name) + it.coord }.distinct().size, r.places.size)
    }

    @Test fun notFoundOffersNearbyPartialMatches() {
        val r = resolve(PlaceRequest("Cubanitas Gourmet"))
        assertTrue(r is Resolution.NotFound)
        assertTrue((r as Resolution.NotFound).suggestions.any { it.name == "Cubanitos" })
        assertTrue(r.suggestions.size <= 2)
    }

    @Test fun neverMoreThanThreeRequests() {
        // Two same-tier online hits without a text hint match: the hint point costs the third (and last) request.
        val a = Place("Tienda Sol", "Barrio Uno", GeoCoord(1.214, -77.282))
        val b = Place("Tienda Sol", "Barrio Dos", GeoCoord(1.205, -77.270))
        val online = FakeOnline(mapOf("Tienda Sol" to listOf(a, b), "Unicentro" to listOf(Place("Unicentro", "", GeoCoord(1.2051, -77.2702)))))
        val resolver = PlaceResolver(index(), online, myPlaces)
        val r = runBlocking { resolver.resolve(PlaceRequest("Tienda Sol", hint = "Unicentro"), home, Language.ES) } as Resolution.Found
        assertTrue(online.queries.size <= PlaceResolver.MAX_REQUESTS)
        assertEquals(3, resolver.lastRequests)
        assertEquals(b, r.places.first()) // re-ranked by proximity to the hint point
    }

    @Test fun offlineWithNothingLocalIsAFailureNotAFarGuess() {
        val failing = OnlinePlaceSearch { _, _, _ -> Outcome.Err(WgoFailure.Offline) }
        val r = runBlocking { PlaceResolver(index(), failing).resolve(PlaceRequest("Zapatería Única"), home, Language.ES) }
        assertEquals(Resolution.Failed(WgoFailure.Offline), r)
    }

    // ---- 3. The language model never supplies coordinates ----

    @Test fun coordinatesInAModelReplyAreIgnored() {
        val i = LlmInterpreter.parseContent(
            """{"kind":"Navigate","name":"Cubanitos","hint":"Panamericana","lat":6.25,"lon":-75.61,""" +
                """"coordinates":[6.25,-75.61],"confidence":0.9}"""
        )!!
        assertEquals("Cubanitos", i.placeQuery)
        assertEquals("Panamericana", i.placeHint)
        assertFalse(i.toString().contains("6.25"))
    }

    @Test fun aCoordinateOnlyNameIsRejected() {
        val i = LlmInterpreter.parseContent("""{"kind":"Navigate","name":"1.2182, -77.2889","confidence":0.9}""")!!
        assertEquals(IntentKind.Unknown, i.kind)
        assertNull(i.placeQuery)
    }

    @Test fun theRequestStillCarriesAFullTranscriptUnderOneKilobyte() {
        val transcript = "llévame a Cubanitos de la Panamericana por favor que tengo mucha hambre y quiero un sándwich"
        val body = LlmInterpreter.requestBody("openai/gpt-oss-120b", transcript, Language.ES)
        assertTrue(body.toByteArray().size <= LlmInterpreter.MAX_PAYLOAD_BYTES)
        assertTrue("the transcript is not trimmed", body.contains("quiero un sándwich"))
    }
}

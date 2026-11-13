package com.wgo.navigator.places

import com.wgo.navigator.Language
import com.wgo.navigator.ai.AiCallBudget
import com.wgo.navigator.ai.AiCounterStore
import com.wgo.navigator.ai.ChoiceOption
import com.wgo.navigator.ai.LlmPlaceChooser
import com.wgo.navigator.ai.LocalIntentParser
import com.wgo.navigator.ai.PlaceAliases
import com.wgo.navigator.ai.PlaceChooser
import com.wgo.navigator.core.Outcome
import com.wgo.navigator.core.Place
import com.wgo.navigator.core.WgoFailure
import com.wgo.navigator.geo.ChoiceTriggers
import com.wgo.navigator.geo.OnlinePlaceSearch
import com.wgo.navigator.geo.PlaceCandidate
import com.wgo.navigator.geo.PlaceRequest
import com.wgo.navigator.geo.PlaceResolver
import com.wgo.navigator.geo.PlaceSource
import com.wgo.navigator.geo.Resolution
import com.wgo.navigator.net.FakeHttpServer
import com.wgo.navigator.net.HttpClient
import com.wgo.navigator.ui.map.GeoCoord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * P8: the obvious real place (docs/PLACE_SEARCH.md, "Landmarks, nicknames and the language model"), with the REAL
 * places index, a fake Nominatim and a fake or HTTP-faked language model.
 */
class PlaceAiChoiceTest {
    companion object {
        private val index: PlaceIndex by lazy {
            PlaceIndex { File("src/main/assets/places_pasto.tsv.gzip").inputStream() }.also { runBlocking { it.ensureLoaded() } }
        }
        private val home = GeoCoord(1.2170, -77.2885)

        /** Universidad Cooperativa de Colombia as Nominatim answered it on 2026-11-11 (P8 audit). */
        private val uccOsm = Place(
            "Universidad Cooperativa de Colombia", "Carrera 45, Pandiaco", GeoCoord(1.229757, -77.28989), category = "amenity:university"
        )
    }

    private class FakeOnline(val answers: Map<String, List<Place>> = emptyMap(), val offline: Boolean = false) : OnlinePlaceSearch {
        val queries = ArrayList<String>()
        override suspend fun once(query: String, center: GeoCoord, bounded: Boolean): Outcome<List<Place>> {
            queries += query
            if (offline) return Outcome.Err(WgoFailure.Offline)
            val hit = answers[query].orEmpty()
            return if (hit.isEmpty()) Outcome.Err(WgoFailure.NotFound) else Outcome.Ok(hit)
        }
    }

    private class FakeChooser(val pick: (List<ChoiceOption>) -> String? = { null }, val names: List<String> = emptyList()) : PlaceChooser {
        val chosen = ArrayList<List<ChoiceOption>>()
        var expansions = 0
        override suspend fun choose(utterance: String, options: List<ChoiceOption>, language: Language, destination: String?): String? {
            chosen += options
            return pick(options)
        }

        override suspend fun expand(utterance: String, words: String, language: Language): List<String> {
            expansions++
            return names
        }
    }

    private fun request(text: String, aiCallsUsed: Int = 0): PlaceRequest {
        val i = LocalIntentParser.parse(text, Language.ES)
        return PlaceRequest(i.placeQuery!!, i.placeHint, i.placeCategory, i.placeFromAlias, utterance = text, aiCallsUsed = aiCallsUsed)
    }

    private fun resolver(online: OnlinePlaceSearch, chooser: PlaceChooser? = null) = PlaceResolver(index, online, null, chooser)

    private fun resolve(req: PlaceRequest, online: OnlinePlaceSearch = FakeOnline(offline = true), chooser: PlaceChooser? = null) =
        runBlocking { resolver(online, chooser).resolve(req, home, Language.ES) }

    private fun Resolution.found() = this as Resolution.Found

    // ---- 1. Golden utterances ----

    @Test fun cooperativaUtterancesAllMeanTheUniversity() {
        for (text in listOf(
            "la cooperativa", "mi universidad la cooperativa", "ir a la cooperativa", "llévame a la cooperativa",
            "llévame a la universidad cooperativa", "vamos a la UCC", "quiero ir a mi U, la cooperativa"
        )) {
            val i = LocalIntentParser.parse(text, Language.ES)
            assertEquals(text, "Universidad Cooperativa de Colombia", i.placeQuery)
            assertTrue(text, i.placeFromAlias)
        }
    }

    @Test fun otherLandmarkUtterances() {
        val cases = mapOf(
            "la mariana" to "Universidad Mariana",
            "la plaza" to "Plaza de Nariño",
            "el hospital departamental" to "Hospital Departamental de Nariño",
            "la terminal" to "Terminal de Transportes de Pasto",
            "el estadio" to "Estadio Departamental Libertad",
            "cesmag" to "Universidad CESMAG",
            "la unad" to "UNAD",
            "unicentro" to "Centro Comercial Unicentro",
            "llévame a bomboná" to "Centro Comercial Bomboná",
            "llévame al centro comercial bombona" to "Centro Comercial Bomboná"
        )
        for ((text, place) in cases) assertEquals(text, place, LocalIntentParser.parse(text, Language.ES).placeQuery)
        // "el hospital" is generic: no nickname, a choice among real hospitals instead.
        val h = LocalIntentParser.parse("el hospital", Language.ES)
        assertEquals("hospital", h.placeQuery)
        assertFalse(h.placeFromAlias)
    }

    // ---- 2. "la cooperativa" end to end ----

    @Test fun laCooperativaOnlineOffersTheUniversityFirstWithNoModelCall() {
        val online = FakeOnline(mapOf("Universidad Cooperativa de Colombia" to listOf(uccOsm)))
        val chooser = FakeChooser()
        val r = resolve(request("llévame a la cooperativa"), online, chooser).found()
        assertEquals("Universidad Cooperativa de Colombia", r.places.first().name)
        assertEquals(listOf("Universidad Cooperativa de Colombia"), online.queries)
        assertTrue("a nickname answered by Nominatim needs no model", chooser.chosen.isEmpty() && chooser.expansions == 0)
    }

    @Test fun laCooperativaOfflineUsesTheIndexRowOfTheUniversity() {
        val r = resolve(request("mi universidad la cooperativa")).found()
        assertEquals("UCC Pasto", r.places.first().name)
        assertEquals("college_university", r.places.first().category)
        assertTrue(r.places.none { it.name.startsWith("Cooperativa") })
    }

    @Test fun everyNicknameIndexNameIsARealRow() {
        for (l in PlaceAliases.landmarks) for (n in l.indexNames) {
            assertTrue("$n (${l.search})", index.search(n, home, minTier = 3).isNotEmpty())
        }
    }

    // ---- 3. Landmark prior ----

    @Test fun laPlazaOfflineIsNeverAShopNamedAfterIt() {
        val r = resolve(request("llévame a la plaza"))
        val names = (r as? Resolution.Found)?.places?.map { it.name }.orEmpty()
        assertTrue(names.toString(), names.none { it.contains("Dentix") || it.contains("Drogueria") })
    }

    @Test fun unicentroAndBombonaOfflinePickTheMall() {
        val u = resolve(request("unicentro")).found().places.first()
        assertEquals("shopping_mall", u.category)
        val b = resolve(request("llévame al centro comercial bombona")).found().places.first()
        assertEquals("Centro Comercial y Artesanal Bomboná", b.name)
    }

    @Test fun aLandmarkBeatsANearerBusinessSharingTheToken() {
        val shop = PlaceCandidate(Place("Cooperativa Pasto Ahorro", "", home, category = "installment_loans"), 2, false, 300.0, 0.9f, PlaceSource.Index)
        val uni = PlaceCandidate(Place("Universidad Cooperativa Sede", "", home, category = "college_university"), 2, false, 2_000.0, 0.7f, PlaceSource.Index)
        assertEquals(uni, PlaceResolver.sortedCandidates(listOf(shop, uni)).first())
    }

    // ---- 4. The model's choice ----

    private fun hospitals(chooser: PlaceChooser?) = resolve(request("llévame al hospital"), chooser = chooser).found().places

    @Test fun aHallucinatedIdIsIgnored() {
        val chooser = FakeChooser(pick = { "c9" })
        assertEquals(hospitals(null), hospitals(chooser))
        assertEquals(1, chooser.chosen.size)
    }

    @Test fun aValidIdReordersTheCandidates() {
        val local = resolve(request("llévame al hospital")).found()
        val secondName = PlaceResolver.dedupe(PlaceResolver.sortedCandidates(local.candidates)).let { local.places[1].name }
        val chooser = FakeChooser(pick = { "c2" })
        val picked = hospitals(chooser)
        assertEquals(secondName, picked.first().name)
        assertTrue(chooser.chosen.single().all { it.id.matches(Regex("c[1-5]")) })
    }

    @Test fun noAnswerKeepsTheLocalRanking() {
        assertEquals(hospitals(null), hospitals(FakeChooser(pick = { null })))
    }

    @Test fun aClearExactMatchNeverAsksTheModel() {
        val chooser = FakeChooser(pick = { "c2" })
        resolve(request("llévame a la Universidad Mariana a pie"), chooser = chooser)
        val exact = PlaceCandidate(Place("Cubanitos", "", home), 3, false, 100.0, 0.95f, PlaceSource.Index)
        val other = PlaceCandidate(Place("Cuabanitos Express", "", home), 2, false, 900.0, 0.9f, PlaceSource.Index)
        assertFalse(ChoiceTriggers.shouldChoose(PlaceRequest("Cubanitos", utterance = "llévame a Cubanitos"), listOf(exact, other)))
        assertTrue(chooser.chosen.isEmpty())
    }

    // ---- 5. Expansion ----

    @Test fun withoutTheNicknameTheExpansionFindsTheUniversity() {
        val online = FakeOnline(mapOf("Universidad Cooperativa de Colombia" to listOf(uccOsm)))
        val chooser = FakeChooser(names = listOf("Universidad Cooperativa de Colombia"))
        // A request as if no nickname existed: two unrelated "Cooperativa ..." rows are all the index has.
        val req = PlaceRequest("cooperativa", utterance = "llévame a la cooperativa")
        val resolver = resolver(online, chooser)
        val r = runBlocking { resolver.resolve(req, home, Language.ES) }.found()
        assertEquals("Universidad Cooperativa de Colombia", r.places.first().name)
        assertEquals(1, chooser.expansions)
        assertTrue(resolver.lastAiCalls <= PlaceResolver.MAX_AI_CALLS)
        assertTrue(online.queries.size <= PlaceResolver.MAX_REQUESTS)
    }

    @Test fun expansionNamesAreTextOnly() {
        val names = LlmPlaceChooser.parseExpansion(
            """{"names":["Universidad Cooperativa de Colombia","1.2297, -77.2899",42,"UCC Pasto","x"],"lat":1.2,"lon":-77.2}"""
        )
        assertEquals(listOf("Universidad Cooperativa de Colombia", "UCC Pasto"), names)
        assertEquals(emptyList<String>(), LlmPlaceChooser.parseExpansion("no json at all"))
    }

    // ---- 6. Validation, privacy, limits ----

    @Test fun onlyAnIdFromTheListIsAccepted() {
        val ids = setOf("c1", "c2", "c3")
        assertEquals("c2", LlmPlaceChooser.parseChoice("""{"id":"c2","lat":1.23,"lon":-77.28}""", ids))
        assertNull(LlmPlaceChooser.parseChoice("""{"id":"c7"}""", ids))
        assertNull(LlmPlaceChooser.parseChoice("""{"id":null}""", ids))
        assertNull(LlmPlaceChooser.parseChoice("""{"id":2}""", ids))
        assertNull(LlmPlaceChooser.parseChoice("garbage", ids))
    }

    @Test fun noCoordinatesDistancesOrPositionInAnyModelRequest() {
        val r = resolve(request("llévame al hospital")).found()
        val options = r.candidates.mapIndexed { i, c -> ChoiceOption("c${i + 1}", c.place.name, c.place.category, c.place.address) }
        val bodies = listOf(
            LlmPlaceChooser.choiceBody("m", "llévame al hospital", options, Language.ES),
            LlmPlaceChooser.expansionBody("m", "llévame a la cooperativa", "cooperativa", Language.ES)
        )
        val coords = (r.candidates.map { it.place.coord } + home).flatMap {
            listOf(String.format(Locale.US, "%.3f", it.lat), String.format(Locale.US, "%.3f", it.lon))
        }
        for (body in bodies) {
            for (c in coords) assertFalse("$c in $body", body.contains(c))
            for (k in listOf("\"lat\"", "\"lon\"", "latitude", "longitude", "distance", "\"km\"", "metros")) assertFalse(k, body.contains(k))
            r.candidates.forEach { assertFalse(body.contains(it.distanceM.toInt().toString() + " m")) }
        }
    }

    @Test fun neverMoreThanTwoModelCallsPerUtterance() {
        // Weak AND ambiguous: an expansion and a choice would both apply.
        val names = listOf("Cooperativa Nariñense de Militares Retirados", "Cooperativa Multiactiva Prospera")
        for (used in 0..2) {
            val chooser = FakeChooser(pick = { "c2" }, names = names)
            val resolver = resolver(FakeOnline(offline = true), chooser)
            runBlocking { resolver.resolve(PlaceRequest("cooperativa", utterance = "mi cooperativa", aiCallsUsed = used), home, Language.ES) }
            assertTrue("used $used", used + resolver.lastAiCalls <= PlaceResolver.MAX_AI_CALLS)
            assertEquals("used $used", chooser.chosen.size + chooser.expansions, resolver.lastAiCalls)
        }
    }

    // ---- 7. The real HTTP chooser: timeout, rate limit, garbage, request content ----

    private class MemoryStore : AiCounterStore {
        var value: Pair<String, Int>? = null
        override suspend fun load() = value
        override suspend fun save(day: String, count: Int) { value = day to count }
    }

    private fun answer(content: String) =
        """{"choices":[{"message":{"content":${org.json.JSONObject.quote(content)}}}],"usage":{"total_tokens":42}}"""

    private val options = listOf(ChoiceOption("c1", "Cooperativa Nariñense", "party_and_event_planning", "Cra. 29 #16 25"),
        ChoiceOption("c2", "UCC Pasto", "college_university", "Calle 18 45-150"))

    @Test fun theHttpChooserTimesOutAt2500msAndFallsBack() {
        FakeHttpServer { Triple(200, answer("""{"id":"c2"}"""), 4_000L) }.use { server ->
            val chooser = LlmPlaceChooser("k", server.baseUrl, "m", HttpClient("test"), AiCallBudget(MemoryStore(), nowMs = { System.currentTimeMillis() }))
            val start = System.nanoTime()
            val id = runBlocking { chooser.choose("la cooperativa", options, Language.ES) }
            val ms = (System.nanoTime() - start) / 1_000_000
            assertNull(id)
            assertTrue("took $ms ms", ms < 3_500)
        }
    }

    @Test fun theHttpChooserHonoursRateLimitsGarbageAndTheCache() {
        var reply = Triple(429, """{"error":{"code":"rate_limit_exceeded"}}""", 0L)
        FakeHttpServer { reply }.use { server ->
            val budget = AiCallBudget(MemoryStore(), nowMs = { System.currentTimeMillis() })
            val chooser = LlmPlaceChooser("k", server.baseUrl, "m", HttpClient("test"), budget)
            assertNull(runBlocking { chooser.choose("la cooperativa", options, Language.ES) })
            assertTrue(budget.coolingDown())
        }
        reply = Triple(200, answer("I think it's the university!"), 0L)
        FakeHttpServer { reply }.use { server ->
            val chooser = LlmPlaceChooser("k", server.baseUrl, "m", HttpClient("test"), AiCallBudget(MemoryStore(), nowMs = { System.currentTimeMillis() }))
            assertNull(runBlocking { chooser.choose("la cooperativa", options, Language.ES) })
        }
        reply = Triple(200, answer("""{"id":"c2"}"""), 0L)
        FakeHttpServer { reply }.use { server ->
            val chooser = LlmPlaceChooser("k", server.baseUrl, "m", HttpClient("test"), AiCallBudget(MemoryStore(), nowMs = { System.currentTimeMillis() }))
            assertEquals("c2", runBlocking { chooser.choose("la cooperativa", options, Language.ES) })
            assertEquals("c2", runBlocking { chooser.choose("La Cooperativa", options, Language.ES) })
            assertEquals("the second answer came from the cache", 1, server.requests.size)
            val sent = server.requests.single().body
            assertTrue(sent.contains("UCC Pasto") && sent.contains("la cooperativa"))
            assertFalse(sent.contains("1.2") || sent.contains("-77"))
        }
    }
}

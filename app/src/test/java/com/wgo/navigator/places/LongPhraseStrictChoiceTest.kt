package com.wgo.navigator.places

import com.wgo.navigator.Language
import com.wgo.navigator.ai.AiCallBudget
import com.wgo.navigator.ai.AiCounterStore
import com.wgo.navigator.ai.ChoiceOption
import com.wgo.navigator.ai.InterpreterTier
import com.wgo.navigator.ai.LlmInterpreter
import com.wgo.navigator.ai.LlmPlaceChooser
import com.wgo.navigator.ai.LocalIntentParser
import com.wgo.navigator.ai.PlaceChooser
import com.wgo.navigator.ai.TieredInterpreter
import com.wgo.navigator.core.IntentKind
import com.wgo.navigator.core.Outcome
import com.wgo.navigator.core.Place
import com.wgo.navigator.core.WgoFailure
import com.wgo.navigator.geo.OnlinePlaceSearch
import com.wgo.navigator.geo.PlaceRequest
import com.wgo.navigator.geo.PlaceResolver
import com.wgo.navigator.geo.PrivatePlaces
import com.wgo.navigator.geo.Resolution
import com.wgo.navigator.net.FakeHttpServer
import com.wgo.navigator.net.HttpClient
import com.wgo.navigator.ui.map.GeoCoord
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * P10 golden cases (docs/design "Long phrases, strict place choice and the heard phrase"): text only, the REAL places
 * index, fake Nominatim, fake or HTTP-faked language model.
 */
class LongPhraseStrictChoiceTest {
    companion object {
        private val index: PlaceIndex by lazy {
            PlaceIndex { File("src/main/assets/places_pasto.tsv.gzip").inputStream() }.also { runBlocking { it.ensureLoaded() } }
        }
        private val home = GeoCoord(1.2170, -77.2885)
        private val ucc = Place("Universidad Cooperativa de Colombia", "Carrera 45, Pandiaco", GeoCoord(1.229757, -77.28989), category = "amenity:university")

        /** The owner's field sentence and its variants (STEP 0 item 3). */
        val COOPERATIVA = listOf(
            "por favor amigo quiero ir a la cooperativa es lo único que te pido",
            "por favor amigo, quiero ir a la cooperativa, es lo único que te pido. Y bueno, más cosas",
            "Por favor, amigo, quiero ir a la cooperativa. Es lo único que te pido. Y bueno, más cosas.",
            "oye quiero ir a la cooperativa por favor",
            "llévame a la cooperativa gracias",
            "llévame a la cooperativa"
        )
    }

    private class Online(val offline: Boolean = false, val answers: Map<String, List<Place>> = emptyMap()) : OnlinePlaceSearch {
        val queries = ArrayList<String>()
        override suspend fun once(query: String, center: GeoCoord, bounded: Boolean): Outcome<List<Place>> {
            queries += query
            if (offline) return Outcome.Err(WgoFailure.Offline)
            return answers[query]?.let { Outcome.Ok(it) } ?: Outcome.Err(WgoFailure.NotFound)
        }
    }

    private class Chooser(val pick: (List<ChoiceOption>) -> String?) : PlaceChooser {
        var calls = 0
        override suspend fun choose(utterance: String, options: List<ChoiceOption>, language: Language, destination: String?): String? {
            calls++
            return pick(options)
        }
        override suspend fun expand(utterance: String, words: String, language: Language): List<String> = emptyList()
    }

    private class MemoryStore : AiCounterStore {
        var value: Pair<String, Int>? = null
        override suspend fun load() = value
        override suspend fun save(day: String, count: Int) {
            value = day to count
        }
    }

    private fun request(text: String): PlaceRequest {
        val i = LocalIntentParser.parse(text, Language.ES)
        return PlaceRequest(i.placeQuery!!, i.placeHint, i.placeCategory, i.placeFromAlias, utterance = text, vague = i.placeVague)
    }

    private fun resolve(req: PlaceRequest, online: OnlinePlaceSearch, chooser: PlaceChooser? = null, withIndex: Boolean = true) =
        runBlocking { PlaceResolver(if (withIndex) index else null, online, null, chooser).resolve(req, home, Language.ES) }

    // ---- long phrases ----

    @Test fun everyCooperativaPhraseMeansTheUniversityOnlineAndOffline() {
        for (text in COOPERATIVA) {
            val i = LocalIntentParser.parse(text, Language.ES)
            assertEquals(text, "Universidad Cooperativa de Colombia", i.placeQuery)
            assertTrue(text, i.placeFromAlias)
            assertEquals(text, "la cooperativa", i.placeHeard)
            val on = resolve(request(text), Online(answers = mapOf("Universidad Cooperativa de Colombia" to listOf(ucc)))) as Resolution.Found
            assertEquals(text, "Universidad Cooperativa de Colombia", on.places.first().name)
            assertFalse("a clear nickname goes straight to Confirm place", on.uncertain)
            val off = resolve(request(text), Online(offline = true)) as Resolution.Found
            assertEquals(text, "UCC Pasto", off.places.first().name)
            assertTrue(off.places.none { it.name.startsWith("Cooperativa") })
        }
    }

    @Test fun theTerminalPhraseKeepsOnlyTheDestination() {
        val i = LocalIntentParser.parse("amigo necesito llegar a la terminal ya", Language.ES)
        assertEquals("Terminal de Transportes de Pasto", i.placeQuery)
        assertTrue(i.placeFromAlias)
    }

    @Test fun aRequestForHelpIsNeverADestination() {
        for (text in listOf("por favor ayúdame", "ayúdame", "help me please")) {
            assertEquals(text, IntentKind.Unknown, LocalIntentParser.parse(text, Language.ES).kind)
        }
    }

    @Test fun aVagueRequestNeverAutoPicks() {
        val text = "quiero ir a un lugar donde pueda comer algo"
        val i = LocalIntentParser.parse(text, Language.ES)
        assertTrue(i.placeVague)
        assertTrue("goes to the remote interpreter", i.confidence < 0.75f)
        for (category in listOf(null, "restaurante")) {
            val r = resolve(request(text).copy(category = category), Online(offline = true))
            assertTrue("$category: $r", r !is Resolution.Found || r.uncertain)
        }
    }

    @Test fun aVeryLongPlaceGoesToTheRemoteExtractor() {
        val i = LocalIntentParser.parse("llévame a la tienda de la señora que vende empanadas cerca del parque grande del barrio", Language.ES)
        assertTrue(i.confidence < 0.75f)
    }

    @Test fun theModelsPhraseMustBeMadeOfTheUsersWords() {
        val said = "por favor amigo quiero ir a la cooperativa es lo único que te pido"
        assertTrue(LocalIntentParser.groundedIn("la cooperativa", said))
        assertTrue(LocalIntentParser.groundedIn("Cooperativa", said))
        assertFalse(LocalIntentParser.groundedIn("Universidad Cooperativa de Colombia", said))
        assertFalse(LocalIntentParser.groundedIn("Cooperativa Nariñense de Militares Retirados", said))
        assertFalse(LocalIntentParser.groundedIn("", said))
    }

    @Test fun anUngroundedExtractorPhraseIsIgnored() = runBlocking {
        val said = "quiero ir a un lugar donde pueda comer algo rico por favor"
        val invented = """{"kind":"Navigate","name":"Restaurante El Chalet","mode":null,"confidence":0.9}"""
        FakeHttpServer { Triple(200, chat(invented), 0L) }.use { server ->
            val t = tiered(server.baseUrl)
            val r = t.interpret(said, Language.ES)
            assertEquals(InterpreterTier.Local, t.lastTier)
            assertEquals("lugar donde pueda comer algo rico", r.placeQuery)
        }
        val copied = """{"kind":"Navigate","name":"lugar donde pueda comer","category":"restaurante","mode":null,"confidence":0.8}"""
        FakeHttpServer { Triple(200, chat(copied), 0L) }.use { server ->
            val t = tiered(server.baseUrl)
            val r = t.interpret(said, Language.ES)
            assertEquals(InterpreterTier.Remote, t.lastTier)
            assertEquals("lugar donde pueda comer", r.placeQuery)
            assertTrue("the vague flag survives the remote answer", r.placeVague)
        }
    }

    // ---- kinds of place and addresses (P10 live session) ----

    private fun requestOf(text: String): PlaceRequest {
        val i = LocalIntentParser.parse(text, Language.ES)
        return PlaceRequest(
            i.placeQuery!!, i.placeHint, i.placeCategory, i.placeFromAlias, utterance = text, vague = i.placeVague, address = i.placeIsAddress
        )
    }

    @Test fun aKindOfPlaceNearAHintListsRealPlacesOfThatKind() {
        val centro = Place("Centro", "Pasto", GeoCoord(1.2146, -77.2780))
        val text = "Llévame a alguna droguería que quede en el centro, porque no conozco Pasto porque soy de Bogotá."
        val i = LocalIntentParser.parse(text, Language.ES)
        assertEquals("droguería", i.placeQuery)
        assertEquals("droguería", i.placeCategory)
        assertEquals("centro", i.placeHint)
        assertTrue(i.placeVague)
        val r = resolve(requestOf(text), Online(answers = mapOf("centro, Pasto" to listOf(centro)))) as Resolution.Found
        assertTrue(r.uncertain)
        assertTrue(r.places.size in 1..3)
        assertTrue(r.places.all { it.category.contains("pharmacy") || it.category.contains("drugstore") })
    }

    @Test fun theBestRestaurantOnAnAvenueIsACategoryNotTheWordBest() {
        val text = "Llévame por favor al mejor restaurante de la avenida de los estudiantes porque tengo mucha hambre."
        val i = LocalIntentParser.parse(text, Language.ES)
        assertEquals("restaurante", i.placeQuery)
        assertEquals("avenida de los estudiantes", i.placeHint)
        val avenue = Place("Avenida de los Estudiantes", "Pasto", GeoCoord(1.2128, -77.2858))
        val r = resolve(requestOf(text), Online(answers = mapOf("avenida de los estudiantes, Pasto" to listOf(avenue)))) as Resolution.Found
        assertTrue(r.uncertain)
        assertTrue(r.places.none { "mejor" in it.name.lowercase() })
        assertTrue(r.places.all { listOf("restaurant", "eatery", "food").any { f -> it.category.contains(f) } })
    }

    @Test fun anAddressIsSearchedAsAnAddressAndNeverAsABusiness() {
        val text = "Llévame a la calle 6A #36-41, barrio San Vicente, porque ahí es mi casa."
        val i = LocalIntentParser.parse(text, Language.ES)
        assertTrue(i.placeIsAddress)
        assertEquals("Calle 6A #36-41", i.placeQuery)
        assertEquals("San Vicente", i.placeHint)
        val street = Place("Calle 6A", "Mariluz II, Comuna 8", GeoCoord(1.2190, -77.2900), category = "highway:residential")
        val inBarrio = Place("Calle 6A", "San Vicente, Comuna 4", GeoCoord(1.2050, -77.2700), category = "highway:residential")
        val online = Online(answers = mapOf("Calle 6A 36-41, Pasto" to listOf(street), "Calle 6A, San Vicente, Pasto" to listOf(inBarrio)))
        val r = resolve(requestOf(text), online) as Resolution.Found
        assertTrue("always the pick list", r.uncertain)
        assertEquals("the street in the neighbourhood said", inBarrio, r.places.first())
        assertTrue(online.queries.size <= PlaceResolver.MAX_REQUESTS)
        // Nothing found: not found, and never business suggestions ("Arturo Calle").
        val none = resolve(requestOf(text), Online())
        assertEquals(Resolution.NotFound(emptyList()), none)
        assertTrue(resolve(requestOf(text), Online(offline = true)) is Resolution.Failed)
    }

    @Test fun addressShapes() {
        for ((text, query) in listOf(
            "llévame a la carrera 27 número 15-30" to "Carrera 27 #15-30",
            "calle 18 # 24-15" to "Calle 18 #24-15",
            "vamos a la avenida panamericana" to null
        )) {
            val i = LocalIntentParser.parse(text, Language.ES)
            if (query == null) assertFalse(text, i.placeIsAddress) else {
                assertTrue(text, i.placeIsAddress)
                assertEquals(text, query, i.placeQuery)
            }
        }
    }

    // ---- strict choice ----

    private val hospitalA = Place("Hospital Infantil Los Ángeles", "Calle 18", GeoCoord(1.2190, -77.2830), category = "amenity:hospital")
    private val pharmacy = Place("Droguería Central", "Calle 19", GeoCoord(1.2160, -77.2850), category = "amenity:pharmacy")
    private val hospitalB = Place("Hospital San Pedro", "Calle 16", GeoCoord(1.2140, -77.2860), category = "amenity:hospital")
    private val tie = Online(answers = mapOf("hospital" to listOf(hospitalA, pharmacy, hospitalB)))

    @Test fun aPickUnrelatedToTheDestinationIsIgnoredAndTheUserChooses() {
        val chooser = Chooser { o -> o.first { it.name == "Droguería Central" }.id }
        val r = resolve(PlaceRequest("hospital", utterance = "llévame al hospital"), tie, chooser, withIndex = false) as Resolution.Found
        assertEquals(1, chooser.calls)
        assertTrue(r.uncertain)
        assertTrue(r.places.size in 2..3)
        assertTrue(r.places.first().name.startsWith("Hospital"))
    }

    @Test fun aNullAnswerGivesThePickListNotAGuess() {
        val r = resolve(PlaceRequest("hospital", utterance = "llévame al hospital"), tie, Chooser { null }, withIndex = false) as Resolution.Found
        assertTrue(r.uncertain)
    }

    @Test fun aRelatedPickIsPromotedAndClear() {
        val chooser = Chooser { o -> o.first { it.name == "Hospital San Pedro" }.id }
        val r = resolve(PlaceRequest("hospital", utterance = "llévame al hospital"), tie, chooser, withIndex = false) as Resolution.Found
        assertEquals("Hospital San Pedro", r.places.first().name)
        assertFalse(r.uncertain)
    }

    @Test fun relatedness() {
        assertTrue(PlaceResolver.relatedToDestination("UCC Pasto", "Universidad Cooperativa de Colombia"))
        assertTrue(PlaceResolver.relatedToDestination("Universidad Cooperativa de Colombia", "cooperativa"))
        assertFalse(PlaceResolver.relatedToDestination("Peluqueria canina mi mejor amigo", "cooperativa"))
        assertFalse(PlaceResolver.relatedToDestination("Pasto Salud ESE", "Terminal de Transportes de Pasto"))
    }

    @Test fun aChooserThatTimesOutOrAnswersGarbageLeavesTheLocalRankingAndThePickList() = runBlocking {
        for ((code, body, delay) in listOf(Triple(200, chat("{\"id\":\"c1\""), 0L), Triple(200, "garbage", 0L), Triple(200, chat("{\"id\":\"c2\"}"), 1_500L))) {
            FakeHttpServer { Triple(code, body, delay) }.use { server ->
                val chooser = LlmPlaceChooser("k", server.baseUrl, "m", HttpClient("WGO-test"), budget(), timeoutMs = 500L)
                val r = resolve(PlaceRequest("hospital", utterance = "llévame al hospital"), tie, chooser, withIndex = false) as Resolution.Found
                assertTrue(r.uncertain)
                assertTrue("the local ranking stands (a hospital, never the pharmacy)", r.places.first().name.startsWith("Hospital"))
            }
        }
    }

    // ---- privacy and budget ----

    @Test fun modelRequestsCarryNoPositionDistanceCoordinatesOrMyPlaces() = runBlocking {
        val secretHome = Place("Casa de la abuela Rosario", "Vereda secreta", GeoCoord(1.20001, -77.30002))
        val mine = object : PrivatePlaces {
            override suspend fun match(name: String): Place? = null
        }
        FakeHttpServer { Triple(200, chat("{\"id\":null}"), 0L) }.use { server ->
            val chooser = LlmPlaceChooser("k", server.baseUrl, "m", HttpClient("WGO-test"), budget(), timeoutMs = 2_000L)
            val r = runBlocking {
                PlaceResolver(null, tie, mine, chooser).resolve(PlaceRequest("hospital", utterance = "llévame al hospital"), home, Language.ES)
            }
            assertTrue(r is Resolution.Found)
            val bodies = server.requests.map { it.body }
            assertTrue(bodies.isNotEmpty())
            for (b in bodies) {
                val user = JSONObject(JSONObject(b).getJSONArray("messages").getJSONObject(1).getString("content")).toString()
                assertFalse(user, Regex("-?\\d{1,3}\\.\\d{3,}").containsMatchIn(user)) // no coordinates
                for (word in listOf("lat", "lon", "distance", "dist", "position", "km\"", secretHome.name, "Vereda")) {
                    assertFalse("$word in $user", user.contains(word, ignoreCase = true))
                }
            }
        }
    }

    @Test fun atMostTwoModelCallsPerUtterance() {
        val chooser = Chooser { null }
        val resolver = PlaceResolver(null, tie, null, chooser)
        runBlocking { resolver.resolve(PlaceRequest("hospital", utterance = "llévame al hospital", aiCallsUsed = 1), home, Language.ES) }
        assertTrue(resolver.lastAiCalls <= 1)
        val spent = PlaceResolver(null, tie, null, Chooser { null })
        runBlocking { spent.resolve(PlaceRequest("hospital", utterance = "llévame al hospital", aiCallsUsed = 2), home, Language.ES) }
        assertEquals(0, spent.lastAiCalls)
    }

    @Test fun clearWinnerRules() {
        val req = PlaceRequest("x")
        assertNull((resolve(PlaceRequest("lugar donde comer", vague = true), Online(offline = true), withIndex = false) as? Resolution.Found))
        assertFalse(PlaceResolver.clearWinner(req, emptyList(), PlaceResolver.ChoiceOutcome.NotAsked))
    }

    private fun budget() = AiCallBudget(MemoryStore(), 200, 60_000L, { 0L }, { "2026-11-12" })

    private fun tiered(base: String) = TieredInterpreter(
        LlmInterpreter("k", base, "openai/gpt-oss-120b", HttpClient("WGO-test")), { true }, budget()
    )

    private fun chat(content: String): String = JSONObject()
        .put("choices", org.json.JSONArray().put(JSONObject().put("message", JSONObject().put("role", "assistant").put("content", content))))
        .put("usage", JSONObject().put("total_tokens", 10))
        .toString()
}

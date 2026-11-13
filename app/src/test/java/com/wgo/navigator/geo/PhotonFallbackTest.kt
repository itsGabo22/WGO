package com.wgo.navigator.geo

import com.wgo.navigator.Language
import com.wgo.navigator.core.Outcome
import com.wgo.navigator.core.Place
import com.wgo.navigator.core.WgoFailure
import com.wgo.navigator.net.FakeHttpServer
import com.wgo.navigator.net.HttpClient
import com.wgo.navigator.ui.map.GeoCoord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** P11 Photon fallback (docs/PLACE_SEARCH.md, "Fallback order and budgets") against a local fake server. */
class PhotonFallbackTest {
    private val user = GeoCoord(1.23456, -77.29876)

    private val misterPollo = """
        {"type":"FeatureCollection","features":[
         {"type":"Feature","geometry":{"type":"Point","coordinates":[-77.2869,1.2340]},
          "properties":{"osm_key":"amenity","osm_value":"fast_food","name":"Míster Pollo norte","street":"Carrera 44B","city":"Pasto"}},
         {"type":"Feature","geometry":{"type":"Point","coordinates":[-77.2797,1.2037]},
          "properties":{"osm_key":"amenity","osm_value":"fast_food","name":"Míster Pollo Avenida Boyacá","city":"Pasto"}},
         {"type":"Feature","geometry":{"type":"Point","coordinates":[-77.2811,1.2136]},
          "properties":{"osm_key":"shop","osm_value":"butcher","name":"Gran Pollo Fresco","city":"Pasto"}},
         {"type":"Feature","geometry":{"type":"Point","coordinates":[-3.70,40.41]},
          "properties":{"osm_key":"amenity","osm_value":"restaurant","name":"Mister Pollo","city":"Madrid"}},
         {"type":"Feature","geometry":{"type":"Point","coordinates":[-77.28,1.21]},"properties":{"osm_key":"highway"}}
        ]}
    """.trimIndent()

    private class Nominatim(val answer: Outcome<List<Place>> = Outcome.Err(WgoFailure.NotFound)) : OnlinePlaceSearch {
        val queries = ArrayList<String>()
        override suspend fun once(query: String, center: GeoCoord, bounded: Boolean): Outcome<List<Place>> {
            queries += query
            return answer
        }
    }

    @Test fun theRequestHoldsTheWordsAndAFixedBoxNeverTheUsersPosition() = runBlocking {
        FakeHttpServer { Triple(200, misterPollo, 0L) }.use { server ->
            val photon = PhotonGeocoder(HttpClient("WGO-test (contact)"), server.baseUrl, RateLimiter(0))
            photon.once("mr pollo", user, bounded = false)
            val req = server.requests.single()
            assertEquals("/api/", req.path)
            val q = req.query!!
            assertTrue(q, q.contains("q=mr+pollo"))
            assertTrue(q, q.contains("lat=1.2136&lon=-77.2811"))
            assertTrue(q, q.contains("bbox=-77.506,0.989,-77.056,1.438"))
            assertFalse(q, q.contains("1.2345") || q.contains("77.298"))
            assertEquals("WGO-test (contact)", req.headers["user-agent"])
        }
    }

    @Test fun parsesNamesOsmTagsAndSubtitles() {
        val places = PhotonGeocoder.parse(misterPollo)!!
        assertEquals(4, places.size) // the nameless feature is skipped
        assertEquals("Míster Pollo norte", places[0].name)
        assertEquals("amenity:fast_food", places[0].category)
        assertEquals("Carrera 44B, Pasto", places[0].address)
        assertEquals(1.2340, places[0].coord.lat, 1e-9)
        assertEquals(null, PhotonGeocoder.parse("<html>busy</html>"))
    }

    @Test fun answersAreCachedAndSpacedByTheProcessLimiter() = runBlocking {
        var now = 0L
        val slept = ArrayList<Long>()
        val limiter = RateLimiter(PhotonGeocoder.MIN_INTERVAL_MS, { now }, { slept += it; now += it })
        FakeHttpServer { Triple(200, misterPollo, 0L) }.use { server ->
            val photon = PhotonGeocoder(HttpClient("WGO-test"), server.baseUrl, limiter)
            photon.once("mr pollo", user, false)
            photon.once("MR POLLO", user, false) // cache: no request
            photon.once("lili pink", user, false)
            assertEquals(2, server.requests.size)
            assertEquals(listOf(PhotonGeocoder.MIN_INTERVAL_MS), slept)
        }
    }

    @Test fun photonAnswersWhenTheIndexAndNominatimHaveNothingWithinTheBudget() = runBlocking {
        FakeHttpServer { Triple(200, misterPollo, 0L) }.use { server ->
            val nominatim = Nominatim()
            val resolver = PlaceResolver(null, nominatim, fallback = PhotonGeocoder(HttpClient("WGO-test"), server.baseUrl, RateLimiter(0)))
            val r = resolver.resolve(PlaceRequest("mr pollo", utterance = "llévame a mr pollo"), user, Language.ES) as Resolution.Found
            assertTrue(r.places.all { it.name.startsWith("Míster Pollo") }) // the butcher and Madrid are dropped
            assertTrue(r.candidates.all { it.source == PlaceSource.Photon })
            assertEquals("Míster Pollo norte", r.places.first().name) // nearest branch first
            assertEquals(1, server.requests.size)
            assertTrue(resolver.lastRequests <= PlaceResolver.MAX_REQUESTS)
            assertEquals(nominatim.queries.size + 1, resolver.lastRequests)
        }
    }

    @Test fun photonIsNotAskedWhenNominatimFoundItOrTheNetworkIsDown() = runBlocking {
        FakeHttpServer { Triple(200, misterPollo, 0L) }.use { server ->
            val near = Place("Mister Pollo", "Calle 18", GeoCoord(1.2210, -77.2830))
            val found = PlaceResolver(null, Nominatim(Outcome.Ok(listOf(near))), fallback = PhotonGeocoder(HttpClient("WGO-test"), server.baseUrl, RateLimiter(0)))
            found.resolve(PlaceRequest("mister pollo"), user, Language.ES)
            val offline = PlaceResolver(null, Nominatim(Outcome.Err(WgoFailure.Offline)), fallback = PhotonGeocoder(HttpClient("WGO-test"), server.baseUrl, RateLimiter(0)))
            val r = offline.resolve(PlaceRequest("mr pollo"), user, Language.ES)
            assertTrue(r is Resolution.Failed)
            assertEquals(0, server.requests.size)
        }
    }
}

package com.wgo.navigator.geo

import com.wgo.navigator.Language
import com.wgo.navigator.TravelMode
import com.wgo.navigator.core.Maneuver
import com.wgo.navigator.core.Maneuver.ARRIVE
import com.wgo.navigator.core.Maneuver.DEPART
import com.wgo.navigator.core.Maneuver.LEFT
import com.wgo.navigator.core.Maneuver.RIGHT
import com.wgo.navigator.core.Outcome
import com.wgo.navigator.core.RouteStep
import com.wgo.navigator.core.WgoFailure
import com.wgo.navigator.net.FakeHttpServer
import com.wgo.navigator.net.HttpClient
import com.wgo.navigator.route.ManeuverText
import com.wgo.navigator.route.OsrmRouter
import com.wgo.navigator.route.Polyline
import com.wgo.navigator.ui.map.GeoCoord
import com.wgo.navigator.ui.map.PastoCentre
import com.wgo.navigator.ui.map.RadarProjection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos

class GeoRouteParsingTest {
    private val udenar = GeoCoord(1.23253, -77.29339)

    // ---- polyline ----

    @Test fun decodesTheReferenceVector() {
        val p = Polyline.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(3, p.size)
        assertEquals(38.5, p[0].lat, 1e-9); assertEquals(-120.2, p[0].lon, 1e-9)
        assertEquals(40.7, p[1].lat, 1e-9); assertEquals(-120.95, p[1].lon, 1e-9)
        assertEquals(43.252, p[2].lat, 1e-9); assertEquals(-126.453, p[2].lon, 1e-9)
        assertTrue("a truncated string does not crash", Polyline.decode("_p~iF~ps|U_ul").size == 1)
    }

    @Test fun simplificationKeepsEndpointsAndStaysWithinTolerance() {
        val raw = Polyline.decode(geometry("osrm_foot.json"))
        val simple = Polyline.simplify(raw, 4.0, 300)
        assertEquals(raw.first(), simple.first())
        assertEquals(raw.last(), simple.last())
        assertTrue(simple.size <= raw.size && simple.size <= 300)
        val worst = raw.maxOf { distanceToPolyline(it, simple) }
        assertTrue("worst deviation $worst m", worst <= 4.0 + 1e-6)
    }

    @Test fun simplificationRespectsThePointCap() {
        // A 2000-point zig-zag with 10 m teeth cannot fit 300 points at 4 m: the tolerance grows instead.
        val zig = (0 until 2000).map { i -> GeoCoord(1.2 + i * 0.00005, -77.28 + if (i % 2 == 0) 0.0 else 0.00009) }
        val simple = Polyline.simplify(zig, 4.0, 300)
        assertTrue(simple.size <= 300)
        assertEquals(zig.first(), simple.first()); assertEquals(zig.last(), simple.last())
        val straight = (0 until 500).map { GeoCoord(1.2 + it * 0.0001, -77.28) }
        assertEquals(2, Polyline.simplify(straight).size)
    }

    // ---- OSRM ----

    @Test fun parsesTheRealFootSample() {
        val r = (OsrmRouter.parse(resource("osrm_foot.json"), TravelMode.Walk) as Outcome.Ok).value
        assertEquals(2902.7, r.distanceM, 0.05)
        assertEquals(2326.5, r.durationS, 0.05)
        // depart/right, turn/right, turn/left, turn/right, turn/left, turn/right, turn/left, turn/right, arrive/left
        assertEquals(listOf(DEPART, RIGHT, LEFT, RIGHT, LEFT, RIGHT, LEFT, RIGHT, ARRIVE), r.steps.map { it.maneuver })
        assertTrue(RadarProjection.distanceMeters(r.polyline.first(), PastoCentre) < 150)
        assertTrue(RadarProjection.distanceMeters(r.polyline.last(), udenar) < 150)
        assertEquals(TravelMode.Walk, r.mode)
    }

    @Test fun parsesTheRealCarSample() {
        val r = (OsrmRouter.parse(resource("osrm_car.json"), TravelMode.Drive) as Outcome.Ok).value
        assertEquals(3572.5, r.distanceM, 0.05)
        assertEquals(389.1, r.durationS, 0.05)
        assertEquals(listOf(DEPART, LEFT, LEFT, RIGHT, LEFT, RIGHT, LEFT, ARRIVE), r.steps.map { it.maneuver })
    }

    @Test fun mapsOsrmManeuvers() {
        assertEquals(DEPART, OsrmRouter.maneuverFor("depart", "right"))
        assertEquals(ARRIVE, OsrmRouter.maneuverFor("arrive", "left"))
        assertEquals(Maneuver.ROUNDABOUT, OsrmRouter.maneuverFor("roundabout", "right"))
        assertEquals(Maneuver.ROUNDABOUT, OsrmRouter.maneuverFor("rotary", null))
        assertEquals(Maneuver.SHARP_LEFT, OsrmRouter.maneuverFor("turn", "sharp left"))
        assertEquals(Maneuver.SLIGHT_RIGHT, OsrmRouter.maneuverFor("fork", "slight right"))
        assertEquals(Maneuver.U_TURN, OsrmRouter.maneuverFor("continue", "uturn"))
        assertEquals(Maneuver.STRAIGHT, OsrmRouter.maneuverFor("new name", "straight"))
        assertEquals(Maneuver.STRAIGHT, OsrmRouter.maneuverFor("notification", null))
    }

    @Test fun translatesManeuvers() {
        val right = RouteStep(RIGHT, "Calle 18", PastoCentre, 120.0)
        assertEquals("Gira a la derecha por Calle 18", ManeuverText.instruction(right, Language.ES))
        assertEquals("Turn right onto Calle 18", ManeuverText.instruction(right, Language.EN))
        val round = RouteStep(Maneuver.ROUNDABOUT, "", PastoCentre, 80.0, exit = 2)
        assertEquals("En la glorieta, toma la salida 2", ManeuverText.instruction(round, Language.ES))
        assertEquals("At the roundabout, take exit 2", ManeuverText.instruction(round, Language.EN))
        assertEquals("Llegas a tu destino", ManeuverText.instruction(RouteStep(ARRIVE, "Carrera 22", PastoCentre, 0.0), Language.ES))
        Maneuver.entries.forEach { m ->
            Language.entries.forEach { l -> assertTrue(ManeuverText.instruction(RouteStep(m, "", PastoCentre, 0.0), l).isNotBlank()) }
        }
    }

    @Test fun osrmFailuresMapToFailureTypes() {
        assertEquals(Outcome.Err(WgoFailure.NotFound), OsrmRouter.parse("{\"code\":\"NoRoute\"}", TravelMode.Walk))
        assertEquals(Outcome.Err(WgoFailure.NotFound), OsrmRouter.parse("{\"code\":\"NoSegment\",\"message\":\"x\"}", TravelMode.Walk, 400))
        assertEquals(Outcome.Err(WgoFailure.Server(429)), OsrmRouter.parse("{\"code\":\"TooBig\"}", TravelMode.Walk, 429))
        assertEquals(Outcome.Err(WgoFailure.Server(502)), OsrmRouter.parse("<html>bad gateway</html>", TravelMode.Walk, 502))
    }

    @Test fun routerRequestsTheRightProfileAndFallsBackForDriving() = runBlocking {
        FakeHttpServer { Triple(200, resource("osrm_foot.json"), 0L) }.use { osmde ->
            FakeHttpServer { Triple(200, resource("osrm_car.json"), 0L) }.use { demo ->
                val router = OsrmRouter(HttpClient("WGO-test"), osmde.baseUrl + "/routed-foot", osmde.baseUrl + "/routed-car", demo.baseUrl)
                val walk = router.route(PastoCentre, udenar, TravelMode.Walk)
                assertTrue(walk is Outcome.Ok)
                val req = osmde.requests.single()
                assertEquals("/routed-foot/route/v1/driving/-77.281100,1.213600;-77.293390,1.232530", req.path)
                assertEquals("overview=full&geometries=polyline&steps=true", req.query)
                assertEquals("WGO-test", req.headers["user-agent"])

                osmde.respond = { Triple(503, "{\"code\":\"Busy\"}", 0L) }
                val drive = router.route(PastoCentre, udenar, TravelMode.Drive)
                assertEquals(3572.5, (drive as Outcome.Ok).value.distanceM, 0.05)
                assertEquals("/routed-car/route/v1/driving/-77.281100,1.213600;-77.293390,1.232530", osmde.requests.last().path)
                assertEquals(1, demo.requests.size)

                osmde.respond = { Triple(400, "{\"code\":\"NoSegment\"}", 0L) }
                assertEquals(Outcome.Err(WgoFailure.NotFound), router.route(PastoCentre, GeoCoord(0.0, -80.0), TravelMode.Drive))
                assertEquals("no fallback for a real no-route", 1, demo.requests.size)
            }
        }
    }

    // ---- Nominatim ----

    @Test fun parsesTheRealNominatimSamples() {
        val u = NominatimGeocoder.parse(resource("nominatim_udenar.json"))!!
        assertEquals(1, u.size)
        assertEquals("Universidad de Nariño", u[0].name)
        assertEquals(1.2325340, u[0].coord.lat, 1e-7)
        assertEquals("Acceso Universidad, Universitario", u[0].address)
        val t = NominatimGeocoder.parse(resource("nominatim_terminal.json"))!!
        assertEquals(2, t.size)
        assertTrue(t.any { it.name == "Terminal de Transportes de Pasto" })
        assertEquals(emptyList<Any>(), NominatimGeocoder.parse("[]"))
        assertNull(NominatimGeocoder.parse("{\"error\":\"x\"}"))
    }

    @Test fun buildsThePolicyCompliantUrl() {
        val url = NominatimGeocoder.url("https://n.example", "Universidad de Nariño", PastoCentre, Language.EN, 3)
        assertEquals(
            "https://n.example/search?q=Universidad+de+Nari%C3%B1o&format=jsonv2&limit=3&countrycodes=co&accept-language=en" +
                "&viewbox=-77.48110,1.41360,-77.08110,1.01360&bounded=0",
            url
        )
    }

    @Test fun geocoderCachesAndMapsFailures() = runBlocking {
        FakeHttpServer { Triple(200, resource("nominatim_udenar.json"), 0L) }.use { server ->
            val g = NominatimGeocoder(HttpClient("WGO-test"), server.baseUrl, RateLimiter(0))
            val first = g.search("Universidad de Nariño", null, Language.ES)
            assertEquals("Universidad de Nariño", (first as Outcome.Ok).value.single().name)
            g.search("universidad de nariño", null, Language.ES)
            assertEquals("cached: no second request", 1, server.requests.size)

            server.respond = { Triple(200, "[]", 0L) }
            assertEquals(Outcome.Err(WgoFailure.NotFound), g.search("qwxzzq", null, Language.ES))
            assertEquals("nothing anywhere: one local retry, then NotFound", 3, server.requests.size)
            assertTrue(server.requests.last().query!!.endsWith("bounded=1"))
            server.respond = { Triple(429, "", 0L) }
            assertEquals(Outcome.Err(WgoFailure.Server(429)), g.search("otro", null, Language.ES))
            assertEquals(Outcome.Err(WgoFailure.NotFound), g.search("   ", null, Language.ES))
            assertEquals(4, server.requests.size)
        }
        assertTrue(NominatimGeocoder(HttpClient("x"), "http://127.0.0.1:1", RateLimiter(0))
            .search("x", null, Language.ES).let { it == Outcome.Err(WgoFailure.Offline) })
    }

    @Test fun farOnlyResultsTriggerOneLocalRetryWithSpanishCategories() = runBlocking {
        val far = "[{\"name\":\"Bus terminal\",\"display_name\":\"Bus terminal, Calle 5, Cali\",\"lat\":\"3.45\",\"lon\":\"-76.53\"}]"
        FakeHttpServer { req ->
            if (req.query!!.contains("bounded=1")) Triple(200, resource("nominatim_terminal.json"), 0L) else Triple(200, far, 0L)
        }.use { server ->
            val g = NominatimGeocoder(HttpClient("WGO-test"), server.baseUrl, RateLimiter(0))
            val r = (g.search("bus terminal", null, Language.EN) as Outcome.Ok).value
            assertEquals(2, server.requests.size)
            assertTrue(server.requests[1].query!!.startsWith("q=terminal+de+transportes&"))
            assertTrue(r.all { RadarProjection.distanceMeters(it.coord, PastoCentre) < 50_000 })
        }
        // A local first answer needs no retry, and far extras are listed after the local ones.
        FakeHttpServer { Triple(200, resource("nominatim_terminal.json"), 0L) }.use { server ->
            NominatimGeocoder(HttpClient("WGO-test"), server.baseUrl, RateLimiter(0)).search("Terminal", null, Language.ES)
            assertEquals(1, server.requests.size)
        }
    }

    @Test fun glossaryTranslatesCategoriesOnly() {
        assertEquals("terminal de transportes", PlaceGlossary.toSpanish("bus terminal"))
        assertEquals("centro comercial unicentro", PlaceGlossary.toSpanish("shopping mall Unicentro"))
        assertEquals("universidad of narino", PlaceGlossary.toSpanish("University of Nariño"))
        assertNull(PlaceGlossary.toSpanish("Unicentro"))
    }

    @Test fun rateLimiterSpacesRequests() = runBlocking {
        var clock = 0L
        val starts = mutableListOf<Long>()
        val limiter = RateLimiter(1_100L, { clock }, { clock += it })
        repeat(3) { limiter.run { starts += clock } }
        assertEquals(listOf(0L, 1_100L, 2_200L), starts.map { it - starts[0] })
    }

    // ---- helpers ----

    private fun resource(name: String) = FakeHttpServer.resource(name)

    private fun geometry(name: String) =
        org.json.JSONObject(resource(name)).getJSONArray("routes").getJSONObject(0).getString("geometry")

    private fun distanceToPolyline(p: GeoCoord, line: List<GeoCoord>): Double {
        val kx = cos(Math.toRadians(line[0].lat)) * 111_320.0
        fun x(c: GeoCoord) = (c.lon - line[0].lon) * kx
        fun y(c: GeoCoord) = (c.lat - line[0].lat) * 111_320.0
        return line.zipWithNext().minOf { (a, b) -> Polyline.segmentDistance(x(p), y(p), x(a), y(a), x(b), y(b)) }
    }
}

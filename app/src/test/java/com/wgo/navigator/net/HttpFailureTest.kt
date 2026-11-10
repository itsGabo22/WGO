package com.wgo.navigator.net

import com.wgo.navigator.Language
import com.wgo.navigator.PipelineError
import com.wgo.navigator.TravelMode
import com.wgo.navigator.core.Outcome
import com.wgo.navigator.core.WgoFailure
import com.wgo.navigator.geo.NominatimGeocoder
import com.wgo.navigator.geo.RateLimiter
import com.wgo.navigator.metrics.WgoMetrics
import com.wgo.navigator.route.OsrmRouter
import com.wgo.navigator.ui.StringKey
import com.wgo.navigator.ui.map.GeoCoord
import com.wgo.navigator.ui.stringFor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** A slow server (connected, no answer in time) is not the same failure as being offline. */
class HttpFailureTest {
    @Test fun readTimeoutIsTimeoutAndRefusedConnectionIsOffline() = runBlocking {
        FakeHttpServer { Triple(200, "[]", 1_500L) }.use { server ->
            val r = HttpClient("WGO-test").request(WgoMetrics.Net.GEOCODE, server.baseUrl + "/x", 1_000, 300)
            assertEquals(Outcome.Err(WgoFailure.Timeout), r)
        }
        val refused = HttpClient("WGO-test").request(WgoMetrics.Net.GEOCODE, "http://127.0.0.1:1/x", 1_000, 300)
        assertEquals(Outcome.Err(WgoFailure.Offline), refused)
    }

    @Test fun slowGeocoderAndRouterKeepTheTimeout() = runBlocking {
        FakeHttpServer { Triple(200, "[]", 7_000L) }.use { server ->
            val g = NominatimGeocoder(HttpClient("WGO-test"), server.baseUrl, RateLimiter(0))
            assertEquals(Outcome.Err(WgoFailure.Timeout), g.search("Udenar", null, Language.ES))
        }
        FakeHttpServer { Triple(200, "{}", 9_000L) }.use { server ->
            val router = OsrmRouter(HttpClient("WGO-test"), server.baseUrl, server.baseUrl, carFallbackBase = null)
            val r = router.route(GeoCoord(1.21, -77.28), GeoCoord(1.23, -77.29), TravelMode.Walk)
            assertEquals(Outcome.Err(WgoFailure.Timeout), r)
        }
    }

    @Test fun pipelineMapsTimeoutToItsOwnMessage() {
        assertEquals(PipelineError.Timeout, PipelineError.forNetwork(WgoFailure.Timeout, PipelineError.RouteUnavailable))
        assertEquals(PipelineError.Offline, PipelineError.forNetwork(WgoFailure.Offline, PipelineError.RouteUnavailable))
        assertEquals(PipelineError.RouteUnavailable, PipelineError.forNetwork(WgoFailure.NotFound, PipelineError.RouteUnavailable))
        assertEquals(PipelineError.Offline, PipelineError.forNetwork(WgoFailure.Server(503), PipelineError.Offline))
        assertEquals("Tardó demasiado", stringFor(StringKey.ERR_TIMEOUT, Language.ES))
        assertEquals("Taking too long", stringFor(StringKey.ERR_TIMEOUT, Language.EN))
        assertNotEquals(stringFor(StringKey.ERR_OFFLINE, Language.ES), stringFor(StringKey.ERR_TIMEOUT, Language.ES))
    }
}

package com.wgo.navigator.route

import com.wgo.navigator.TravelMode
import com.wgo.navigator.core.Maneuver
import com.wgo.navigator.core.Outcome
import com.wgo.navigator.core.Route
import com.wgo.navigator.core.RouteStep
import com.wgo.navigator.core.Router
import com.wgo.navigator.core.WgoFailure
import com.wgo.navigator.metrics.WgoMetrics
import com.wgo.navigator.net.HttpClient
import com.wgo.navigator.ui.map.GeoCoord
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONException
import org.json.JSONObject
import java.util.Locale

/**
 * Routes from an OSRM-compatible HTTP server: walking on the OSM-DE foot profile, driving on the OSM-DE car profile,
 * with the public OSRM demo (car only) as the driving fallback when OSM-DE fails with a server error or timeout.
 * (OSM-DE picks the profile through the `routed-foot`/`routed-car` path prefix; the URL profile segment stays
 * `driving`.) One request in flight at a time; cancelling the caller disconnects it.
 */
class OsrmRouter(
    private val http: HttpClient,
    private val footBase: String = FOOT_BASE,
    private val carBase: String = CAR_BASE,
    private val carFallbackBase: String? = CAR_FALLBACK_BASE
) : Router {
    private val inFlight = Mutex()

    override suspend fun route(from: GeoCoord, to: GeoCoord, mode: TravelMode): Outcome<Route> = inFlight.withLock {
        val primary = request(if (mode == TravelMode.Walk) footBase else carBase, from, to, mode)
        val retryable = primary is Outcome.Err && (primary.failure is WgoFailure.Server || primary.failure == WgoFailure.Timeout)
        if (mode == TravelMode.Drive && retryable && carFallbackBase != null) {
            WgoMetrics.event("route fallback=osrm_demo")
            request(carFallbackBase, from, to, mode)
        } else primary
    }

    private suspend fun request(base: String, from: GeoCoord, to: GeoCoord, mode: TravelMode): Outcome<Route> {
        val response = http.request(WgoMetrics.Net.ROUTE, url(base, from, to), CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS)
        val ok = when (response) {
            is Outcome.Err -> return response
            is Outcome.Ok -> response.value
        }
        return parse(ok.body, mode, ok.code)
    }

    companion object {
        const val FOOT_BASE = "https://routing.openstreetmap.de/routed-foot"
        const val CAR_BASE = "https://routing.openstreetmap.de/routed-car"
        const val CAR_FALLBACK_BASE = "https://router.project-osrm.org"
        const val CONNECT_TIMEOUT_MS = 4_000
        const val READ_TIMEOUT_MS = 8_000
        const val SIMPLIFY_TOLERANCE_M = 4.0
        const val MAX_POINTS = 300

        fun url(base: String, from: GeoCoord, to: GeoCoord): String = base.trimEnd('/') + String.format(
            Locale.US, "/route/v1/driving/%.6f,%.6f;%.6f,%.6f", from.lon, from.lat, to.lon, to.lat
        ) + "?overview=full&geometries=polyline&steps=true"

        /**
         * Parses an OSRM `route` answer. "NoRoute"/"NoSegment" (no road near a point, or no connection) is
         * [WgoFailure.NotFound]; other errors are [WgoFailure.Server] with the HTTP status.
         */
        fun parse(body: String, mode: TravelMode, httpCode: Int = 200): Outcome<Route> = try {
            val root = JSONObject(body)
            when (val code = root.optString("code")) {
                "Ok" -> {
                    val r = root.getJSONArray("routes").getJSONObject(0)
                    val raw = Polyline.decode(r.getString("geometry"))
                    val polyline = Polyline.simplify(raw, SIMPLIFY_TOLERANCE_M, MAX_POINTS)
                    WgoMetrics.event("route points raw=${raw.size} simplified=${polyline.size}")
                    val steps = r.getJSONArray("legs").let { legs ->
                        (0 until legs.length()).flatMap { l ->
                            val s = legs.getJSONObject(l).getJSONArray("steps")
                            (0 until s.length()).map { step(s.getJSONObject(it)) }
                        }
                    }
                    if (polyline.size < 2) Outcome.Err(WgoFailure.NotFound)
                    else Outcome.Ok(Route(polyline, r.getDouble("distance"), r.getDouble("duration"), steps, mode))
                }
                "NoRoute", "NoSegment" -> Outcome.Err(WgoFailure.NotFound)
                else -> Outcome.Err(WgoFailure.Server(if (httpCode in 200..299) 0 else httpCode)).also {
                    WgoMetrics.event("route error code=$code")
                }
            }
        } catch (e: JSONException) {
            Outcome.Err(WgoFailure.Server(httpCode))
        }

        private fun step(o: JSONObject): RouteStep {
            val m = o.getJSONObject("maneuver")
            val loc = m.getJSONArray("location")
            val type = m.optString("type")
            val modifier = m.optString("modifier").ifEmpty { null }
            return RouteStep(
                maneuver = maneuverFor(type, modifier),
                street = o.optString("name").trim(),
                location = GeoCoord(loc.getDouble(1), loc.getDouble(0)),
                distanceM = o.optDouble("distance", 0.0),
                exit = if (m.has("exit")) m.optInt("exit").takeIf { it > 0 } else null
            )
        }

        /** OSRM maneuver type + modifier to the app's [Maneuver]. */
        fun maneuverFor(type: String, modifier: String?): Maneuver = when (type) {
            "depart" -> Maneuver.DEPART
            "arrive" -> Maneuver.ARRIVE
            "roundabout", "rotary", "roundabout turn", "exit roundabout", "exit rotary" -> Maneuver.ROUNDABOUT
            else -> when (modifier) {
                "uturn" -> Maneuver.U_TURN
                "sharp right" -> Maneuver.SHARP_RIGHT
                "right" -> Maneuver.RIGHT
                "slight right" -> Maneuver.SLIGHT_RIGHT
                "slight left" -> Maneuver.SLIGHT_LEFT
                "left" -> Maneuver.LEFT
                "sharp left" -> Maneuver.SHARP_LEFT
                else -> Maneuver.STRAIGHT
            }
        }
    }
}

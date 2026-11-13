package com.wgo.navigator.geo

import com.wgo.navigator.core.Outcome
import com.wgo.navigator.core.Place
import com.wgo.navigator.core.WgoFailure
import com.wgo.navigator.metrics.WgoMetrics
import com.wgo.navigator.net.HttpClient
import com.wgo.navigator.places.PlaceIndex
import com.wgo.navigator.ui.map.PastoCentre
import org.json.JSONException
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

/**
 * Photon (OpenStreetMap, fuzzy search; P11) after Nominatim (docs/PLACE_SEARCH.md, "Fallback order and budgets").
 * Terms of the public instance (https://photon.komoot.io/, read 2026-11-13): "please be fair - extensive usage will be
 * throttled"; no availability guarantee. So: one request per committed utterance at most (the resolver's shared
 * budget of 3 geocoder requests), at most one per [MIN_INTERVAL_MS] across the process ([limiter]), identifying
 * User-Agent (set on [http]), an in-memory cache of [CACHE_SIZE] queries. The request holds the place words, a FIXED
 * box (the places index box) and the FIXED Pasto centre as bias: never the user's position.
 */
class PhotonGeocoder(
    private val http: HttpClient,
    private val baseUrl: String = BASE_URL,
    private val limiter: RateLimiter = sharedLimiter
) : OnlinePlaceSearch {
    private val cache = object : LinkedHashMap<String, List<Place>>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Place>>?) = size > CACHE_SIZE
    }

    /** One request (none on a cache hit). [center] and [bounded] are ignored: the box and the bias are fixed. */
    override suspend fun once(query: String, center: com.wgo.navigator.ui.map.GeoCoord, bounded: Boolean): Outcome<List<Place>> {
        val q = query.trim()
        if (q.isEmpty()) return Outcome.Err(WgoFailure.NotFound)
        val key = q.lowercase(Locale.ROOT)
        synchronized(cache) { cache[key] }?.let {
            WgoMetrics.event("photon cache_hit")
            return Outcome.Ok(it)
        }
        val response = limiter.run { http.request(WgoMetrics.Net.GEOCODE, url(baseUrl, q), CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS) }
        val ok = when (response) {
            is Outcome.Err -> return response
            is Outcome.Ok -> response.value
        }
        if (ok.code !in 200..299) return Outcome.Err(WgoFailure.Server(ok.code))
        val places = parse(ok.body) ?: return Outcome.Err(WgoFailure.Server(ok.code))
        WgoMetrics.event("photon results=${places.size}")
        if (places.isEmpty()) return Outcome.Err(WgoFailure.NotFound)
        synchronized(cache) { cache[key] = places }
        return Outcome.Ok(places)
    }

    companion object {
        const val BASE_URL = "https://photon.komoot.io"
        const val CACHE_SIZE = 20
        const val MIN_INTERVAL_MS = 1_100L
        const val CONNECT_TIMEOUT_MS = 4_000
        const val READ_TIMEOUT_MS = 6_000
        const val LIMIT = 8

        /** One limiter for the whole process (its own: Nominatim's spacing is separate). */
        val sharedLimiter = RateLimiter(MIN_INTERVAL_MS)

        /** The request: the words, the fixed index box and the fixed Pasto centre (never the user's position). */
        fun url(base: String, query: String): String = base.trimEnd('/') + "/api/?q=" + URLEncoder.encode(query, "UTF-8") +
            String.format(
                Locale.US, "&limit=%d&lat=%.4f&lon=%.4f&bbox=%.3f,%.3f,%.3f,%.3f",
                LIMIT, PastoCentre.lat, PastoCentre.lon,
                PlaceIndex.LON_MIN, PlaceIndex.LAT_MIN, PlaceIndex.LON_MAX, PlaceIndex.LAT_MAX
            )

        /**
         * Parses Photon's GeoJSON: each feature's name, a subtitle (street and number, then district or city) and the
         * OSM tag as the category ("amenity:fast_food"). Features without a name are skipped; null when the body is
         * not a feature collection.
         */
        fun parse(body: String): List<Place>? = try {
            val features = JSONObject(body).getJSONArray("features")
            (0 until features.length()).mapNotNull { i ->
                val f = features.optJSONObject(i) ?: return@mapNotNull null
                val p = f.optJSONObject("properties") ?: return@mapNotNull null
                val c = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: return@mapNotNull null
                val name = p.optString("name").trim()
                if (name.isEmpty() || c.length() < 2) return@mapNotNull null
                val street = listOf(p.optString("street"), p.optString("housenumber")).filter { it.isNotBlank() }.joinToString(" ")
                val area = listOf(p.optString("district"), p.optString("locality"), p.optString("city"))
                    .firstOrNull { it.isNotBlank() }.orEmpty()
                val subtitle = listOf(street, area).filter { it.isNotBlank() }.joinToString(", ")
                val kind = listOf(p.optString("osm_key"), p.optString("osm_value")).filter { it.isNotBlank() }.joinToString(":")
                Place(name, subtitle, com.wgo.navigator.ui.map.GeoCoord(c.getDouble(1), c.getDouble(0)), category = kind)
            }
        } catch (e: JSONException) {
            null
        }
    }
}

package com.wgo.navigator.geo

import com.wgo.navigator.Language
import com.wgo.navigator.core.Geocoder
import com.wgo.navigator.core.Outcome
import com.wgo.navigator.core.Place
import com.wgo.navigator.core.WgoFailure
import com.wgo.navigator.metrics.WgoMetrics
import com.wgo.navigator.net.HttpClient
import com.wgo.navigator.ui.map.GeoCoord
import com.wgo.navigator.ui.map.PastoCentre
import com.wgo.navigator.ui.map.RadarProjection
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONException
import java.net.URLEncoder
import java.util.Locale

/**
 * Minimum spacing between requests, shared by every caller ([Mutex] + last start time). Nominatim's usage policy
 * allows at most one request per second per application.
 */
class RateLimiter(
    private val minIntervalMs: Long,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 },
    private val sleep: suspend (Long) -> Unit = { delay(it) }
) {
    private val mutex = Mutex()
    private var last = Long.MIN_VALUE / 2

    suspend fun <T> run(block: suspend () -> T): T = mutex.withLock {
        val wait = last + minIntervalMs - nowMs()
        if (wait > 0) sleep(wait)
        last = nowMs()
        block()
    }
}

/**
 * Place search on Nominatim (OpenStreetMap). Policy: identifying User-Agent (set on [http]), at most one request per
 * 1.1 s across the whole app ([limiter], process-wide by default), an in-memory LRU cache of [CACHE_SIZE] queries,
 * one request per committed utterance (no search-as-you-type), results biased to a +/-0.2 degree box around [near]
 * (the Pasto centre without a fix) but not bounded to it, Colombia only. Hits within [LOCAL_KM] come first; when there
 * are none, ONE more request searches inside the box (English category words translated by [PlaceGlossary]), so a
 * query costs at most two requests. OTHER cycles the cached results with no request.
 */
class NominatimGeocoder(
    private val http: HttpClient,
    private val baseUrl: String = BASE_URL,
    private val limiter: RateLimiter = sharedLimiter
) : Geocoder {
    private val cache = object : LinkedHashMap<String, List<Place>>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Place>>?) = size > CACHE_SIZE
    }

    override suspend fun search(query: String, near: GeoCoord?, language: Language, limit: Int): Outcome<List<Place>> {
        val q = query.trim()
        if (q.isEmpty()) return Outcome.Err(WgoFailure.NotFound)
        val center = near ?: PastoCentre
        val key = cacheKey(q, center, language, limit)
        synchronized(cache) { cache[key] }?.let {
            WgoMetrics.event("geocode cache_hit")
            return Outcome.Ok(it)
        }
        // First request: biased to the viewbox but not bounded (the place may be outside it).
        val first = fetch(q, center, language, limit, bounded = false)
        if (first is Outcome.Err && first.failure != WgoFailure.NotFound) return first
        val firstPlaces = (first as? Outcome.Ok)?.value.orEmpty()
        val near = firstPlaces.filter { isLocal(it, center) }
        val places = if (near.isNotEmpty()) {
            near + (firstPlaces - near.toSet()) // local hits first, far ones after
        } else {
            // No hit within LOCAL_KM: one retry inside the viewbox, with Spanish category words for English searches.
            val localQuery = (if (language == Language.EN) PlaceGlossary.toSpanish(q) else null) ?: q
            WgoMetrics.event("geocode local_retry glossary=${localQuery != q}")
            val second = fetch(localQuery, center, language, limit, bounded = true)
            (second as? Outcome.Ok)?.value?.takeIf { it.isNotEmpty() } ?: firstPlaces
        }
        if (places.isEmpty()) return Outcome.Err(WgoFailure.NotFound)
        synchronized(cache) { cache[key] = places }
        return Outcome.Ok(places)
    }

    private suspend fun fetch(q: String, center: GeoCoord, language: Language, limit: Int, bounded: Boolean): Outcome<List<Place>> {
        val response = limiter.run {
            http.request(
                WgoMetrics.Net.GEOCODE, url(baseUrl, q, center, language, limit, bounded), CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS
            )
        }
        val ok = when (response) {
            is Outcome.Err -> return response
            is Outcome.Ok -> response.value
        }
        if (ok.code !in 200..299) return Outcome.Err(WgoFailure.Server(ok.code))
        val places = parse(ok.body) ?: return Outcome.Err(WgoFailure.Server(ok.code))
        return if (places.isEmpty()) Outcome.Err(WgoFailure.NotFound) else Outcome.Ok(places)
    }

    private fun isLocal(place: Place, center: GeoCoord) =
        RadarProjection.distanceMeters(place.coord, center) <= LOCAL_KM * 1000

    companion object {
        const val BASE_URL = "https://nominatim.openstreetmap.org"
        const val CACHE_SIZE = 20
        const val MIN_INTERVAL_MS = 1_100L
        const val CONNECT_TIMEOUT_MS = 4_000
        const val READ_TIMEOUT_MS = 6_000
        private const val BOX_DEG = 0.2

        /** Hits farther than this from the bias centre are "far": listed last, and alone they trigger the local retry. */
        const val LOCAL_KM = 50.0

        /** One limiter for the whole process: every geocoder instance shares the 1.1 s spacing. */
        val sharedLimiter = RateLimiter(MIN_INTERVAL_MS)

        fun url(base: String, query: String, center: GeoCoord, language: Language, limit: Int, bounded: Boolean = false): String {
            val viewbox = String.format(
                Locale.US, "%.5f,%.5f,%.5f,%.5f",
                center.lon - BOX_DEG, center.lat + BOX_DEG, center.lon + BOX_DEG, center.lat - BOX_DEG
            )
            return base.trimEnd('/') + "/search?q=" + URLEncoder.encode(query, "UTF-8") +
                "&format=jsonv2&limit=$limit&countrycodes=co&accept-language=${language.name.lowercase()}" +
                "&viewbox=$viewbox&bounded=${if (bounded) 1 else 0}"
        }

        private fun cacheKey(q: String, c: GeoCoord, language: Language, limit: Int) =
            String.format(Locale.US, "%s|%.1f|%.1f|%s|%d", q.lowercase(), c.lat, c.lon, language, limit)

        /** Parses a `format=jsonv2` answer; null when it is not the expected JSON array. */
        fun parse(body: String): List<Place>? = try {
            val array = JSONArray(body)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val lat = o.optString("lat").toDoubleOrNull() ?: return@mapNotNull null
                val lon = o.optString("lon").toDoubleOrNull() ?: return@mapNotNull null
                val display = o.optString("display_name")
                val parts = display.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                val name = o.optString("name").trim().ifEmpty { parts.firstOrNull().orEmpty() }
                if (name.isEmpty()) return@mapNotNull null
                // Subtitle: the next two parts of the display name after the name itself (street, neighbourhood).
                val subtitle = parts.filter { it != name }.take(2).joinToString(", ")
                Place(name, subtitle, GeoCoord(lat, lon))
            }
        } catch (e: JSONException) {
            null
        }
    }
}

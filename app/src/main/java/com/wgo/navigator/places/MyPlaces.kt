package com.wgo.navigator.places

import android.content.Context
import com.wgo.navigator.core.Place
import com.wgo.navigator.core.PlaceOrigin
import com.wgo.navigator.geo.PrivatePlaces
import com.wgo.navigator.metrics.WgoMetrics
import com.wgo.navigator.ui.map.GeoCoord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException

/** One entry of the private list. */
data class MyPlace(val aliases: List<String>, val name: String, val coord: GeoCoord, val note: String)

/**
 * The owner's private places (docs/PLACES.md): `assets/my_places.json`, git-ignored, read lazily on the first search.
 * An alias match (accent and case insensitive, stop words ignored) beats every other source and needs no network and
 * no language model. A missing or broken file is an empty list: the app and the build work without it. Never sent
 * anywhere.
 */
class MyPlaces(private val read: () -> String?) : PrivatePlaces {
    @Volatile
    private var byAlias: Map<String, MyPlace>? = null
    private val mutex = Mutex()

    override suspend fun match(name: String): Place? {
        val key = PlaceText.normalize(name)
        if (key.isEmpty()) return null
        val hit = load()[key] ?: return null
        return Place(hit.name, hit.note, hit.coord, PlaceOrigin.Private)
    }

    override suspend fun prewarm() {
        load()
    }

    private suspend fun load(): Map<String, MyPlace> {
        byAlias?.let { return it }
        return mutex.withLock {
            byAlias ?: withContext(Dispatchers.IO) {
                val entries = read()?.let(::parse).orEmpty()
                WgoMetrics.event("my_places entries=${entries.size}")
                index(entries).also { byAlias = it }
            }
        }
    }

    companion object {
        const val ASSET = "my_places.json"

        fun fromAssets(context: Context): MyPlaces {
            val app = context.applicationContext
            return MyPlaces { runCatching { app.assets.open(ASSET).bufferedReader().use { it.readText() } }.getOrNull() }
        }

        /** Every alias and the name itself, normalised, to its place (the first entry wins a duplicate alias). */
        fun index(entries: List<MyPlace>): Map<String, MyPlace> {
            val out = LinkedHashMap<String, MyPlace>()
            for (e in entries) for (a in e.aliases + e.name) {
                val k = PlaceText.normalize(a)
                if (k.isNotEmpty() && k !in out) out[k] = e
            }
            return out
        }

        /** Parses the JSON array; entries without a name or valid coordinates are skipped. */
        fun parse(json: String): List<MyPlace> = try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val name = o.optString("name").trim().ifEmpty { return@mapNotNull null }
                val lat = o.optDouble("lat", Double.NaN)
                val lon = o.optDouble("lon", Double.NaN)
                if (lat.isNaN() || lon.isNaN() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return@mapNotNull null
                val aliases = o.optJSONArray("aliases")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
                MyPlace(aliases.filter { it.isNotBlank() }, name, GeoCoord(lat, lon), o.optString("note").trim())
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }
}

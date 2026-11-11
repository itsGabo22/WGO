package com.wgo.navigator.places

import android.content.Context
import com.wgo.navigator.metrics.WgoMetrics
import com.wgo.navigator.ui.map.GeoCoord
import com.wgo.navigator.ui.map.RadarProjection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

/** One place of the offline index (Overture Maps Places, docs/THIRD_PARTY_DATA.md). */
class IndexedPlace(
    val name: String,
    /** Normalised name tokens without category words (the "core" compared with a query). */
    val core: List<String>,
    /** Normalised alternate names, each as core tokens. */
    val alts: List<List<String>>,
    val category: String,
    val address: String,
    val coord: GeoCoord,
    val confidence: Float,
    val source: String
)

/** A match of the index: [tier] 3 exact core name, 2 every query token matched, 1 some token matched. */
class IndexHit(val place: IndexedPlace, val tier: Int, val categoryBoost: Boolean, val distanceM: Double)

/**
 * The offline places index (docs/PLACE_SEARCH.md): loaded lazily off the main thread on the first place search (never at
 * app start), kept for the process. Matching is accent and case insensitive, ignores stop words and category words,
 * and accepts token prefixes and one edit in tokens of 5+ characters. [open] provides the gzip TSV written by
 * `tools/build_places_index.py`.
 */
class PlaceIndex(private val open: () -> InputStream) {
    @Volatile
    private var entries: List<IndexedPlace>? = null
    private val mutex = Mutex()

    val isLoaded: Boolean get() = entries != null
    val size: Int get() = entries?.size ?: 0

    /** Loads the asset once (IO dispatcher). False when it is missing or unreadable (the search then goes online). */
    suspend fun ensureLoaded(): Boolean {
        entries?.let { return true }
        return mutex.withLock {
            entries?.let { return@withLock true }
            withContext(Dispatchers.IO) {
                val start = System.nanoTime()
                val runtime = Runtime.getRuntime()
                val before = runtime.totalMemory() - runtime.freeMemory()
                val loaded = runCatching { open().use(::parse) }.getOrNull()
                entries = loaded
                val after = runtime.totalMemory() - runtime.freeMemory()
                WgoMetrics.event(
                    "places_index loaded=${loaded != null} rows=${loaded?.size ?: 0} " +
                        "ms=${(System.nanoTime() - start) / 1_000_000} heap_delta_kb=${(after - before) / 1024}"
                )
                loaded != null
            }
        }
    }

    /**
     * Index hits for [query] (raw text; normalised here) within [maxKm] of [anchor], best first: tier, category boost,
     * distance, confidence. Only tier >= [minTier]; at most [limit]. Empty when the index is not loaded.
     */
    fun search(query: String, anchor: GeoCoord, minTier: Int = 1, maxKm: Double = 25.0, limit: Int = 20): List<IndexHit> {
        val all = entries ?: return emptyList()
        val (q, fragments) = PlaceCategories.split(PlaceText.tokens(query))
        if (q.isEmpty()) return emptyList()
        val hits = ArrayList<IndexHit>()
        for (p in all) {
            var tier = tierOf(q, p.core)
            for (alt in p.alts) if (tier < 3) tier = maxOf(tier, tierOf(q, alt))
            if (tier < minTier) continue
            val d = RadarProjection.distanceMeters(anchor, p.coord)
            if (d > maxKm * 1000) continue
            hits += IndexHit(p, tier, PlaceCategories.matches(p.category, fragments), d)
        }
        return hits.sortedWith(
            compareByDescending<IndexHit> { it.tier }
                .thenByDescending { it.categoryBoost }
                .thenBy { it.distanceM }
                .thenByDescending { it.place.confidence }
        ).take(limit)
    }

    companion object {
        const val ASSET = "places_pasto.tsv.gz"

        /** The box the index covers (the builder's default); outside it the index is skipped. */
        const val LON_MIN = -77.40
        const val LON_MAX = -77.17
        const val LAT_MIN = 1.08
        const val LAT_MAX = 1.32

        fun covers(c: GeoCoord) = c.lon in LON_MIN..LON_MAX && c.lat in LAT_MIN..LAT_MAX

        fun fromAssets(context: Context): PlaceIndex {
            val app = context.applicationContext
            return PlaceIndex { app.assets.open(ASSET) }
        }

        /** 3 = same core tokens, 2 = every query token matches a name token, 1 = some long query token matches. */
        fun tierOf(q: List<String>, name: List<String>): Int {
            if (name.isEmpty()) return 0
            if (q == name) return 3
            var all = true
            var any = false
            for (t in q) {
                val m = name.any { PlaceText.tokenMatches(t, it) }
                if (m) { if (t.length >= 4) any = true } else all = false
            }
            return when {
                all -> 2
                any -> 1
                else -> 0
            }
        }

        /** Parses the gzip TSV: `#` comment lines, one header line, then id, name, name_norm, alt_norm, category, ... */
        fun parse(input: InputStream): List<IndexedPlace> {
            val reader = BufferedReader(InputStreamReader(GZIPInputStream(input), Charsets.UTF_8))
            val out = ArrayList<IndexedPlace>(6_500)
            var headerSeen = false
            reader.forEachLine { line ->
                if (line.isEmpty() || line.startsWith("#")) return@forEachLine
                if (!headerSeen) { headerSeen = true; return@forEachLine }
                val f = line.split('\t')
                if (f.size < 10) return@forEachLine
                val lat = f[6].toDoubleOrNull() ?: return@forEachLine
                val lon = f[7].toDoubleOrNull() ?: return@forEachLine
                val core = PlaceCategories.core(f[2].split(' ').filter { it.isNotEmpty() })
                val alts = if (f[3].isEmpty()) emptyList() else f[3].split('|').map { a ->
                    PlaceCategories.core(a.split(' ').filter { it.isNotEmpty() })
                }
                out += IndexedPlace(
                    name = f[1], core = core, alts = alts, category = f[4], address = f[5],
                    coord = GeoCoord(lat, lon), confidence = f[8].toFloatOrNull() ?: 0f, source = f[9]
                )
            }
            return out
        }
    }
}

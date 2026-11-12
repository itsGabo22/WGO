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
    /** Category words taken out of the name ("drogueria"): an exact match needs the user to have said them too. */
    val categoryWords: List<String>,
    val category: String,
    val address: String,
    val coord: GeoCoord,
    val confidence: Float,
    val source: String
)

/** A match of the index: [tier] 3 exact core name, 2 every query token matched, 1 some token matched. */
class IndexHit(val place: IndexedPlace, val tier: Int, val categoryBoost: Boolean, val distanceM: Double)

/**
 * The offline places index (docs/PLACE_SEARCH.md): loaded lazily off the main thread when the user first presses the
 * microphone (or on the first place search), never at app start or on Home idle, and kept for the process. Measured on
 * the Galaxy Watch5 Pro: about 0.5 s and 4 MB in the release-like build; the debug build is 10-50x slower (debuggable
 * code plus the debug UI's animation work), which the mic-press start hides behind listening. Matching is accent and case insensitive, ignores stop words and category words,
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
        val tokens = PlaceText.tokens(query)
        val (q, fragments) = PlaceCategories.split(tokens)
        if (q.isEmpty()) return emptyList()
        val said = tokens.filter(PlaceCategories::isCategoryWord).toSet()
        val hits = ArrayList<IndexHit>()
        for (p in all) {
            var tier = tierOf(q, p.core)
            for (alt in p.alts) if (tier < 3) tier = maxOf(tier, tierOf(q, alt))
            // "Droguería Plaza de Nariño" is not exactly "Plaza de Nariño" unless the user said "droguería" (P7, watch).
            if (tier == 3 && !said.containsAll(p.categoryWords)) tier = 2
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
        /**
         * Not `.gz`: the Android packager gunzips `.gz` assets and drops the extension (measured on the P7 APK), so
         * the app would not find the file. `.gzip` is stored as is (it is already compressed).
         */
        const val ASSET = "places_pasto.tsv.gzip"

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

        /**
         * Parses the gzip TSV: `#` comment lines, one header line, then id, name, name_norm, alt_norm, category, ...
         * Hand-rolled on purpose: Kotlin's `toDoubleOrNull` validates with a large regex and `split` allocates a list
         * per line, which made the first load take 20 s in the debug build on the watch.
         */
        fun parse(input: InputStream): List<IndexedPlace> {
            val reader = BufferedReader(InputStreamReader(GZIPInputStream(input, 32 * 1024), Charsets.UTF_8), 64 * 1024)
            val out = ArrayList<IndexedPlace>(6_500)
            val f = arrayOfNulls<String>(10)
            var headerSeen = false
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty() || line[0] == '#') continue
                if (!headerSeen) { headerSeen = true; continue }
                var start = 0
                var n = 0
                while (n < 10) {
                    val tab = line.indexOf('\t', start)
                    f[n++] = if (tab < 0) line.substring(start) else line.substring(start, tab)
                    if (tab < 0) break
                    start = tab + 1
                }
                if (n < 10) continue
                val lat = number(f[6]!!) ?: continue
                val lon = number(f[7]!!) ?: continue
                val alt = f[3]!!
                val nameWords = words(f[2]!!, ' ')
                val core = PlaceCategories.core(nameWords)
                out += IndexedPlace(
                    name = f[1]!!, core = core,
                    alts = if (alt.isEmpty()) emptyList() else words(alt, '|').map { PlaceCategories.core(words(it, ' ')) },
                    categoryWords = if (core.size == nameWords.size) emptyList() else nameWords.filter(PlaceCategories::isCategoryWord),
                    category = f[4]!!, address = f[5]!!, coord = GeoCoord(lat, lon),
                    confidence = number(f[8]!!)?.toFloat() ?: 0f, source = f[9]!!
                )
            }
            return out
        }

        private fun number(s: String): Double? = try {
            java.lang.Double.parseDouble(s)
        } catch (e: NumberFormatException) {
            null
        }

        private fun words(s: String, sep: Char): List<String> {
            val out = ArrayList<String>(4)
            var start = 0
            while (start <= s.length) {
                val end = s.indexOf(sep, start).let { if (it < 0) s.length else it }
                if (end > start) out += s.substring(start, end)
                start = end + 1
            }
            return out
        }
    }
}

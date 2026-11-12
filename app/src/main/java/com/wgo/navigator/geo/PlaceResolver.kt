package com.wgo.navigator.geo

import com.wgo.navigator.Language
import com.wgo.navigator.core.Outcome
import com.wgo.navigator.core.Place
import com.wgo.navigator.core.PlaceOrigin
import com.wgo.navigator.core.WgoFailure
import com.wgo.navigator.metrics.WgoMetrics
import com.wgo.navigator.places.IndexedPlace
import com.wgo.navigator.places.PlaceCategories
import com.wgo.navigator.places.PlaceIndex
import com.wgo.navigator.places.PlaceText
import com.wgo.navigator.ui.map.GeoCoord
import com.wgo.navigator.ui.map.PastoCentre
import com.wgo.navigator.ui.map.RadarProjection

/** The place words of one utterance: the name, an optional landmark hint and an optional category word. */
data class PlaceRequest(
    val name: String,
    val hint: String? = null,
    val category: String? = null,
    /** The name came from a Pasto nickname: a search string verified on Nominatim, asked there first. */
    val fromAlias: Boolean = false
)

/** Where a candidate came from. */
enum class PlaceSource { MyPlaces, Index, Nominatim }

/** A scored candidate (docs/PLACE_SEARCH.md, "Ranking"). */
data class PlaceCandidate(
    val place: Place,
    val tier: Int,
    val categoryBoost: Boolean,
    val distanceM: Double,
    val confidence: Float,
    val source: PlaceSource,
    val hintScore: Double = 0.0
) {
    /** A destination category (docs/PLACE_SEARCH.md, "Landmark prior"). */
    val landmark: Boolean get() = PlaceCategories.isLandmark(place.category)

    /** Confidence high enough to beat weaker rows of the same rank (Nominatim counts as 0.6). */
    val trusted: Boolean get() = confidence >= TRUSTED_CONFIDENCE

    companion object {
        const val TRUSTED_CONFIDENCE = 0.6f
    }
}

sealed interface Resolution {
    /** One or two places, best first; a place over [PlaceResolver.FAR_KM] is never first when a nearer one exists. */
    data class Found(val candidates: List<PlaceCandidate>) : Resolution {
        val places: List<Place> get() = candidates.map { it.place }
    }

    /** No candidate: up to two partial matches within [PlaceResolver.SUGGEST_KM] (name only). */
    data class NotFound(val suggestions: List<Place>) : Resolution

    /** The online step failed and nothing local was found. */
    data class Failed(val failure: WgoFailure) : Resolution
}

/** One Nominatim request (the real one is [NominatimGeocoder.once]); a seam for tests. */
fun interface OnlinePlaceSearch {
    suspend fun once(query: String, center: GeoCoord, bounded: Boolean): Outcome<List<Place>>
}

/** A private list of the owner's places (my_places.json); an alias match beats everything. */
fun interface PrivatePlaces {
    suspend fun match(name: String): Place?

    /** Reads the list ahead of the first search. */
    suspend fun prewarm() {}
}

/**
 * Turns place words into at most two candidates, following docs/PLACE_SEARCH.md: my places, then the local index
 * (offline), then Nominatim only when nothing local is strong (at most [MAX_REQUESTS] requests); ranked by distance
 * class (<= [FAR_KM] first), match tier, category boost, hint score, distance and confidence; deduplicated (same name
 * within [DEDUPE_M]).
 */
class PlaceResolver(
    private val index: PlaceIndex?,
    private val online: OnlinePlaceSearch?,
    private val privatePlaces: PrivatePlaces? = null
) {
    /** Requests used by the last [resolve] (debug metric and tests). */
    @Volatile
    var lastRequests: Int = 0
        private set

    /** Loads the local index ahead of the search (called at the mic press, so it overlaps with listening). */
    suspend fun prewarm() {
        privatePlaces?.prewarm()
        index?.ensureLoaded()
    }

    suspend fun resolve(request: PlaceRequest, fix: GeoCoord?, @Suppress("UNUSED_PARAMETER") language: Language): Resolution {
        val anchor = fix ?: PastoCentre
        var requests = 0
        lastRequests = 0
        val name = request.name.trim()
        if (name.isEmpty()) return Resolution.NotFound(emptyList())

        privatePlaces?.match(name)?.let { place ->
            WgoMetrics.event("places source=my_places")
            return Resolution.Found(listOf(candidate(place, 4, false, anchor, 1f, PlaceSource.MyPlaces)))
        }

        val candidates = ArrayList<PlaceCandidate>()
        var partial = emptyList<PlaceCandidate>()
        val query = listOfNotNull(request.category, name).joinToString(" ")

        var onlineFailure: WgoFailure? = null
        suspend fun ask(q: String, bounded: Boolean): List<Place> {
            val search = online ?: return emptyList()
            if (requests >= MAX_REQUESTS) return emptyList()
            requests++
            return when (val r = search.once(q, anchor, bounded)) {
                is Outcome.Ok -> r.value
                is Outcome.Err -> {
                    if (r.failure != WgoFailure.NotFound) onlineFailure = r.failure
                    emptyList()
                }
            }
        }

        // A nickname ("la plaza", "el terminal") is a landmark name verified on Nominatim: ask there first (P7, the
        // local index matched "Drogueria Plaza De Nariño" for "Plaza de Nariño"); offline, the index still answers.
        if (request.fromAlias) {
            ask(name, bounded = false).filter { distance(it, anchor) <= FAR_KM * 1000 }.forEach { p ->
                candidates += candidate(p, 3, false, anchor, NOMINATIM_CONFIDENCE, PlaceSource.Nominatim)
            }
        }
        if (candidates.isEmpty() && index != null && PlaceIndex.covers(anchor) && index.ensureLoaded()) {
            val hits = index.search(query, anchor, minTier = 1, maxKm = FAR_KM)
            hits.filter { it.tier >= 2 }.forEach {
                candidates += PlaceCandidate(it.place.toPlace(), it.tier, it.categoryBoost, it.distanceM, it.place.confidence, PlaceSource.Index)
            }
            partial = hits.filter { it.tier == 1 && it.distanceM <= SUGGEST_KM * 1000 }.map {
                PlaceCandidate(it.place.toPlace(), 1, false, it.distanceM, it.place.confidence, PlaceSource.Index)
            }
        }

        if (candidates.isEmpty()) {
            val hint = request.hint
            val found = if (hint != null) {
                ask("$name, $hint", bounded = false).ifEmpty { ask(name, bounded = true) }
            } else {
                val first = ask(name, bounded = false)
                if (first.any { distance(it, anchor) <= FAR_KM * 1000 }) first
                else ask(name, bounded = true).ifEmpty { first }
            }
            val nameTokens = PlaceText.tokens(name)
            found.forEach { p ->
                val tier = if (PlaceText.tokens(p.name) == nameTokens) 3 else 2 // Nominatim decided it matches
                candidates += candidate(p, tier, false, anchor, NOMINATIM_CONFIDENCE, PlaceSource.Nominatim)
            }
        }

        // The hint only re-ranks: text match first, then (when that cannot separate the top two) proximity to the
        // hint's own point, at the cost of one more request.
        var ranked: List<PlaceCandidate> = candidates
        request.hint?.let { hint ->
            ranked = ranked.map { it.copy(hintScore = HintMatch.textScore(hint, it.place.name + " " + it.place.address)) }
            val top = sortedCandidates(ranked)
            if (top.size >= 2 && top[0].tier == top[1].tier && top.take(2).all { it.hintScore == 0.0 }) {
                ask(hint, bounded = true).firstOrNull()?.let { point ->
                    ranked = ranked.map {
                        if (RadarProjection.distanceMeters(it.place.coord, point.coord) <= HINT_NEAR_M) {
                            it.copy(hintScore = it.hintScore + 0.5)
                        } else it
                    }
                }
            }
        }

        lastRequests = requests
        val best = dedupe(sortedCandidates(ranked)).take(MAX_CANDIDATES)
        WgoMetrics.event(
            "places resolved n=${best.size} sources=${best.joinToString("+") { it.source.name }} requests=$requests " +
                "hint=${request.hint != null}"
        )
        if (best.isNotEmpty()) return Resolution.Found(best)
        val suggestions = dedupe(partial.sortedBy { it.distanceM }).take(MAX_CANDIDATES).map { it.place }
        onlineFailure?.let { if (suggestions.isEmpty()) return Resolution.Failed(it) }
        return Resolution.NotFound(suggestions)
    }

    private fun candidate(p: Place, tier: Int, boost: Boolean, anchor: GeoCoord, conf: Float, source: PlaceSource) =
        PlaceCandidate(p, tier, boost, distance(p, anchor), conf, source)

    companion object {
        const val MAX_REQUESTS = 3
        const val MAX_CANDIDATES = 2
        const val FAR_KM = 25.0
        const val SUGGEST_KM = 5.0
        const val DEDUPE_M = 150.0
        const val HINT_NEAR_M = 600.0
        const val NOMINATIM_CONFIDENCE = 0.6f

        private fun distance(p: Place, anchor: GeoCoord) = RadarProjection.distanceMeters(p.coord, anchor)

        /** The ranking order of docs/PLACE_SEARCH.md ("Ranking"; landmark and trusted since P8). */
        fun sortedCandidates(list: List<PlaceCandidate>): List<PlaceCandidate> = list.sortedWith(
            compareBy<PlaceCandidate> { it.distanceM > FAR_KM * 1000 }
                .thenByDescending { it.tier }
                .thenByDescending { it.landmark }
                .thenByDescending { it.categoryBoost }
                .thenByDescending { it.hintScore }
                .thenByDescending { it.trusted }
                .thenBy { it.distanceM }
                .thenByDescending { it.confidence }
        )

        private fun IndexedPlace.toPlace() = Place(name, address, coord, PlaceOrigin.Overture, category)

        /** Keeps the first of the candidates with the same normalised name within [DEDUPE_M]. */
        fun dedupe(sorted: List<PlaceCandidate>): List<PlaceCandidate> {
            val kept = ArrayList<PlaceCandidate>()
            for (c in sorted) {
                val n = PlaceText.normalize(c.place.name)
                if (kept.none { PlaceText.normalize(it.place.name) == n &&
                        RadarProjection.distanceMeters(it.place.coord, c.place.coord) <= DEDUPE_M }) kept += c
            }
            return kept
        }
    }
}

/** Textual hint matching with street abbreviations and the Panamericana synonyms (docs/PLACE_SEARCH.md). */
object HintMatch {
    private val SYNONYMS: Map<String, List<List<String>>> = mapOf(
        "panamericana" to listOf(listOf("panamericana"), listOf("pan", "american"), listOf("panamerican"), listOf("panamericano")),
        "calle" to listOf(listOf("calle"), listOf("cl"), listOf("cll")),
        "carrera" to listOf(listOf("carrera"), listOf("cra"), listOf("kr"), listOf("cr"), listOf("kra")),
        "avenida" to listOf(listOf("avenida"), listOf("av"), listOf("avda")),
        "transversal" to listOf(listOf("transversal"), listOf("tv"), listOf("tr"))
    )

    /** Fraction (0..1) of the hint's tokens found in [text] (address + name). */
    fun textScore(hint: String, text: String): Double {
        val h = PlaceText.tokens(hint)
        if (h.isEmpty()) return 0.0
        val t = PlaceText.tokens(text)
        val found = h.count { token ->
            val forms = SYNONYMS[token] ?: listOf(listOf(token))
            forms.any { form -> containsSequence(t, form) } ||
                (form1(token) && t.any { PlaceText.tokenMatches(token, it) })
        }
        return found.toDouble() / h.size
    }

    /** Long hint words may also match by prefix / one edit ("unicentro" vs "unicentr"); short ones must be equal. */
    private fun form1(token: String) = token.length >= 5 && token !in SYNONYMS

    private fun containsSequence(t: List<String>, seq: List<String>): Boolean {
        for (i in 0..t.size - seq.size) if (seq.indices.all { t[i + it] == seq[it] }) return true
        return false
    }
}

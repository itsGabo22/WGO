package com.wgo.navigator.geo

import com.wgo.navigator.Language
import com.wgo.navigator.ai.ChoiceOption
import com.wgo.navigator.ai.LlmPlaceChooser
import com.wgo.navigator.ai.PlaceAliases
import com.wgo.navigator.ai.PlaceChooser
import com.wgo.navigator.core.Outcome
import com.wgo.navigator.core.Place
import com.wgo.navigator.core.PlaceOrigin
import com.wgo.navigator.core.WgoFailure
import com.wgo.navigator.metrics.WgoMetrics
import com.wgo.navigator.places.IndexHit
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
    val fromAlias: Boolean = false,
    /** The whole transcript (what the language model may read for a place choice); null: the name alone. */
    val utterance: String? = null,
    /** Language-model calls this utterance already used (the interpreter's); the place search keeps the total <= 2. */
    val aiCallsUsed: Int = 0,
    /** P10: a description or a kind of place, not a name: never one silent guess (pick list). */
    val vague: Boolean = false,
    /** P10: [name] is a street address ("Calle 6A #36-41"), [hint] its neighbourhood: Nominatim only, pick list. */
    val address: Boolean = false,
    /** P11: the user asked for the nearest one ("el más cercano"); the words are not in [name]. */
    val nearest: Boolean = false
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
    val hintScore: Double = 0.0,
    /** P11: query tokens matched only by prefix, sound or spelling (index rows; fewer ranks first inside a tier). */
    val inexact: Int = 0,
    /** P11: the query matched the row's brand ("Mister Pollo MRP"). */
    val brand: Boolean = false
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
    /**
     * Up to three places, best first; a place over [PlaceResolver.FAR_KM] is never first when a nearer one exists.
     * [uncertain] (P10 confidence gate): no clear winner, so the user picks from the list ("¿CUÁL?").
     */
    data class Found(val candidates: List<PlaceCandidate>, val uncertain: Boolean = false) : Resolution {
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
    private val privatePlaces: PrivatePlaces? = null,
    /** The language model's roles (P8); null without an API key: the local ranking decides alone. */
    private val chooser: PlaceChooser? = null
) {
    /** Requests used by the last [resolve] (debug metric and tests). */
    @Volatile
    var lastRequests: Int = 0
        private set

    /** P10: what the model's choice did in the last [resolve] (the confidence gate reads it). */
    enum class ChoiceOutcome { NotAsked, Picked, NoPick }

    @Volatile
    var lastChoice: ChoiceOutcome = ChoiceOutcome.NotAsked
        private set

    /** Language-model calls made by the last [resolve] (tests and the place log). */
    @Volatile
    var lastAiCalls: Int = 0
        private set

    /** Loads the local index ahead of the search (called at the mic press, so it overlaps with listening). */
    suspend fun prewarm() {
        privatePlaces?.prewarm()
        index?.ensureLoaded()
    }

    /**
     * The state of one [resolve] call: the Nominatim and model budgets, the online failure and the candidates. (A class
     * rather than locals captured by a local suspend function: ART's verifier rejected that state machine once the
     * method grew, while the JVM unit tests accepted it.)
     */
    private inner class Search(val anchor: GeoCoord, aiCallsUsed: Int) {
        var requests = 0
        var aiCalls = 0
        private var aiLeft = (MAX_AI_CALLS - aiCallsUsed).coerceAtLeast(0)
        var onlineFailure: WgoFailure? = null
        val candidates = ArrayList<PlaceCandidate>()
        var partial: List<PlaceCandidate> = emptyList()

        /** One Nominatim request within [MAX_REQUESTS]; no hit, offline or over the limit: empty. */
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

        /** Takes one model call from this utterance's budget. */
        fun takeAiCall(): Boolean {
            if (aiLeft <= 0) return false
            aiLeft--
            aiCalls++
            return true
        }

        fun addIndex(hits: List<IndexHit>) {
            hits.forEach {
                candidates += PlaceCandidate(
                    it.place.toPlace(), it.tier, it.categoryBoost, it.distanceM, it.place.confidence, PlaceSource.Index,
                    inexact = it.inexact, brand = it.brand
                )
            }
        }

        /** Nominatim hits within [FAR_KM] (when [nearOnly]); tier 3 when the name equals [asked], else 2. */
        fun addOnline(found: List<Place>, asked: String, nearOnly: Boolean, exactTier: Int? = null) {
            val tokens = PlaceText.tokens(asked)
            found.filter { !nearOnly || distance(it, anchor) <= FAR_KM * 1000 }.forEach { p ->
                val tier = exactTier ?: if (PlaceText.tokens(p.name) == tokens) 3 else 2
                candidates += candidate(p, tier, false, anchor, NOMINATIM_CONFIDENCE, PlaceSource.Nominatim)
            }
        }
    }

    private suspend fun indexReady(anchor: GeoCoord): Boolean = index != null && PlaceIndex.covers(anchor) && index.ensureLoaded()

    suspend fun resolve(request: PlaceRequest, fix: GeoCoord?, language: Language): Resolution {
        lastRequests = 0
        lastAiCalls = 0
        lastChoice = ChoiceOutcome.NotAsked
        val anchor = fix ?: PastoCentre
        val name = request.name.trim()
        if (name.isEmpty()) return Resolution.NotFound(emptyList())

        val mine = privatePlaces?.match(name)
        if (mine != null) {
            WgoMetrics.event("places source=my_places")
            return Resolution.Found(listOf(candidate(mine, 4, false, anchor, 1f, PlaceSource.MyPlaces)))
        }

        val s = Search(anchor, request.aiCallsUsed)
        // P10 address: never the business index; the street that Nominatim knows, as the pick list, or not found.
        if (request.address) {
            gatherAddress(request, name, s)
            lastRequests = s.requests
            val found = dedupe(sortedCandidates(s.candidates)).take(MAX_CANDIDATES)
            WgoMetrics.event("places resolved address n=${found.size} requests=${s.requests}")
            if (found.isNotEmpty()) return Resolution.Found(found, uncertain = true)
            val failure = s.onlineFailure
            return if (failure != null) Resolution.Failed(failure) else Resolution.NotFound(emptyList())
        }
        // P10 category request ("alguna droguería que quede en el centro"): real places of that kind near the hint,
        // nearest first, always the pick list; no model call.
        if (request.vague && request.category != null && PlaceCategories.categoryWord(PlaceText.normalize(request.category)) != null) {
            val near = gatherCategory(request, s)
            lastRequests = s.requests
            if (near.isNotEmpty()) {
                WgoMetrics.event("places resolved category n=${near.size} requests=${s.requests}")
                return Resolution.Found(near, uncertain = true)
            }
        }
        if (request.fromAlias) gatherNickname(name, s) else gatherName(request, name, s)
        // Role 1 (P8): no nickname and only weak candidates -> the model names the landmark (text), searched normally.
        if (!request.fromAlias && chooser != null && ChoiceTriggers.weak(request, sortedCandidates(s.candidates)) && s.takeAiCall()) {
            expand(chooser, request, name, language, s)
        }
        var ordered = dedupe(sortedCandidates(rankByHint(request, s)))
        // Role 2 (P8): the language model picks among the real candidates by id when the ranking is ambiguous.
        if (chooser != null && ChoiceTriggers.shouldChoose(request, ordered) && s.takeAiCall()) {
            ordered = choose(chooser, request, name, ordered, language)
        }

        lastRequests = s.requests
        lastAiCalls = s.aiCalls
        val uncertain = ordered.isNotEmpty() && !clearWinner(request, ordered, lastChoice)
        // The pick list puts the landmarks first (the university before a business that shares a word).
        val best = (if (uncertain) ordered.sortedByDescending { it.landmark } else ordered).take(MAX_CANDIDATES)
        WgoMetrics.event(
            "places resolved n=${best.size} sources=${best.joinToString("+") { it.source.name }} requests=${s.requests} " +
                "ai_calls=${s.aiCalls} hint=${request.hint != null} uncertain=$uncertain choice=$lastChoice"
        )
        if (best.isNotEmpty()) return Resolution.Found(best, uncertain)
        val suggestions = dedupe(s.partial.sortedBy { it.distanceM }).take(MAX_CANDIDATES).map { it.place }
        val failure = s.onlineFailure
        if (failure != null && suggestions.isEmpty()) return Resolution.Failed(failure)
        return Resolution.NotFound(suggestions)
    }

    /**
     * A nickname ("la plaza", "el terminal") is a landmark name verified on Nominatim: asked there first (P7: the local
     * index had matched "Drogueria Plaza De Nariño"). Offline, or with no near hit, only EXACT index rows of the
     * landmark's own names (P8: "UCC Pasto" for the Universidad Cooperativa): "la plaza" never becomes a dental clinic.
     */
    private suspend fun gatherNickname(name: String, s: Search) {
        s.addOnline(s.ask(name, bounded = false), name, nearOnly = true, exactTier = 3)
        if (s.candidates.isNotEmpty() || !indexReady(s.anchor)) return
        for (n in (listOf(name) + PlaceAliases.indexNames(name)).distinct()) {
            s.addIndex(index!!.search(n, s.anchor, minTier = 3, maxKm = FAR_KM))
        }
    }

    /** Any other name: the local index, then Nominatim only when the index has no strong match. */
    private suspend fun gatherName(request: PlaceRequest, name: String, s: Search) {
        if (indexReady(s.anchor)) {
            val query = listOfNotNull(request.category, name).joinToString(" ")
            val hits = index!!.search(query, s.anchor, minTier = 1, maxKm = FAR_KM)
            s.addIndex(hits.filter { it.tier >= 2 })
            s.partial = hits.filter { it.tier == 1 && it.distanceM <= SUGGEST_KM * 1000 }.map {
                PlaceCandidate(it.place.toPlace(), 1, false, it.distanceM, it.place.confidence, PlaceSource.Index)
            }
        }
        if (s.candidates.isNotEmpty()) return
        val hint = request.hint
        val found = if (hint != null) {
            s.ask("$name, $hint", bounded = false).ifEmpty { s.ask(name, bounded = true) }
        } else {
            val first = s.ask(name, bounded = false)
            if (first.any { distance(it, s.anchor) <= FAR_KM * 1000 }) first else s.ask(name, bounded = true).ifEmpty { first }
        }
        s.addOnline(found, name, nearOnly = false) // Nominatim decided it matches: tier 3 for the same name, else 2
    }

    /**
     * P10 address: the street and house number in Pasto, then the street with the neighbourhood, then the street alone
     * (at most [MAX_REQUESTS] requests); near results only.
     */
    private suspend fun gatherAddress(request: PlaceRequest, name: String, s: Search) {
        val street = name.substringBefore(" #").trim()
        val house = name.substringAfter(" #", "").trim()
        val queries = listOfNotNull(
            house.takeIf { it.isNotEmpty() }?.let { "$street $it, Pasto" },
            request.hint?.let { "$street, $it, Pasto" },
            "$street, Pasto"
        )
        val hint = request.hint
        var fallback: Pair<List<Place>, String>? = null
        for (q in queries) {
            val near = s.ask(q, bounded = false).filter { distance(it, s.anchor) <= FAR_KM * 1000 }
            if (near.isEmpty()) continue
            // With a neighbourhood said, keep asking until a result is in it (else the first results stand).
            val inBarrio = hint == null || near.any { HintMatch.textScore(hint, it.name + " " + it.address) > 0.0 }
            if (inBarrio) {
                s.addOnline(near, q, nearOnly = true, exactTier = 2)
                if (hint != null) s.candidates.replaceAll { it.copy(hintScore = HintMatch.textScore(hint, it.place.name + " " + it.place.address)) }
                return
            }
            if (fallback == null) fallback = near to q
        }
        fallback?.let { (near, q) -> s.addOnline(near, q, nearOnly = true, exactTier = 2) }
    }

    /** P10: up to [MAX_CANDIDATES] index places of the request's category, nearest to the hint's point (or the user). */
    private suspend fun gatherCategory(request: PlaceRequest, s: Search): List<PlaceCandidate> {
        if (!indexReady(s.anchor)) return emptyList()
        val center = request.hint?.let { s.ask("$it, Pasto", bounded = true).firstOrNull()?.coord } ?: s.anchor
        return index!!.searchCategory(request.category!!, center, limit = MAX_CANDIDATES).map {
            PlaceCandidate(it.place.toPlace(), 2, true, RadarProjection.distanceMeters(s.anchor, it.place.coord), it.place.confidence, PlaceSource.Index)
        }
    }

    /** Role 1: up to three official names (text) from the model, each searched in the index, then on Nominatim. */
    private suspend fun expand(chooser: PlaceChooser, request: PlaceRequest, name: String, language: Language, s: Search) {
        val names = chooser.expand(request.utterance ?: name, name, language)
        WgoMetrics.event("places ai_expansion names=${names.size}")
        val ready = indexReady(s.anchor)
        for (alt in names) {
            val local = if (ready) index!!.search(alt, s.anchor, minTier = 2, maxKm = FAR_KM, limit = 5) else emptyList()
            s.addIndex(local)
            if (local.isEmpty()) s.addOnline(s.ask(alt, bounded = false), alt, nearOnly = true)
        }
    }

    /**
     * The hint only re-ranks: text match first, then (when that cannot separate the top two) proximity to the hint's
     * own point, at the cost of one more request.
     */
    private suspend fun rankByHint(request: PlaceRequest, s: Search): List<PlaceCandidate> {
        val hint = request.hint ?: return s.candidates
        var ranked = s.candidates.map { it.copy(hintScore = HintMatch.textScore(hint, it.place.name + " " + it.place.address)) }
        val top = sortedCandidates(ranked)
        if (top.size >= 2 && top[0].tier == top[1].tier && top.take(2).all { it.hintScore == 0.0 }) {
            val point = s.ask(hint, bounded = true).firstOrNull()
            if (point != null) {
                ranked = ranked.map {
                    if (RadarProjection.distanceMeters(it.place.coord, point.coord) <= HINT_NEAR_M) it.copy(hintScore = it.hintScore + 0.5) else it
                }
            }
        }
        return ranked
    }

    /** Role 2: the model's pick among the first candidates, by list id only. */
    private suspend fun choose(
        chooser: PlaceChooser, request: PlaceRequest, name: String, ordered: List<PlaceCandidate>, language: Language
    ): List<PlaceCandidate> {
        val options = ordered.take(LlmPlaceChooser.MAX_OPTIONS).mapIndexed { i, c ->
            ChoiceOption("c${i + 1}", c.place.name, c.place.category, c.place.address)
        }
        val id = chooser.choose(request.utterance ?: name, options, language, destination = name)
        var picked = options.indexOfFirst { it.id == id }
        // P10 strict choice: a pick whose name has nothing to do with the destination (or its nickname's names) is
        // ignored, whatever the model says.
        if (picked >= 0 && !relatedToDestination(ordered[picked].place.name, name)) {
            WgoMetrics.event("places ai_choice_ignored=unrelated")
            picked = -1
        }
        WgoMetrics.event("places ai_choice options=${options.size} picked=$picked")
        lastChoice = if (picked >= 0) ChoiceOutcome.Picked else ChoiceOutcome.NoPick
        return promote(ordered, picked)
    }

    private fun candidate(p: Place, tier: Int, boost: Boolean, anchor: GeoCoord, conf: Float, source: PlaceSource) =
        PlaceCandidate(p, tier, boost, distance(p, anchor), conf, source)

    companion object {
        const val MAX_REQUESTS = 3

        /** P10: up to three candidates (the pick list cycles them with OTRO). */
        const val MAX_CANDIDATES = 3

        /**
         * P10 confidence gate (docs/design "Confidence gate and pick list"): straight to Confirm place only for my
         * places, a nickname answered by its landmark, or a top candidate of a strictly better match tier than the
         * runner-up (or the model's related pick); never for a vague request, nor when the model was asked and
         * declined.
         */
        fun clearWinner(request: PlaceRequest, ranked: List<PlaceCandidate>, choice: ChoiceOutcome): Boolean {
            val top = ranked.firstOrNull() ?: return false
            if (top.source == PlaceSource.MyPlaces) return true
            if (request.vague) return false
            if (request.fromAlias && (top.source == PlaceSource.Nominatim || top.tier >= 3)) return true
            if (choice == ChoiceOutcome.NoPick) return false
            if (choice == ChoiceOutcome.Picked) return true
            val second = ranked.getOrNull(1) ?: return true
            return top.tier > second.tier
        }

        /** Language-model calls per utterance, the interpreter's included (docs/PLACE_SEARCH.md, P8). */
        const val MAX_AI_CALLS = 2

        /** Region words every Pasto place may carry: never enough to tie a name to the destination. */
        private val REGION = setOf("pasto", "narino", "colombia", "san", "juan")

        /**
         * P10: [placeName] shares a content word (prefix or one edit allowed) with the destination [name] or with the
         * index names of the nickname it came from ("UCC Pasto" for "Universidad Cooperativa de Colombia").
         */
        fun relatedToDestination(placeName: String, name: String): Boolean {
            val words = (listOf(name) + PlaceAliases.indexNames(name)).flatMap { PlaceText.tokens(it) }.filter { it !in REGION }
            val place = PlaceText.tokens(placeName).filter { it !in REGION }
            return words.any { w -> place.any { p -> PlaceText.tokenMatches(w, p) || PlaceText.tokenMatches(p, w) } }
        }

        /**
         * Moves the candidate at [index] first (the model's pick); out of range: unchanged. A candidate beyond
         * [FAR_KM] never moves ahead of a nearer one.
         */
        fun promote(ordered: List<PlaceCandidate>, index: Int): List<PlaceCandidate> {
            if (index <= 0 || index >= ordered.size) return ordered
            val pick = ordered[index]
            if (pick.distanceM > FAR_KM * 1000 && ordered[0].distanceM <= FAR_KM * 1000) return ordered
            return listOf(pick) + ordered.filterIndexed { i, _ -> i != index }
        }
        const val FAR_KM = 25.0
        const val SUGGEST_KM = 5.0
        const val DEDUPE_M = 150.0
        const val HINT_NEAR_M = 600.0
        const val NOMINATIM_CONFIDENCE = 0.6f

        private fun distance(p: Place, anchor: GeoCoord) = RadarProjection.distanceMeters(p.coord, anchor)

        /** The ranking order of docs/PLACE_SEARCH.md ("Ranking"; landmark and trusted since P8; brand and exactness P11). */
        fun sortedCandidates(list: List<PlaceCandidate>): List<PlaceCandidate> = list.sortedWith(
            compareBy<PlaceCandidate> { it.distanceM > FAR_KM * 1000 }
                .thenByDescending { it.tier }
                .thenByDescending { it.brand }
                .thenBy { it.inexact }
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

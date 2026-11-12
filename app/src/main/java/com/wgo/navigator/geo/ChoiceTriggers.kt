package com.wgo.navigator.geo

import com.wgo.navigator.places.PlaceCategories
import com.wgo.navigator.places.PlaceText

/**
 * When the place search asks the language model (docs/PLACE_SEARCH.md, "Landmarks, nicknames and the language model").
 * Pure, so the rules are unit-tested; clear matches never trigger a call.
 */
object ChoiceTriggers {
    /** Possessives that mark a personal landmark ("mi universidad"). */
    val POSSESSIVES = setOf("mi", "mis", "nuestra", "nuestro", "my", "our")

    /** Descriptor nouns said before a landmark's name ("la universidad cooperativa", "el hospital departamental"). */
    val DESCRIPTORS = setOf("universidad", "u", "hospital", "clinica", "colegio", "estadio", "terminal", "parque", "plaza", "centro", "comercial")

    /** Choice call for these ranked candidates (best first)? [utterance] is the transcript (or the name). */
    fun shouldChoose(request: PlaceRequest, ranked: List<PlaceCandidate>): Boolean {
        if (ranked.size < 2) return false
        val top = ranked[0]
        if (top.source == PlaceSource.MyPlaces) return false
        if (request.fromAlias && top.source == PlaceSource.Nominatim) return false
        val second = ranked[1]
        val said = PlaceText.tokens(request.utterance ?: request.name)
        // (a) two equally good, non-exact candidates of the same kind.
        if (top.tier < 3 && top.tier == second.tier && top.landmark == second.landmark) return true
        // (b) a category the user said (or the interpreter heard) that the top candidate is not.
        val fragments = PlaceCategories.split(said + PlaceText.tokens(request.category.orEmpty())).second
        if (fragments.isNotEmpty() && !PlaceCategories.matches(top.place.category, fragments)) return true
        // (c) a possessive anywhere, or a descriptor noun opening the place words ("hospital", "u", "universidad ...").
        if (said.any { it in POSSESSIVES } || PlaceText.tokens(request.name).firstOrNull() in DESCRIPTORS) return true
        // (d) a one-word query that is only part of a longer, ordinary name (a different entity).
        return differentEntity(request, top)
    }

    /** The candidates are weak: nothing, or only ordinary names that merely contain a short query (expansion role). */
    fun weak(request: PlaceRequest, ranked: List<PlaceCandidate>): Boolean =
        ranked.isEmpty() || ranked.all { it.tier <= 2 && !it.landmark && differentEntity(request, it) }

    private fun differentEntity(request: PlaceRequest, c: PlaceCandidate): Boolean {
        val query = PlaceCategories.core(PlaceText.tokens(request.name))
        val name = PlaceCategories.core(PlaceText.tokens(c.place.name))
        return c.tier == 2 && !c.landmark && query.size == 1 && name.size >= 3
    }
}

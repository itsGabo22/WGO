package com.wgo.navigator.ai

import com.wgo.navigator.Language

/** One real candidate as the language model may see it: public text only, never a coordinate or a distance. */
data class ChoiceOption(val id: String, val name: String, val category: String, val address: String)

/**
 * The language model's roles in place choice (docs/PLACE_SEARCH.md, "Landmarks, nicknames and the language model").
 * Implementations never receive the user's position, distances or coordinates, and every answer is validated.
 */
interface PlaceChooser {
    /** Role 2: the id of the place [utterance] most obviously means, ONLY one of [options]' ids, or null. */
    suspend fun choose(utterance: String, options: List<ChoiceOption>, language: Language): String?

    /** Role 1: up to 3 official place names (text) that [words] may stand for; empty when unsure or unavailable. */
    suspend fun expand(utterance: String, words: String, language: Language): List<String> = emptyList()
}

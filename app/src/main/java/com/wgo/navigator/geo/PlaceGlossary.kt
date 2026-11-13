package com.wgo.navigator.geo

import com.wgo.navigator.ai.LocalIntentParser

/**
 * English place categories to the Spanish words OpenStreetMap uses in Colombia ("bus terminal" -> "terminal de
 * transportes"), for the one local retry of an English search. Whole-word phrases, longest first; proper names pass
 * through unchanged.
 */
object PlaceGlossary {
    private val phrases = listOf(
        "bus terminal" to "terminal de transportes", "bus station" to "terminal de transportes",
        "shopping center" to "centro comercial", "shopping mall" to "centro comercial", "main square" to "plaza",
        "town hall" to "alcaldia", "city hall" to "alcaldia", "police station" to "estacion de policia",
        "fire station" to "estacion de bomberos", "gas station" to "estacion de servicio", "train station" to "estacion",
        "university" to "universidad", "airport" to "aeropuerto", "cathedral" to "catedral", "church" to "iglesia",
        "hospital" to "hospital", "clinic" to "clinica", "stadium" to "estadio", "museum" to "museo", "park" to "parque",
        "square" to "plaza", "market" to "mercado", "mall" to "centro comercial", "library" to "biblioteca",
        "school" to "colegio", "pharmacy" to "farmacia", "supermarket" to "supermercado", "bank" to "banco",
        "hotel" to "hotel", "terminal" to "terminal de transportes"
    ).sortedByDescending { it.first.length }

    private val split = phrases.map { (en, es) -> en.split(' ') to es }

    /** The Spanish form of [query], or null when no category word was found. One pass: output is never re-matched. */
    fun toSpanish(query: String): String? {
        val words = LocalIntentParser.normalize(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
        val out = ArrayList<String>()
        var changed = false
        var i = 0
        while (i < words.size) {
            val hit = split.firstOrNull { (en, _) -> i + en.size <= words.size && en.indices.all { words[i + it] == en[it] } }
            if (hit != null) {
                out += hit.second
                i += hit.first.size
                changed = true
            } else {
                out += words[i++]
            }
        }
        return if (changed) out.joinToString(" ") else null
    }
}

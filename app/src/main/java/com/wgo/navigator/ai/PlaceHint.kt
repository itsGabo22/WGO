package com.wgo.navigator.ai

/**
 * Splits spoken place words into the place NAME and a landmark HINT (docs/PLACE_SEARCH.md): "Cubanitos de la
 * Panamericana" -> ("Cubanitos", "Panamericana"), "Cubanitos cerca de Unicentro" -> ("Cubanitos", "Unicentro").
 * Relation phrases ("cerca de", "al lado de", "junto a", "frente a", "near", "next to") always split; the plain
 * connectors ("de la", "del", "por", "en", "sobre", "on", "by") split only when the hint starts with a street or
 * landmark word, so "Universidad de Nariño" and "Plaza de Nariño" stay whole. The hint only re-ranks, it never filters.
 */
object PlaceHint {
    data class Split(val name: String, val hint: String?)

    private class Connector(text: String, val always: Boolean) {
        val words = text.split(' ')
    }

    private val CONNECTORS = listOf(
        Connector("al lado de", true), Connector("al lado del", true), Connector("cerca de", true),
        Connector("cerca del", true), Connector("junto a", true), Connector("junto al", true),
        Connector("frente a", true), Connector("frente al", true), Connector("next to", true), Connector("near", true),
        Connector("de la", false), Connector("del", false), Connector("de", false), Connector("por la", false),
        Connector("por el", false), Connector("por", false), Connector("en la", false), Connector("en el", false),
        Connector("en", false), Connector("sobre la", false), Connector("sobre el", false), Connector("sobre", false),
        Connector("on", false), Connector("by", false)
    ).sortedByDescending { it.words.size }

    /** Normalised words that make a plain connector a landmark relation ("de la Panamericana", "por la 27"). */
    private val LANDMARK_WORDS = setOf(
        "panamericana", "avenida", "av", "calle", "cl", "carrera", "cra", "kr", "transversal", "diagonal", "barrio",
        "centro", "parque", "terminal", "unicentro", "estadio", "aeropuerto", "glorieta", "esquina", "plazoleta",
        "boyaca", "idema", "torobajo", "chapultepec", "street", "avenue", "road", "downtown", "mall", "square"
    )
    private val ARTICLES = setOf("el", "la", "los", "las", "the")

    fun split(place: String): Split {
        val orig = place.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val norm = orig.map(LocalIntentParser::normalize)
        for (i in 1 until norm.size) {
            for (c in CONNECTORS) {
                val end = i + c.words.size
                if (end >= norm.size) continue
                if (c.words.indices.any { norm[i + it] != c.words[it] }) continue
                var h = end
                while (h < norm.size - 1 && norm[h] in ARTICLES) h++
                if (!c.always && norm[h] !in LANDMARK_WORDS && norm[h].firstOrNull()?.isDigit() != true) continue
                val name = orig.subList(0, i).joinToString(" ")
                val hint = orig.subList(h, orig.size).joinToString(" ")
                return Split(name, hint.ifBlank { null })
            }
        }
        return Split(place.trim(), null)
    }
}

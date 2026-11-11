package com.wgo.navigator.places

import java.text.Normalizer

/**
 * Text rules shared by every local place source (docs/PLACE_SEARCH.md): lower case, accents removed, punctuation to
 * spaces, stop words dropped, and the token comparison (equal, prefix, or one edit for long tokens). The Python index
 * builder (`tools/build_places_index.py`) applies the same normalisation to `name_norm`.
 */
object PlaceText {
    val STOP_WORDS = setOf("de", "la", "el", "los", "las", "del", "en", "por", "y", "a", "al")

    private val COMBINING = Regex("\\p{Mn}+")
    private val NON_ALNUM = Regex("[^0-9a-z]+")

    /** Normalised tokens, stop words removed. */
    fun tokens(text: String): List<String> =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(COMBINING, "")
            .replace(NON_ALNUM, " ")
            .split(' ')
            .filter { it.isNotEmpty() && it !in STOP_WORDS }

    fun normalize(text: String): String = tokens(text).joinToString(" ")

    /**
     * A query token matches a name token when equal, when it is a prefix of it (query >= 3 chars), or, for tokens of
     * >= 5 chars, within one edit (insert, delete or substitute).
     */
    fun tokenMatches(query: String, name: String): Boolean {
        if (query == name) return true
        if (query.length >= 3 && name.startsWith(query)) return true
        return query.length >= 5 && name.length >= 4 && withinOneEdit(query, name)
    }

    /** True when [a] and [b] differ by at most one insertion, deletion or substitution. */
    fun withinOneEdit(a: String, b: String): Boolean {
        if (kotlin.math.abs(a.length - b.length) > 1) return false
        if (a.length > b.length) return withinOneEdit(b, a)
        var i = 0
        var j = 0
        var edits = 0
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) {
                i++; j++
            } else {
                if (++edits > 1) return false
                if (a.length == b.length) i++
                j++
            }
        }
        return edits + (b.length - j) + (a.length - i) <= 1
    }
}

package com.wgo.navigator.places

import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap

/**
 * Text rules shared by every local place source (docs/PLACE_SEARCH.md): lower case, accents removed, punctuation to
 * spaces, stop words dropped, one form per spelling variant ("mr" = "mister", "cra" = "carrera"; P11), and the token
 * comparison with its quality (equal, prefix, Spanish phonetic key, edit distance or trigram overlap, a speech slip).
 * The Python index builder (`tools/build_places_index.py`) writes the plain normalisation to `name_norm`; the variant
 * forms ([canonical]) are applied to the index names when they are loaded.
 */
object PlaceText {
    val STOP_WORDS = setOf("de", "la", "el", "los", "las", "del", "en", "por", "y", "a", "al")

    /** P11: words that only say where a place is; in a NAME they never block an exact match ("Catedral Pasto"). */
    val REGION_WORDS = setOf("pasto", "narino", "colombia")

    private val COMBINING = Regex("\\p{Mn}+")
    private val NON_ALNUM = Regex("[^0-9a-z]+")

    /** P11 spelling variants -> one form (docs/PLACE_SEARCH.md, "Normalisation"). */
    private val VARIANTS = mapOf(
        "mr" to "mister", "mistr" to "mister", "senor" to "mister", "sr" to "mister",
        "cll" to "calle", "cl" to "calle",
        "cra" to "carrera", "kr" to "carrera", "kra" to "carrera", "cr" to "carrera",
        "av" to "avenida", "avda" to "avenida",
        // "S.A." is "s a" in the builder (the stop word "a" dropped) and "sa" when said or protected as an abbreviation
        "sa" to "s"
    )

    /** Normalised tokens, stop words removed, spelling variants unified. */
    fun tokens(text: String): List<String> = canonical(
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(COMBINING, "")
            .replace(NON_ALNUM, " ")
            .split(' ')
            .filter { it.isNotEmpty() && it !in STOP_WORDS }
    )

    fun normalize(text: String): String = tokens(text).joinToString(" ")

    /**
     * One form per variant for already normalised tokens: "mr" -> "mister", "cra" -> "carrera", "cc" and the pair
     * "c c" (from "C.C.") -> "centro comercial". Returns the same list when nothing changes.
     */
    fun canonical(tokens: List<String>): List<String> {
        var changed = false
        for (t in tokens) if (t in VARIANTS || t == "cc" || t == "c") { changed = true; break }
        if (!changed) return tokens
        val out = ArrayList<String>(tokens.size + 1)
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            when {
                t == "cc" -> { out += "centro"; out += "comercial" }
                t == "c" && tokens.getOrNull(i + 1) == "c" -> { out += "centro"; out += "comercial"; i++ }
                else -> out += VARIANTS[t] ?: t
            }
            i++
        }
        return out
    }

    /** A query token matches a name token when [quality] > 0. */
    fun tokenMatches(query: String, name: String): Boolean = quality(query, name) > 0

    /** [quality] of an equal token. */
    const val EQUAL = 4

    /**
     * How well a query token matches a name token (P11): 4 equal; 3 prefix (query >= 3 chars) or the same Spanish
     * phonetic key (both >= 4 chars); 2 within edit distance 1 (5+ chars) or a trigram overlap >= 0.5 (6+ chars);
     * 1 within edit distance 2 (8+ chars) or the speech slip "mi" for "mister"; 0 no match.
     */
    fun quality(query: String, name: String): Int {
        if (query == name) return EQUAL
        if (query.length >= 3 && name.startsWith(query)) return 3
        if (query.length >= 4 && name.length >= 4 && kotlin.math.abs(query.length - name.length) <= 3 &&
            phonetic(query) == phonetic(name)) return 3
        if (query.length >= 5 && name.length >= 4) {
            val max = if (query.length >= 8) 2 else 1
            if (kotlin.math.abs(query.length - name.length) <= max) {
                val d = editDistance(query, name, max)
                if (d <= 1) return 2
                if (d <= max) return 1
            }
            if (query.length >= 6 && name.length >= 6 && query[0] == name[0] && trigramJaccard(query, name) >= 0.5) return 2
        }
        if (query == "mi" && name == "mister") return 1
        return 0
    }

    /** True when [a] and [b] differ by at most one insertion, deletion or substitution. */
    fun withinOneEdit(a: String, b: String): Boolean = kotlin.math.abs(a.length - b.length) <= 1 && editDistance(a, b, 1) <= 1

    /** Levenshtein distance between [a] and [b], or [max] + 1 as soon as it is known to exceed [max]. */
    fun editDistance(a: String, b: String, max: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > max) return max + 1
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (cur[j] < rowMin) rowMin = cur[j]
            }
            if (rowMin > max) return max + 1
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    /** Jaccard overlap of the padded character trigrams of [a] and [b]. */
    fun trigramJaccard(a: String, b: String): Double {
        val ta = trigrams(a)
        val tb = trigrams(b)
        if (ta.isEmpty() || tb.isEmpty()) return 0.0
        val common = ta.count { it in tb }
        return common.toDouble() / (ta.size + tb.size - common)
    }

    private fun trigrams(s: String): Set<String> {
        val p = " $s "
        return (0..p.length - 3).mapTo(HashSet()) { p.substring(it, it + 3) }
    }

    private val phoneticCache = ConcurrentHashMap<String, String>()

    /**
     * A Spanish sound key for a normalised token (P11): b/v, s/z/soft c, ll/y, k/qu/hard c, j/soft g are one sound
     * each, h is silent (except in "ch"), w sounds as u, x as ks, ph as f, and repeated sounds collapse: "kocha" =
     * "cocha", "barver" = "barber", "pozgrados" = "posgrados". Cached per token.
     */
    fun phonetic(token: String): String = phoneticCache.getOrPut(token) { phoneticKey(token) }

    private fun phoneticKey(t: String): String {
        val out = StringBuilder(t.length)
        var i = 0
        fun next(k: Int) = t.getOrNull(i + k)
        while (i < t.length) {
            val c = t[i]
            val s: String = when (c) {
                'b', 'v' -> "b"
                'z' -> "s"
                'c' -> when (next(1)) {
                    'e', 'i' -> "s"
                    'h' -> { i++; "C" }
                    else -> "k"
                }
                'q' -> { if (next(1) == 'u') i++; "k" }
                'k' -> "k"
                'g' -> when (next(1)) {
                    'e', 'i' -> "j"
                    'u' -> if (next(2) == 'e' || next(2) == 'i') { i++; "g" } else "g"
                    else -> "g"
                }
                'h' -> ""
                'l' -> if (next(1) == 'l') { i++; "y" } else "l"
                'y' -> "y"
                'w' -> "u"
                'x' -> "ks"
                'p' -> if (next(1) == 'h') { i++; "f" } else "p"
                else -> c.toString()
            }
            if (s.isNotEmpty() && !(out.isNotEmpty() && out.endsWith(s))) out.append(s)
            i++
        }
        return out.toString()
    }
}

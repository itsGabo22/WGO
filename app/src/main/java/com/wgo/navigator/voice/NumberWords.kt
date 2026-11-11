package com.wgo.navigator.voice

import com.wgo.navigator.Language

/**
 * Whole numbers 0..999 999 as words, Spanish and English, so the spoken guidance reads "doscientos metros" the same way
 * on every text-to-speech engine (spec section 8, spoken guidance).
 */
object NumberWords {
    fun of(n: Int, language: Language): String {
        require(n in 0..999_999) { "out of range: $n" }
        return if (language == Language.ES) es(n) else en(n)
    }

    private val ES_UNITS = listOf(
        "cero", "uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve", "diez", "once", "doce", "trece",
        "catorce", "quince", "dieciséis", "diecisiete", "dieciocho", "diecinueve", "veinte", "veintiuno", "veintidós",
        "veintitrés", "veinticuatro", "veinticinco", "veintiséis", "veintisiete", "veintiocho", "veintinueve"
    )
    private val ES_TENS = listOf("", "", "", "treinta", "cuarenta", "cincuenta", "sesenta", "setenta", "ochenta", "noventa")
    private val ES_HUNDREDS = listOf(
        "", "ciento", "doscientos", "trescientos", "cuatrocientos", "quinientos", "seiscientos", "setecientos",
        "ochocientos", "novecientos"
    )

    private fun es(n: Int): String = when {
        n < 30 -> ES_UNITS[n]
        n < 100 -> ES_TENS[n / 10] + if (n % 10 == 0) "" else " y " + ES_UNITS[n % 10]
        n == 100 -> "cien"
        n < 1000 -> ES_HUNDREDS[n / 100] + if (n % 100 == 0) "" else " " + es(n % 100)
        else -> {
            val thousands = n / 1000
            val head = if (thousands == 1) "mil" else esApocope(es(thousands)) + " mil"
            head + if (n % 1000 == 0) "" else " " + es(n % 1000)
        }
    }

    /** "uno" before a noun-like "mil" becomes "un" ("veintiún mil"). */
    private fun esApocope(words: String): String = when {
        words.endsWith("veintiuno") -> words.removeSuffix("veintiuno") + "veintiún"
        words.endsWith("uno") -> words.removeSuffix("uno") + "un"
        else -> words
    }

    private val EN_UNITS = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve",
        "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"
    )
    private val EN_TENS = listOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")

    private fun en(n: Int): String = when {
        n < 20 -> EN_UNITS[n]
        n < 100 -> EN_TENS[n / 10] + if (n % 10 == 0) "" else "-" + EN_UNITS[n % 10]
        n < 1000 -> EN_UNITS[n / 100] + " hundred" + if (n % 100 == 0) "" else " " + en(n % 100)
        else -> en(n / 1000) + " thousand" + if (n % 1000 == 0) "" else " " + en(n % 1000)
    }
}

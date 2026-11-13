package com.wgo.navigator.ui

import com.wgo.navigator.Language
import java.util.Locale
import kotlin.math.roundToInt

/** "350 m" / "1,8 km" (ES) / "1.8 km" (EN); metres rounded to 10 below 1 km. */
fun formatDistance(meters: Double, language: Language): String {
    val locale = if (language == Language.ES) Locale("es", "CO") else Locale.US
    return if (meters < 1000) "${((meters / 10).roundToInt() * 10).coerceAtLeast(0)} ${stringFor(StringKey.UNIT_M, language)}"
    else String.format(locale, "%.1f %s", meters / 1000, stringFor(StringKey.UNIT_KM, language))
}

/** Whole minutes, at least 1. */
fun minutesOf(seconds: Double): Int = (seconds / 60).roundToInt().coerceAtLeast(1)

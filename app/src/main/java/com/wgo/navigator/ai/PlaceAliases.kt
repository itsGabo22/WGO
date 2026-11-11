package com.wgo.navigator.ai

/**
 * What people in Pasto actually say -> the SEARCH STRING the geocoder gets (never coordinates: the place is still
 * looked up live, so a moved or renamed place keeps working).
 *
 * HOW TO ADD YOUR OWN NICKNAME: add one line to [EXACT] (or to [ANYWHERE] for an acronym that may appear inside a
 * longer phrase), rebuild, done. Write the nickname as people say it, without the leading article ("terminal", not
 * "el terminal"); case and accents do not matter ("nariño" = "narino"). Before adding one, check that its search string
 * finds the place: https://nominatim.openstreetmap.org/ui/search.html?q=<search string>
 *
 * Every entry was verified live on 2026-11-10 with one Nominatim search (the app's own parameters: Colombia, biased to
 * Pasto): each search string returned a hit within 50 km of the Pasto centre. Dropped: "Universidad Nacional Abierta
 * y a Distancia" (only hits outside Nariño; "UNAD" finds the Pasto campus) and "Hospital Universitario Departamental
 * de Nariño" (no hit; "Hospital Departamental de Nariño" finds it).
 */
object PlaceAliases {
    /** The whole place must be exactly the nickname: "la plaza" yes, "la plaza de mercado" no. */
    private val EXACT = mapOf(
        "u de nariño" to "Universidad de Nariño",
        "universidad de nariño" to "Universidad de Nariño",
        "mariana" to "Universidad Mariana",
        "u mariana" to "Universidad Mariana",
        "torobajo" to "Universidad de Nariño",
        "sede torobajo" to "Universidad de Nariño",
        "terminal" to "Terminal de Transportes de Pasto",
        "terminal de transportes" to "Terminal de Transportes de Pasto",
        "bus terminal" to "Terminal de Transportes de Pasto",
        "hospital departamental" to "Hospital Departamental de Nariño",
        "departamental" to "Hospital Departamental de Nariño",
        "plaza" to "Plaza de Nariño",
        "plaza de nariño" to "Plaza de Nariño",
        "main square" to "Plaza de Nariño",
        "estadio" to "Estadio Departamental Libertad",
        "estadio libertad" to "Estadio Departamental Libertad",
        "stadium" to "Estadio Departamental Libertad",
        "aeropuerto" to "Aeropuerto Antonio Nariño",
        "airport" to "Aeropuerto Antonio Nariño"
    )

    /** Distinctive acronyms: they also match as whole words inside a longer place ("la sede de udenar en torobajo"). */
    private val ANYWHERE = mapOf(
        "udenar" to "Universidad de Nariño",
        "unad" to "UNAD",
        "cesmag" to "Universidad CESMAG",
        "hudn" to "Hospital Departamental de Nariño"
    )

    private val exact: Map<String, String> = EXACT.mapKeys { LocalIntentParser.normalize(it.key) }
    private val anywhere: Map<String, String> = ANYWHERE.mapKeys { LocalIntentParser.normalize(it.key) }

    /** All nicknames (normalized), for tests. */
    val keys: Set<String> get() = exact.keys + anywhere.keys

    /** The search string for a place given as normalized words (accents and articles already removed), or null. */
    fun resolve(words: List<String>): String? {
        if (words.isEmpty()) return null
        exact[words.joinToString(" ")]?.let { return it }
        for (w in words) anywhere[w]?.let { return it }
        return null
    }
}

package com.wgo.navigator.ai

/**
 * What people in Pasto actually say -> the landmark they mean (never coordinates: the place is still looked up, so a
 * moved or renamed place keeps working). Each entry has the SEARCH STRING asked on Nominatim first and the name(s) the
 * offline index uses for the same landmark (Overture often has a short form), which answer offline as exact matches.
 *
 * HOW TO ADD YOUR OWN NICKNAME: add one line to [EXACT] (or to [ANYWHERE] for an acronym that may appear inside a
 * longer phrase), rebuild, done. Write the nickname as people say it, without the leading article ("terminal", not
 * "el terminal"); case and accents do not matter ("nariño" = "narino"). Possessives and descriptor nouns in front of it
 * ("mi universidad la cooperativa") are removed before the lookup. Before adding one, check that its search string
 * finds the place: https://nominatim.openstreetmap.org/ui/search.html?q=<search string>
 *
 * Verification: every search string returned the landmark itself within 25 km of the Pasto centre (P7 on 2026-11-10,
 * re-checked and extended in P8 on 2026-11-11: Universidad Cooperativa de Colombia amenity/university, Universidad
 * Mariana, Universidad CESMAG, UNAD, Terminal de Transportes de Pasto amenity/bus_station, Hospital Departamental de
 * Nariño amenity/hospital, Plaza de Nariño place/square, Estadio Departamental Libertad leisure/stadium, Centro Comercial
 * Unicentro, Centro Comercial Bomboná shop/mall, Universidad de Nariño); every index name is a row of
 * `places_pasto.tsv.gzip`. Not added (no verifiable record): "la nacho", "la U" (ambiguous). Plain "Bomboná" on
 * Nominatim is a locality in Consacá, so its nickname searches "Centro Comercial Bomboná".
 */
object PlaceAliases {
    /** A landmark: the Nominatim search string and the local index's own names for it. */
    data class Landmark(val search: String, val indexNames: List<String> = emptyList())

    private val UCC = Landmark("Universidad Cooperativa de Colombia", listOf("UCC Pasto"))
    private val UDENAR = Landmark("Universidad de Nariño", listOf("Universidad de Nariño"))
    private val MARIANA = Landmark("Universidad Mariana", listOf("Universidad Mariana"))
    private val CESMAG = Landmark("Universidad CESMAG", listOf("University Institution CESMAG"))
    private val UNAD = Landmark("UNAD", listOf("Universidad Nacional Abierta Y A Distancia"))
    private val TERMINAL = Landmark("Terminal de Transportes de Pasto", listOf("Terminal De Transportes De Pasto S.A."))
    private val HOSPITAL = Landmark("Hospital Departamental de Nariño", listOf("Hospital Departamental De Nariño"))
    private val PLAZA = Landmark("Plaza de Nariño")
    private val ESTADIO = Landmark("Estadio Departamental Libertad", listOf("Estadio Departamental Libertad"))
    private val AIRPORT = Landmark("Aeropuerto Antonio Nariño")
    private val UNICENTRO = Landmark("Centro Comercial Unicentro", listOf("Unicentro Pasto"))
    private val BOMBONA = Landmark("Centro Comercial Bomboná", listOf("Centro Comercial y Artesanal Bomboná"))

    /**
     * The verified public landmark names (their search strings), P9: the ONLY words the cloud transcription's vocabulary
     * hint may contain (never anything from my places, history or the user's position).
     */
    val landmarkNames: List<String> = listOf(
        UCC, UDENAR, MARIANA, CESMAG, UNAD, TERMINAL, HOSPITAL, PLAZA, ESTADIO, AIRPORT, UNICENTRO, BOMBONA
    ).map { it.search }

    /** The whole place must be exactly the nickname: "la plaza" yes, "la plaza de mercado" no. */
    private val EXACT = mapOf(
        "cooperativa" to UCC,
        "u cooperativa" to UCC,
        "universidad cooperativa" to UCC,
        "universidad cooperativa de colombia" to UCC,
        "u de nariño" to UDENAR,
        "universidad de nariño" to UDENAR,
        "torobajo" to UDENAR,
        "sede torobajo" to UDENAR,
        "mariana" to MARIANA,
        "u mariana" to MARIANA,
        "universidad mariana" to MARIANA,
        "universidad cesmag" to CESMAG,
        "terminal" to TERMINAL,
        "terminal de transportes" to TERMINAL,
        "terminal de pasto" to TERMINAL,
        "bus terminal" to TERMINAL,
        "hospital departamental" to HOSPITAL,
        "departamental" to HOSPITAL,
        "plaza" to PLAZA,
        "plaza de nariño" to PLAZA,
        "main square" to PLAZA,
        "estadio" to ESTADIO,
        "estadio libertad" to ESTADIO,
        "estadio departamental" to ESTADIO,
        "stadium" to ESTADIO,
        "aeropuerto" to AIRPORT,
        "airport" to AIRPORT,
        "unicentro" to UNICENTRO,
        "centro comercial unicentro" to UNICENTRO,
        "bombona" to BOMBONA,
        "centro comercial bombona" to BOMBONA
    )

    /** Distinctive acronyms: they also match as whole words inside a longer place ("la sede de udenar en torobajo"). */
    private val ANYWHERE = mapOf(
        "udenar" to UDENAR,
        "unad" to UNAD,
        "cesmag" to CESMAG,
        "hudn" to HOSPITAL,
        "ucc" to UCC
    )

    /** Possessives removed anywhere before the lookup ("mi universidad"). */
    private val POSSESSIVES = setOf("mi", "mis", "nuestra", "nuestro", "my", "our")

    /** Descriptor nouns (and articles) removed from the front before the lookup ("universidad la cooperativa"). */
    private val LEADING = setOf(
        "universidad", "u", "hospital", "clinica", "colegio", "centro", "comercial", "estadio", "terminal", "parque",
        "plaza", "el", "la", "los", "las", "the"
    )

    private val exact: Map<String, Landmark> = EXACT.mapKeys { LocalIntentParser.normalize(it.key) }
    private val anywhere: Map<String, Landmark> = ANYWHERE.mapKeys { LocalIntentParser.normalize(it.key) }
    private val bySearch: Map<String, Landmark> = (EXACT.values + ANYWHERE.values).associateBy { it.search }

    /** All nicknames (normalized), for tests. */
    val keys: Set<String> get() = exact.keys + anywhere.keys

    /** Every landmark (for tests and the verification table). */
    val landmarks: Set<Landmark> get() = bySearch.values.toSet()

    /** The search string for a place given as normalized words (accents and leading articles removed), or null. */
    fun resolve(words: List<String>): String? = landmark(words)?.search

    /** The index's own names for the landmark whose search string is [search] (empty when none or unknown). */
    fun indexNames(search: String): List<String> = bySearch[search]?.indexNames.orEmpty()

    private fun landmark(words: List<String>): Landmark? {
        if (words.isEmpty()) return null
        exact[words.joinToString(" ")]?.let { return it }
        for (w in words) anywhere[w]?.let { return it }
        // "mi universidad la cooperativa" -> "universidad la cooperativa" -> "la cooperativa" -> "cooperativa":
        // a nickname is tried after each possessive or leading descriptor is removed.
        var rest = words.filterNot { it in POSSESSIVES }
        while (rest.isNotEmpty()) {
            exact[rest.joinToString(" ")]?.let { return it }
            if (rest.first() !in LEADING) break
            rest = rest.drop(1)
        }
        return null
    }
}

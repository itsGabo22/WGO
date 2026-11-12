package com.wgo.navigator.places

/**
 * Category words people put in front of a name ("restaurante La Merced", "droguería Pasteur"), as normalised tokens,
 * with the fragments of the Overture category ids they mean. A category word is matched against the candidate's
 * category (a soft boost) instead of its name, and the same words are ignored inside candidate names, so "Restaurante
 * La Merced" and "La Merced" share the core name "merced".
 */
object PlaceCategories {
    private val WORDS: Map<String, List<String>> = mapOf(
        "restaurante" to listOf("restaurant", "eatery", "food"), "restaurant" to listOf("restaurant", "eatery", "food"),
        "comidas" to listOf("restaurant", "food"), "pizzeria" to listOf("pizza", "restaurant"),
        "cafe" to listOf("cafe", "coffee"), "cafeteria" to listOf("cafe", "coffee", "restaurant"),
        "panaderia" to listOf("bakery"), "bakery" to listOf("bakery"), "heladeria" to listOf("ice_cream", "dessert"),
        "bar" to listOf("bar", "pub"), "discoteca" to listOf("dance_club", "night"),
        "drogueria" to listOf("pharmacy", "drugstore"), "farmacia" to listOf("pharmacy", "drugstore"),
        "pharmacy" to listOf("pharmacy", "drugstore"),
        "universidad" to listOf("college", "university"), "university" to listOf("college", "university"),
        "colegio" to listOf("school", "education"), "escuela" to listOf("school", "education"),
        "school" to listOf("school", "education"),
        "hotel" to listOf("hotel", "lodging", "hostel"), "hostal" to listOf("hostel", "hotel", "lodging"),
        "iglesia" to listOf("worship", "church"), "templo" to listOf("worship", "church"),
        "church" to listOf("worship", "church"),
        "hospital" to listOf("hospital", "health"), "clinica" to listOf("clinic", "hospital", "health"),
        "clinic" to listOf("clinic", "hospital", "health"),
        "supermercado" to listOf("supermarket", "grocery"), "supermarket" to listOf("supermarket", "grocery"),
        "banco" to listOf("bank", "financial"), "bank" to listOf("bank", "financial"),
        "ferreteria" to listOf("hardware"), "gimnasio" to listOf("gym", "fitness"), "gym" to listOf("gym", "fitness"),
        "cine" to listOf("movie", "cinema"), "cinema" to listOf("movie", "cinema")
    )

    fun isCategoryWord(token: String) = token in WORDS

    /**
     * Splits normalised query tokens into the core name tokens and the category fragments. Category words stay in the
     * core when nothing else would be left ("hotel" alone is a name to search).
     */
    fun split(tokens: List<String>): Pair<List<String>, List<String>> {
        val core = tokens.filterNot(::isCategoryWord)
        if (core.isEmpty()) return tokens to emptyList()
        return core to tokens.filter(::isCategoryWord).flatMap { WORDS.getValue(it) }
    }

    /** The name tokens compared with a query: category words dropped unless nothing else is left. */
    fun core(nameTokens: List<String>): List<String> = nameTokens.filterNot(::isCategoryWord).ifEmpty { nameTokens }

    fun matches(category: String, fragments: List<String>): Boolean =
        fragments.isNotEmpty() && fragments.any { category.contains(it) }

    /**
     * Fragments of the categories people use as destinations (docs/PLACE_SEARCH.md, "Landmark prior"): education,
     * health, shopping centre, transport hub, government, park or plaza, stadium. Matched against Overture categories
     * ("college_university", "shopping_mall", "bus_station") and OpenStreetMap class:type ("amenity:university",
     * "shop:mall", "place:square", "leisure:stadium").
     */
    private val LANDMARK = listOf(
        "university", "college", "hospital", "shopping_mall", "shopping_center", ":mall", "bus_station", "airport",
        "aerodrome", "train_station", "townhall", "town_hall", "city_hall", "government", "park", "square", "plaza",
        "stadium"
    )

    /** True for a landmark category. "parking" is a car park, not a park. */
    fun isLandmark(category: String): Boolean {
        val c = category.lowercase()
        if (c.isEmpty() || c.contains("parking")) return false
        return LANDMARK.any { c.contains(it) }
    }
}

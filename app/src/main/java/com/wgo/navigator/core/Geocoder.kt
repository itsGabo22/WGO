package com.wgo.navigator.core

import com.wgo.navigator.ui.map.GeoCoord

/** A geocoding hit. */
data class Place(
    /** Short display name ("Universidad de Nariño"). */
    val name: String,
    /** Secondary line (street, neighbourhood); may be empty. */
    val address: String,
    val coord: GeoCoord
)

/**
 * Text to places, biased toward [near] (the user's position or the Pasto centre). Named after the role, not the
 * platform class `android.location.Geocoder`. Never throws.
 */
interface Geocoder {
    suspend fun search(query: String, near: GeoCoord?, limit: Int = 3): Outcome<List<Place>>
}

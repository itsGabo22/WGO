package com.wgo.navigator.core

import com.wgo.navigator.Language
import com.wgo.navigator.ui.map.GeoCoord

/** Where a place's data comes from: decides the credit shown with it (spec section 7.1). */
enum class PlaceOrigin { OpenStreetMap, Overture, Private }

/** A geocoding hit. */
data class Place(
    /** Short display name ("Universidad de Nariño"). */
    val name: String,
    /** Secondary line (street, neighbourhood); may be empty. */
    val address: String,
    val coord: GeoCoord,
    val origin: PlaceOrigin = PlaceOrigin.OpenStreetMap,
    /**
     * What kind of place it is, for the ranking only (never shown): the Overture category ("college_university") or
     * the OpenStreetMap class and type ("amenity:university"); empty when unknown.
     */
    val category: String = ""
)

/**
 * Text to places, biased toward [near] (the user's position or the Pasto centre), names in [language]. Named after the
 * role, not the platform class `android.location.Geocoder`. Never throws; no hit is [WgoFailure.NotFound].
 */
interface Geocoder {
    suspend fun search(query: String, near: GeoCoord?, language: Language = Language.ES, limit: Int = 3): Outcome<List<Place>>
}

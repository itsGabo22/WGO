package com.wgo.navigator.core

import com.wgo.navigator.Language
import com.wgo.navigator.TravelMode

/** What the user wants, as far as the app can act on it. Nothing else can ever be triggered by a transcript. */
enum class IntentKind { Navigate, ChangeMode, Cancel, Repeat, Unknown }

/** The interpretation of one transcript. */
data class Interpretation(
    val kind: IntentKind,
    /** Search text for the geocoder ("Universidad de Nariño"); only for [IntentKind.Navigate]. */
    val placeQuery: String?,
    /** Travel mode if the user said it ("a pie", "en carro"); null lets the user choose. */
    val mode: TravelMode?,
    /** 0..1; at or above [ACCEPT_CONFIDENCE] the local result is used with no network call. */
    val confidence: Float,
    /** Optional short reply text (never shown as an instruction, never executed). */
    val reply: String? = null,
    /** A landmark that locates the place ("Panamericana" in "Cubanitos de la Panamericana"); only re-ranks. */
    val placeHint: String? = null,
    /** A category word the user said ("restaurante"); only a soft ranking boost. */
    val placeCategory: String? = null,
    /** [placeQuery] came from a Pasto nickname (PlaceAliases): a search string verified on Nominatim. */
    val placeFromAlias: Boolean = false,
    /** P10: the place is a description or a kind of place ("un lugar donde pueda comer algo"), not a name. */
    val placeVague: Boolean = false,
    /** P10: the destination words as heard ("la cooperativa"), shown on Confirm place to spot a mishearing. */
    val placeHeard: String? = null,
    /** P10: [placeQuery] is a street address ("Calle 6A #36-41"); [placeHint] then holds its neighbourhood. */
    val placeIsAddress: Boolean = false
) {
    companion object {
        const val ACCEPT_CONFIDENCE = 0.75f
        val Unknown = Interpretation(IntentKind.Unknown, null, null, 0f)
    }
}

/** Turns a transcript into an [Interpretation]. Never throws; [IntentKind.Unknown] means nothing usable was said. */
interface IntentInterpreter {
    suspend fun interpret(transcript: String, language: Language): Interpretation
}

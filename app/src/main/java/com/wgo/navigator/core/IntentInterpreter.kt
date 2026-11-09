package com.wgo.navigator.core

import com.wgo.navigator.Language
import com.wgo.navigator.TravelMode

/** What the user asked for, extracted from a transcript. */
data class DestinationIntent(
    /** Search text for the geocoder ("Universidad de Nariño"). */
    val query: String,
    /** Travel mode if the user said it ("caminando", "en carro"); null lets the user choose. */
    val mode: TravelMode?,
    /** The transcript the intent came from. */
    val transcript: String
)

/** Turns a transcript into a [DestinationIntent]. Never throws; [WgoFailure.NotFound] means nothing usable was said. */
interface IntentInterpreter {
    suspend fun interpret(transcript: String, language: Language): Outcome<DestinationIntent>
}

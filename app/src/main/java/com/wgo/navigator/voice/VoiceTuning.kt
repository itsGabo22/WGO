package com.wgo.navigator.voice

/** Settings "Tono" (spec section 7, P8): the value handed to `TextToSpeech.setPitch`. */
enum class VoicePitch(val value: Float) {
    Low(0.85f), Normal(1.0f), High(1.15f);

    /** The next value of the row's cycle: LOW -> NORMAL -> HIGH -> LOW. */
    fun next(): VoicePitch = entries[(ordinal + 1) % entries.size]
}

/** Settings "Velocidad" (spec section 7, P8): the value handed to `TextToSpeech.setSpeechRate`. */
enum class VoiceRate(val value: Float) {
    Slow(0.85f), Normal(1.0f), Fast(1.2f);

    /** The next value of the row's cycle: SLOW -> NORMAL -> FAST -> SLOW. */
    fun next(): VoiceRate = entries[(ordinal + 1) % entries.size]
}

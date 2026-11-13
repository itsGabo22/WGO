package com.wgo.navigator.voice

/**
 * Turns the recognizer's RMS dB readings (roughly -2..10 dB on Android) into a smoothed 0..1 level: fast attack so
 * the bars jump with the voice, slow release so they do not flicker between syllables. Pure, for unit tests.
 */
class LevelSmoother(
    private val minDb: Float = -2f,
    private val maxDb: Float = 10f,
    private val attack: Float = 0.6f,
    private val release: Float = 0.15f
) {
    var value: Float = 0f
        private set

    fun push(rmsDb: Float): Float {
        val target = if (rmsDb.isNaN()) 0f else ((rmsDb - minDb) / (maxDb - minDb)).coerceIn(0f, 1f)
        val k = if (target > value) attack else release
        value += (target - value) * k
        return value
    }

    fun reset() {
        value = 0f
    }
}

/**
 * The live voice level shared between the ViewModel (writer) and the MicOrb draw lambda (reader). A plain volatile
 * float, NOT Compose state: the orb redraws every frame from the screen clock while listening anyway, so reading it
 * in the draw phase never triggers a recomposition.
 */
class VoiceLevel {
    @Volatile
    var value: Float = 0f
}

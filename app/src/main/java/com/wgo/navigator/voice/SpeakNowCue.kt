package com.wgo.navigator.voice

/**
 * The speak-now cue (spec section 7 / 8): when the recognizer becomes ready more than [SLOW_START_MS] after the mic
 * press, one light Tick tells the user to speak now (people start talking during a slow start and the recognizer does
 * not hear them). After a faster start the press Tick is enough. At most one cue per press. Pure, for unit tests.
 */
class SpeakNowCue(private val now: () -> Long) {
    private var pressedAt = NO_PRESS

    /** The mic was pressed: a new session starts preparing. */
    fun onPress() {
        pressedAt = now()
    }

    /** How long the current session has been (or was) preparing, in ms; -1 without a press. */
    fun preparingMs(): Long = if (pressedAt == NO_PRESS) -1 else now() - pressedAt

    /** The recognizer is ready: true when the cue should play. Consumes the press, so a session cues at most once. */
    fun onReady(): Boolean {
        if (pressedAt == NO_PRESS) return false
        val slow = now() - pressedAt > SLOW_START_MS
        pressedAt = NO_PRESS
        return slow
    }

    companion object {
        const val SLOW_START_MS = 600L
        private const val NO_PRESS = Long.MIN_VALUE
    }
}

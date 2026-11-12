package com.wgo.navigator.voice

import android.os.SystemClock
import android.util.Log

/**
 * Structured log of the voice path under the tag [TAG], in EVERY build type (release included) so a field problem can
 * be read with `adb logcat -s WgoVoice` on the owner's watch. One short line per step: the mic press, the warm-up,
 * each recognizer instance, ready, speech, every error code with the action taken, restarts and the final outcome,
 * each with `+ms` since the press. Never a transcript, only its length; never a location, key or identifier.
 */
object VoiceLog {
    const val TAG = "WgoVoice"

    @Volatile
    private var pressAt = 0L

    /** The mic press (or a retry): later lines count their `+ms` from here. */
    fun press(extra: String = "") {
        pressAt = now()
        write("press$extra")
    }

    /** Milliseconds since the last press (0 when there was none). */
    fun sincePress(): Long = if (pressAt == 0L) 0L else now() - pressAt

    private fun now(): Long = try {
        SystemClock.elapsedRealtime()
    } catch (e: RuntimeException) {
        0L // JVM unit tests: SystemClock is a stub there.
    }

    fun event(text: String) = write(text)

    private fun write(text: String) {
        try {
            Log.i(TAG, "$text +${sincePress()}ms")
        } catch (e: RuntimeException) {
            // JVM unit tests: android.util.Log is a stub there.
        }
    }
}

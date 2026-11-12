package com.wgo.navigator.voice

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.speech.tts.TextToSpeech

/**
 * Brings a recognition service back when the system holds it in its restart backoff (spec section 7.1, P8).
 *
 * Measured on the Galaxy Watch5 Pro: the Google recognition service's process had died, ActivityManager scheduled the
 * restart 30-120 s later and kept postponing it under memory pressure, so a mic press waited for nothing. When a
 * process starts for any reason the system also starts the services waiting to restart in it, and Google's recognizer
 * shares its main process with Google's text-to-speech engine: binding that engine (no audio is ever played) starts the
 * process. Only done when the selected recognizer's package is also a TTS engine; the engine is released when it
 * answers, or after [RELEASE_AFTER_MS]. Main thread only.
 */
object RecognizerRevive {
    private const val RELEASE_AFTER_MS = 5_000L

    /** Binds the recognizer package's TTS engine for a moment. False when there is nothing to poke. */
    fun poke(context: Context): Boolean {
        val app = context.applicationContext
        val selected = Settings.Secure.getString(app.contentResolver, "voice_recognition_service")
        val pkg = selected?.let(ComponentName::unflattenFromString)?.packageName ?: return false
        val engine = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE).setPackage(pkg)
        if (app.packageManager.queryIntentServices(engine, 0).isEmpty()) return false
        val t0 = SystemClock.elapsedRealtime()
        var tts: TextToSpeech? = null
        val release = Runnable {
            tts?.shutdown()
            tts = null
        }
        val handler = Handler(Looper.getMainLooper())
        tts = TextToSpeech(app, { status ->
            VoiceLog.event("revive engine_answered status=$status ms=${SystemClock.elapsedRealtime() - t0}")
            handler.post(release)
        }, pkg)
        handler.postDelayed(release, RELEASE_AFTER_MS)
        return true
    }
}

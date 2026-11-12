package com.wgo.navigator.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi
import com.wgo.navigator.metrics.WgoMetrics

/**
 * Binds the system recognition service during the cold-start intro (API 33+ `checkRecognitionSupport`, which never
 * opens the microphone) so the first mic press finds it warm; released 8 s after the intro ends (Home idle keeps 0
 * recognizers). Measured on the Galaxy Watch5 Pro (debug build, recognizer service force-stopped): first press ready in
 * 8.8-22.6 s (median 11.1) instead of 17.4-18.8 s (median 18.3). Main thread only.
 */
object RecognizerWarmUp {
    private var recognizer: SpeechRecognizer? = null

    fun start(context: Context, languageTag: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || recognizer != null) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) return
        warm(context.applicationContext, languageTag)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun warm(context: Context, languageTag: String) {
        val t0 = SystemClock.elapsedRealtime()
        val r = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = r
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        r.checkRecognitionSupport(intent, context.mainExecutor, object : RecognitionSupportCallback {
            override fun onSupportResult(support: RecognitionSupport) {
                WgoMetrics.event("voice_warm ok ms=${SystemClock.elapsedRealtime() - t0}")
            }

            override fun onError(error: Int) {
                WgoMetrics.event("voice_warm error=$error ms=${SystemClock.elapsedRealtime() - t0}")
            }
        })
        WgoMetrics.event("voice_warm start")
    }

    fun stop() {
        val r = recognizer ?: return
        r.destroy()
        recognizer = null
        WgoMetrics.event("voice_warm released")
    }
}

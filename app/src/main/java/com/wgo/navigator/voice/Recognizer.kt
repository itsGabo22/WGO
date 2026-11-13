package com.wgo.navigator.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** One recognition attempt: the BCP-47 [languageTag] and whether the offline model is preferred. */
data class RecognizerRequest(val languageTag: String, val preferOffline: Boolean)

/** The callbacks [InAppVoiceInput] needs, without Android types (so a fake can drive it in JVM tests). */
interface RecognizerListener {
    fun onReady()
    fun onBeginningOfSpeech()
    fun onRms(rmsDb: Float)
    fun onPartial(text: String)
    fun onResults(text: String?)
    fun onError(code: Int)
}

/**
 * One speech recognizer instance. [start] may be called once per instance; [destroy] releases it (it must not be used
 * afterwards). Created and driven on the main thread.
 */
interface Recognizer {
    fun start(request: RecognizerRequest, listener: RecognizerListener)
    fun stop()
    fun cancel()
    fun destroy()
}

/** The platform [SpeechRecognizer] behind [Recognizer]. */
class AndroidRecognizer(private val context: Context) : Recognizer {
    private val recognizer = SpeechRecognizer.createSpeechRecognizer(context)

    override fun start(request: RecognizerRequest, listener: RecognizerListener) {
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = listener.onReady()
            override fun onBeginningOfSpeech() = listener.onBeginningOfSpeech()
            override fun onRmsChanged(rmsdB: Float) = listener.onRms(rmsdB)
            override fun onPartialResults(partialResults: Bundle?) {
                best(partialResults)?.let(listener::onPartial)
            }
            override fun onResults(results: Bundle?) = listener.onResults(best(results))
            override fun onError(error: Int) = listener.onError(error)
            override fun onEndOfSpeech() = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        recognizer.startListening(intent(request))
    }

    override fun stop() = recognizer.stopListening()
    override fun cancel() = recognizer.cancel()
    override fun destroy() = recognizer.destroy()

    private fun intent(request: RecognizerRequest) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, request.languageTag)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, request.languageTag)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, request.preferOffline)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, InAppVoiceInput.SILENCE_AFTER_SPEECH_MS)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
    }

    private fun best(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull { it.isNotBlank() }?.trim()
}

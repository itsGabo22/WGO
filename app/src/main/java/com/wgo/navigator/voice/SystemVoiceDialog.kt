package com.wgo.navigator.voice

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import com.wgo.navigator.Language
import com.wgo.navigator.core.VoiceEvent
import com.wgo.navigator.core.WgoFailure
import com.wgo.navigator.core.speechTag
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Fallback strategy 2: the system speech activity (`RecognizerIntent.ACTION_RECOGNIZE_SPEECH`), the official Wear OS
 * route when the in-app recognizer is unusable. Like the permission broker it never touches an Activity: [request]
 * posts the intent on [requests], the Activity launches it with `StartActivityForResult` and reports back through
 * [onResult]. One dialog at a time. The system activity records the audio itself, so no RECORD_AUDIO is needed.
 */
class SystemVoiceDialog {
    private val _requests = Channel<Intent>(Channel.CONFLATED)

    /** Collected by the Activity, which launches each intent for a result. */
    val requests: Flow<Intent> = _requests.receiveAsFlow()

    @Volatile
    private var pending: CompletableDeferred<VoiceEvent>? = null

    /** Shows the system dialog and suspends until it answers with [VoiceEvent.Final] or [VoiceEvent.Failed]. */
    suspend fun request(language: Language): VoiceEvent {
        pending?.complete(VoiceEvent.Failed(WgoFailure.Cancelled))
        val deferred = CompletableDeferred<VoiceEvent>()
        pending = deferred
        VoiceLog.event("dialog_requested")
        _requests.trySend(intent(language))
        return try {
            deferred.await()
        } finally {
            if (pending === deferred) pending = null
        }
    }

    /** Called by the Activity with the dialog's result. */
    fun onResult(resultCode: Int, data: Intent?) {
        val text = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull { it.isNotBlank() }?.trim()
        val event = when {
            resultCode == Activity.RESULT_OK && text != null -> VoiceEvent.Final(text)
            resultCode == Activity.RESULT_OK -> VoiceEvent.Failed(WgoFailure.NotFound)
            else -> VoiceEvent.Failed(WgoFailure.Cancelled)
        }
        if (pending != null) VoiceLog.event("dialog_result code=$resultCode len=${text?.length ?: -1}")
        pending?.complete(event)
    }

    /** Called by the Activity when no activity could be launched. */
    fun onUnavailable() {
        VoiceLog.event("dialog_unavailable")
        pending?.complete(VoiceEvent.Failed(WgoFailure.Unavailable))
    }

    companion object {
        fun intent(language: Language): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.speechTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language.speechTag)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        /** True when some activity on the device handles the system speech intent. */
        fun isAvailable(context: Context): Boolean = intent(Language.ES).resolveActivity(context.packageManager) != null
    }
}

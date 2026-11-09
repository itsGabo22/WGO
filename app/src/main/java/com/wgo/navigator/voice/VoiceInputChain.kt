package com.wgo.navigator.voice

import android.content.Context
import com.wgo.navigator.Language
import com.wgo.navigator.core.VoiceEvent
import com.wgo.navigator.core.VoiceInput
import com.wgo.navigator.core.WgoFailure
import com.wgo.navigator.metrics.WgoMetrics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

enum class VoiceStrategy { InApp, SystemDialog, None }

/**
 * The voice strategy chain: (1) the in-app recognizer when a recognition service is available AND selected;
 * (2) otherwise the system speech activity; (3) otherwise [WgoFailure.Unavailable]. When strategy 1 fails before it is
 * ready with an "unavailable" error (the documented "no selected voice recognition service" case on Galaxy Watch4/5)
 * the same session continues with strategy 2, and later sessions start there directly.
 */
class VoiceInputChain(
    context: Context,
    private val inApp: InAppVoiceInput = InAppVoiceInput(context),
    val dialog: SystemVoiceDialog = SystemVoiceDialog()
) : VoiceInput {
    private val app = context.applicationContext

    @Volatile
    private var inAppBroken = false

    /** The strategy the next session will use (re-evaluated every time: services can be installed or selected). */
    fun strategy(): VoiceStrategy = when {
        !inAppBroken && InAppVoiceInput.isAvailable(app) -> VoiceStrategy.InApp
        SystemVoiceDialog.isAvailable(app) -> VoiceStrategy.SystemDialog
        else -> VoiceStrategy.None
    }

    override fun cancel() {
        inApp.cancel()
        dialog.onResult(android.app.Activity.RESULT_CANCELED, null)
    }

    override fun listen(language: Language): Flow<VoiceEvent> = flow {
        val strategy = strategy()
        WgoMetrics.event("voice_strategy=${strategy.name}")
        when (strategy) {
            VoiceStrategy.InApp -> {
                var ready = false
                var fallBack = false
                inApp.listen(language).collect { event ->
                    if (event == VoiceEvent.Ready) ready = true
                    val earlyUnavailable = event is VoiceEvent.Failed && !ready && event.failure == WgoFailure.Unavailable
                    if (earlyUnavailable && SystemVoiceDialog.isAvailable(app)) fallBack = true else emit(event)
                }
                if (fallBack) {
                    inAppBroken = true
                    WgoMetrics.event("voice_strategy=SystemDialog reason=inapp_failed_before_ready")
                    emit(VoiceEvent.AwaitingSystem)
                    emit(dialog.request(language))
                }
            }
            VoiceStrategy.SystemDialog -> {
                emit(VoiceEvent.AwaitingSystem)
                emit(dialog.request(language))
            }
            VoiceStrategy.None -> emit(VoiceEvent.Failed(WgoFailure.Unavailable))
        }
    }
}

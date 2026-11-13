package com.wgo.navigator.voice

import android.content.Context
import com.wgo.navigator.Language
import com.wgo.navigator.core.VoiceEvent
import com.wgo.navigator.core.VoiceInput
import com.wgo.navigator.core.WgoFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.wgo.navigator.net.isOnline

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
    val dialog: SystemVoiceDialog = SystemVoiceDialog(),
    online: (() -> Boolean)? = null
) : VoiceInput {
    private val app = context.applicationContext

    /** P10: without a network the system speech dialog is never opened (it only says "no network" in its own UI). */
    private val online: () -> Boolean = online ?: { isOnline(app) }

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
        VoiceLog.event("strategy=${strategy.name}")
        when (strategy) {
            VoiceStrategy.InApp -> {
                var ready = false
                var fallBack = false
                inApp.listen(language).collect { event ->
                    if (event == VoiceEvent.Ready) ready = true
                    if (event == VoiceEvent.Restarting) ready = false // a false "ready": the fallback is possible again
                    val earlyUnavailable = event is VoiceEvent.Failed && !ready && event.failure == WgoFailure.Unavailable
                    if (earlyUnavailable && SystemVoiceDialog.isAvailable(app)) fallBack = true else emit(event)
                }
                if (fallBack) {
                    if (!online()) {
                        // A failure caused by a missing network: no dialog, and later presses keep the in-app recognizer.
                        VoiceLog.event("strategy=none reason=offline")
                        emit(VoiceEvent.Failed(WgoFailure.Offline))
                    } else {
                        inAppBroken = true
                        VoiceLog.event("strategy=SystemDialog reason=inapp_failed_before_ready")
                        emit(VoiceEvent.AwaitingSystem)
                        emit(dialog.request(language))
                    }
                }
            }
            VoiceStrategy.SystemDialog -> {
                if (!online()) {
                    VoiceLog.event("strategy=none reason=offline")
                    emit(VoiceEvent.Failed(WgoFailure.Offline))
                } else {
                    emit(VoiceEvent.AwaitingSystem)
                    emit(dialog.request(language))
                }
            }
            VoiceStrategy.None -> emit(VoiceEvent.Failed(WgoFailure.Unavailable))
        }
    }
}

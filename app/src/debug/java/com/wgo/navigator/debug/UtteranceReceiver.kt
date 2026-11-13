package com.wgo.navigator.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import com.wgo.navigator.voice.DebugUtterances

/**
 * Debug builds only (this source set is not in release): injects an utterance into the voice pipeline, as if the
 * recognizer had produced it. Extras: `text` (or `text_b64`, UTF-8 Base64, for accents through adb) and optional
 * `lang` (`es` / `en`).
 *
 * `adb shell am broadcast -a com.wgo.navigator.debug.UTTERANCE -p com.wgo.navigator --es text "el exito"`
 */
class UtteranceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra(EXTRA_TEXT)
            ?: intent.getStringExtra(EXTRA_TEXT_B64)?.let { encoded ->
                runCatching { String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8) }.getOrNull()
            }
            ?: return
        DebugUtterances.inject(text, intent.getStringExtra(EXTRA_LANG))
    }

    private companion object {
        const val EXTRA_TEXT = "text"
        const val EXTRA_TEXT_B64 = "text_b64"
        const val EXTRA_LANG = "lang"
    }
}

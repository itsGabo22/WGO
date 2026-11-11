package com.wgo.navigator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.wgo.navigator.Language
import com.wgo.navigator.ui.StringKey
import com.wgo.navigator.ui.string
import com.wgo.navigator.ui.theme.Wgo
import com.wgo.navigator.ui.theme.WgoType
import com.wgo.navigator.ui.theme.wgoTouchSide
import com.wgo.navigator.voice.GuidancePhrases
import com.wgo.navigator.voice.SpeechEngine
import com.wgo.navigator.voice.VoiceChooser
import com.wgo.navigator.voice.VoiceLabels

/** At most this many voices are listed (spec section 7, voice picker). */
private const val MAX_VOICES = 4
private const val PITCH = 57f

/**
 * The voice picker (spec section 7, P7): title "VOZ", up to 4 installed voices of the UI language as selectable pills
 * (the chosen one `primary`), and "PROBAR", which speaks a sample with it on any output. The text-to-speech engine
 * exists only while this screen is shown. Choosing persists through [onChoose] (silent).
 */
@Composable
fun ScreenVoicePicker(language: Language, selected: String?, onChoose: (String?) -> Unit, modifier: Modifier = Modifier) {
    val palette = Wgo.palette
    val context = LocalContext.current
    val engine = remember(language) { SpeechEngine(context, language, selected) }
    DisposableEffect(engine) { onDispose { engine.shutdown() } }
    val ready by engine.ready.collectAsState()
    val voices = if (ready) VoiceChooser.ranked(engine.voices, language, MAX_VOICES) else emptyList()
    val current = selected ?: engine.voiceName
    val scroll = rememberScrollState()

    BoxWithConstraints(modifier.fillMaxSize().background(palette.canvas)) {
        val scale = maxWidth.value / 227f
        fun dial(v: Float): Dp = (v * scale).dp
        val touch = minOf(wgoTouchSide(44.dp), dial(PITCH))
        val rows = voices.size + 1
        Box(
            Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .height(dial(60f + rows * PITCH + 100f))
        ) {
            Text(
                text = string(StringKey.SET_VOICE).uppercase(),
                color = palette.ink,
                style = WgoType.labelMd,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = dial(18f))
            )
            if (ready && voices.isEmpty()) {
                Text(
                    text = string(StringKey.VOICE_NONE),
                    color = palette.inkMuted,
                    style = WgoType.bodyMd,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.TopCenter).offset(y = dial(70f)).width(dial(160f))
                )
            }
            val counts = HashMap<String, Int>()
            voices.forEachIndexed { i, v ->
                val n = (counts[v.languageTag] ?: 0) + 1
                counts[v.languageTag] = n
                val label = VoiceLabels.label(v, n, language) + if (!v.network) " · " + string(StringKey.VOICE_OFFLINE) else ""
                val chosen = v.name == current
                PillButton(
                    label = label,
                    onClick = {
                        engine.useVoice(v.name)
                        onChoose(v.name)
                    },
                    background = if (chosen) palette.primary else palette.surface,
                    content = if (chosen) palette.onPrimary else palette.ink,
                    border = if (chosen) null else palette.ink,
                    width = 156.dp,
                    height = 44.dp,
                    textStyle = WgoType.bodyMd.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                    uppercase = false,
                    modifier = Modifier.align(Alignment.TopCenter).offset(y = dial(70f + i * PITCH) - (touch - 44.dp) / 2)
                )
            }
            PillButton(
                label = string(StringKey.BTN_TRY),
                onClick = { engine.speak(GuidancePhrases.sample(language)) },
                background = palette.ink,
                content = palette.inkInverse,
                width = 120.dp,
                height = 40.dp,
                textStyle = WgoType.button.copy(fontSize = 13.sp),
                modifier = Modifier.align(Alignment.TopCenter).offset(y = dial(70f + voices.size * PITCH) - (touch - 40.dp) / 2)
            )
        }
        PositionIndicator(scrollState = scroll)
    }
}

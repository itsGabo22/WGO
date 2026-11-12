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
import com.wgo.navigator.voice.VoiceCatalog
import com.wgo.navigator.voice.VoiceLabels

private const val PITCH = 57f
private const val HEADER = 20f

/**
 * The voice picker (spec section 7, "ScreenVoicePicker and voice settings, P8"): title "VOZ", then the distinct voices
 * of the UI language from every installed engine, grouped under an engine header, at most 6 pills (the chosen one
 * `primary`), and "PROBAR", which speaks a sample with the chosen voice, [pitch] and [rate] on any output. Every engine
 * is bound only while this screen is shown. Choosing persists the voice and its engine through [onChoose] (silent).
 */
@Composable
fun ScreenVoicePicker(
    language: Language,
    selected: String?,
    onChoose: (name: String?, engine: String?) -> Unit,
    modifier: Modifier = Modifier,
    pitch: Float = 1f,
    rate: Float = 1f
) {
    val palette = Wgo.palette
    val context = LocalContext.current
    // The system's default engine is bound first and the others only once it answered: bound together on the watch,
    // the cold Google engine (19-24 s) held the default one back for as long (P8, measured).
    val packages = remember { SpeechEngine.engines(context).map { it.first } }
    // Each engine starts on the saved voice when it has it (PROBAR on reopening tried Google's automatic voice instead).
    val first = remember(language, pitch, rate) { SpeechEngine(context, language, selected, null, pitch, rate) }
    val firstReady = first.ready.collectAsState().value
    val others = remember(language, pitch, rate, firstReady) {
        if (!firstReady) emptyList()
        else packages.filter { it != first.engine }.map { SpeechEngine(context, language, selected, it, pitch, rate) }
    }
    DisposableEffect(first) { onDispose { first.shutdown() } }
    DisposableEffect(others) { onDispose { others.forEach { it.shutdown() } } }
    val engines = listOf(first) + others
    val ready = engines.map { it.ready.collectAsState().value }
    val allReady = ready.all { it } && engines.size == packages.size.coerceAtLeast(1)
    // Each engine's voices appear as soon as it answers.
    val entries = VoiceCatalog.entries(engines.filterIndexed { i, _ -> ready[i] }.flatMap { it.voices }, language)
    // Without a choice, the system default engine's automatic voice is the current one.
    val current = selected ?: engines.firstOrNull { it.engine == it.systemDefaultEngine }?.voiceName
    val female = string(StringKey.VOICE_FEMALE)
    val male = string(StringKey.VOICE_MALE)
    val scroll = rememberScrollState()

    BoxWithConstraints(modifier.fillMaxSize().background(palette.canvas)) {
        val scale = maxWidth.value / 227f
        fun dial(v: Float): Dp = (v * scale).dp
        val touch = minOf(wgoTouchSide(44.dp), dial(PITCH))
        val groups = entries.groupBy { it.voice.engine }
        val height = 60f + groups.size * HEADER + entries.size * PITCH + 100f
        Box(
            Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .height(dial(height))
        ) {
            Text(
                text = string(StringKey.SET_VOICE).uppercase(),
                color = palette.ink,
                style = WgoType.labelMd,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = dial(18f))
            )
            if (allReady && entries.isEmpty()) {
                Text(
                    text = string(StringKey.VOICE_NONE),
                    color = palette.inkMuted,
                    style = WgoType.bodyMd,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.TopCenter).offset(y = dial(70f)).width(dial(160f))
                )
            }
            var y = 52f
            for ((engine, rows) in groups) {
                Text(
                    text = VoiceCatalog.engineName(engine).uppercase(),
                    color = palette.inkMuted,
                    style = WgoType.labelSm,
                    modifier = Modifier.align(Alignment.TopCenter).offset(y = dial(y))
                )
                y += HEADER
                for (entry in rows) {
                    val v = entry.voice
                    val chosen = v.name == current
                    PillButton(
                        label = VoiceLabels.entryLabel(entry, language, female, male),
                        onClick = {
                            engines.firstOrNull { it.engine == v.engine }?.useVoice(v.name)
                            onChoose(v.name, v.engine)
                        },
                        background = if (chosen) palette.primary else palette.surface,
                        content = if (chosen) palette.onPrimary else palette.ink,
                        border = if (chosen) null else palette.ink,
                        width = 156.dp,
                        height = 44.dp,
                        textStyle = WgoType.bodyMd.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        uppercase = false,
                        modifier = Modifier.align(Alignment.TopCenter).offset(y = dial(y + 8f) - (touch - 44.dp) / 2)
                    )
                    y += PITCH
                }
            }
            val sampleEngine = entries.firstOrNull { it.voice.name == current }?.voice?.engine
            PillButton(
                label = string(StringKey.BTN_TRY),
                onClick = {
                    val e = engines.firstOrNull { it.engine == sampleEngine } ?: engines.firstOrNull()
                    e?.speak(GuidancePhrases.sample(language))
                },
                background = palette.ink,
                content = palette.inkInverse,
                width = 120.dp,
                height = 40.dp,
                textStyle = WgoType.button.copy(fontSize = 13.sp),
                modifier = Modifier.align(Alignment.TopCenter).offset(y = dial(y + 18f) - (touch - 40.dp) / 2)
            )
        }
        PositionIndicator(scrollState = scroll)
    }
}

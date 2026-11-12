package com.wgo.navigator.ui.screens

import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.wgo.navigator.Language
import com.wgo.navigator.SettingsViewModel
import com.wgo.navigator.MapOrientation
import com.wgo.navigator.VoiceMode
import com.wgo.navigator.ui.StringKey
import com.wgo.navigator.ui.components.MapRow
import com.wgo.navigator.ui.components.SegmentedToggle
import com.wgo.navigator.ui.components.SettingRow
import com.wgo.navigator.ui.string
import com.wgo.navigator.ui.theme.Wgo
import com.wgo.navigator.ui.theme.WgoType
import com.wgo.navigator.ui.theme.wgoTouchSide
import com.wgo.navigator.voice.VoicePitch
import com.wgo.navigator.voice.VoiceRate
import com.wgo.navigator.voice.SpeechEngine
import com.wgo.navigator.voice.VoiceCatalog
import com.wgo.navigator.voice.VoiceSettingsRules
import com.wgo.navigator.voice.VoiceLabels

private const val REFERENCE_SIZE = 227f

/** Centre-to-centre pitch of the stacked controls: 48 real dp hit boxes (56.7 dp at s = 0.846) never overlap. */
private const val ROW_PITCH = 57f

/** Centres of the P7 rows (MapRow is centred at 174): voice guidance, then voice. */
/** P8: the orientation row right after MAPA; every row below moved down one pitch. */
internal const val ORIENTATION_ROW_CENTER = 231f
internal const val VOICE_MODE_CENTER = 288f
internal const val VOICE_ROW_CENTER = 345f
internal const val PITCH_ROW_CENTER = 402f
internal const val RATE_ROW_CENTER = 459f

/** Scrolled content height: the last row can reach the middle of the dial. */

/**
 * Settings (spec section 7, P7 update): a vertically scrolling list (touch scroll, position indicator while
 * scrolling), no close button (leave with Back / swipe from the left edge). Positions are the spec's `top` values on
 * the 227 dp canvas: title @18, theme toggle @40, language toggle @97, map row @152, then the voice guidance row
 * (centre 231, a tap cycles OFF / AUTO / ALWAYS) and the voice row (centre 288, opens the voice picker), all 57 dp apart
 * so every hit box is 48 real dp down to s = 0.846 (below that, capped at the pitch). Values apply live and persist
 * through [viewModel].
 */
@Composable
fun ScreenSettings(
    viewModel: SettingsViewModel,
    onOpenMapPicker: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenVoicePicker: () -> Unit = {},
    onOpenCredits: () -> Unit = {}
) {
    val palette = Wgo.palette
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val language by viewModel.language.collectAsState()
    val mapStyle by viewModel.mapStyle.collectAsState()
    val voiceMode by viewModel.voiceMode.collectAsState()
    val voiceName by viewModel.voiceName.collectAsState()
    val voicePitch by viewModel.voicePitch.collectAsState()
    val mapOrientation by viewModel.mapOrientation.collectAsState()
    val voiceRate by viewModel.voiceRate.collectAsState()
    val context = LocalContext.current
    // Is any text-to-speech engine installed? (A package query, not an engine start.)
    val hasEngine = remember {
        context.packageManager.queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0).isNotEmpty()
    }
    // P8: the installed engines (packages, from the package manager) and the chosen one (else the system default).
    val ttsEngine by viewModel.ttsEngine.collectAsState()
    val engines = remember { SpeechEngine.engines(context) }
    val currentEngine = ttsEngine ?: VoiceSettingsRules.defaultEngine(
        android.provider.Settings.Secure.getString(context.contentResolver, "tts_default_synth"), engines.map { it.first }
    )
    // "Más voces": the chosen engine's own voice-download screen, only when it resolves on this watch.
    val moreVoices = remember(currentEngine) {
        currentEngine?.let { pkg ->
            Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).setPackage(pkg)
                .takeIf { it.resolveActivity(context.packageManager) != null }
        }
    }
    // Rows below the voice row: pitch and speed, the engine row, more voices, credits.
    val rowsAfterVoice = (if (hasEngine) 2 else 0) + (if (engines.size > 1) 1 else 0) + (if (moreVoices != null) 1 else 0) + 1
    val scroll = rememberScrollState()

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(palette.canvas)) {
        val scale = maxWidth.value / REFERENCE_SIZE
        fun dial(value: Float): Dp = (value * scale).dp
        val pitch = dial(ROW_PITCH)

        Box(
            Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .height(dial(VOICE_ROW_CENTER + rowsAfterVoice * ROW_PITCH + 122f))
        ) {
            Text(
                text = string(StringKey.SETTINGS_HEADER).uppercase(),
                color = palette.ink,
                style = WgoType.labelMd,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = dial(18f))
            )

            // Hit boxes grow to 48 real dp around the 40 / 44 dp visuals (capped at the pitch); visuals stay on position.
            val toggleTouch = minOf(wgoTouchSide(40.dp), pitch)
            val toggleExtra = (toggleTouch - 40.dp) / 2
            SegmentedToggle(
                options = listOf(string(StringKey.LIGHT), string(StringKey.DARK)),
                selectedIndex = if (isDarkMode) 1 else 0,
                onSelect = { index -> if ((index == 1) != isDarkMode) viewModel.toggleTheme() },
                modifier = Modifier.align(Alignment.TopCenter).offset(y = dial(40f) - toggleExtra),
                maxTouchHeight = pitch
            )
            SegmentedToggle(
                options = listOf(string(StringKey.LANG_NAME_ES), string(StringKey.LANG_NAME_EN)),
                selectedIndex = if (language == Language.ES) 0 else 1,
                onSelect = { index -> if ((index == 0) != (language == Language.ES)) viewModel.toggleLanguage() },
                modifier = Modifier.align(Alignment.TopCenter).offset(y = dial(97f) - toggleExtra),
                maxTouchHeight = pitch
            )

            val rowTouch = minOf(wgoTouchSide(44.dp), pitch)
            val rowExtra = (rowTouch - 44.dp) / 2
            MapRow(
                styleId = mapStyle,
                onClick = onOpenMapPicker,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = dial(152f) - rowExtra),
                maxTouchHeight = pitch
            )

            // P8: map orientation (north up / heading up).
            SettingRow(
                label = string(StringKey.SET_MAP_ORIENTATION),
                value = string(if (mapOrientation == MapOrientation.NorthUp) StringKey.ORIENT_NORTH else StringKey.ORIENT_HEADING),
                onClick = viewModel::toggleMapOrientation,
                state = true,
                maxTouchHeight = pitch,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = dial(ORIENTATION_ROW_CENTER - 22f) - rowExtra)
            )

            // P7: spoken guidance.
            SettingRow(
                label = string(StringKey.SET_VOICE_GUIDANCE),
                value = string(
                    when (voiceMode) {
                        VoiceMode.Off -> StringKey.VOICE_MODE_OFF
                        VoiceMode.Auto -> StringKey.VOICE_MODE_AUTO
                        VoiceMode.Always -> StringKey.VOICE_MODE_ALWAYS
                    }
                ),
                onClick = viewModel::cycleVoiceMode,
                state = true,
                maxTouchHeight = pitch,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = dial(VOICE_MODE_CENTER - 22f) - rowExtra)
            )
            SettingRow(
                label = string(StringKey.SET_VOICE),
                value = when {
                    !hasEngine -> string(StringKey.VOICE_NONE)
                    voiceName == null -> string(StringKey.VOICE_AUTOMATIC)
                    else -> VoiceLabels.shortName(voiceName.orEmpty(), language)
                },
                onClick = onOpenVoicePicker,
                opens = true,
                maxTouchHeight = pitch,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = dial(VOICE_ROW_CENTER - 22f) - rowExtra)
            )
            // P8: pitch and speed of every phrase (spec section 7, voice settings).
            if (hasEngine) {
                SettingRow(
                    label = string(StringKey.SET_VOICE_PITCH),
                    value = string(
                        when (voicePitch) {
                            VoicePitch.Low -> StringKey.PITCH_LOW
                            VoicePitch.Normal -> StringKey.PITCH_NORMAL
                            VoicePitch.High -> StringKey.PITCH_HIGH
                        }
                    ),
                    onClick = viewModel::cycleVoicePitch,
                    state = true,
                    maxTouchHeight = pitch,
                    modifier = Modifier.align(Alignment.TopCenter).offset(y = dial(PITCH_ROW_CENTER - 22f) - rowExtra)
                )
                SettingRow(
                    label = string(StringKey.SET_VOICE_RATE),
                    value = string(
                        when (voiceRate) {
                            VoiceRate.Slow -> StringKey.RATE_SLOW
                            VoiceRate.Normal -> StringKey.RATE_NORMAL
                            VoiceRate.Fast -> StringKey.RATE_FAST
                        }
                    ),
                    onClick = viewModel::cycleVoiceRate,
                    state = true,
                    maxTouchHeight = pitch,
                    modifier = Modifier.align(Alignment.TopCenter).offset(y = dial(RATE_ROW_CENTER - 22f) - rowExtra)
                )
            }
            var nextCenter = if (hasEngine) RATE_ROW_CENTER + ROW_PITCH else VOICE_ROW_CENTER + ROW_PITCH
            // P8: the engine row only with more than one engine; a tap moves to the next one (voice back to automatic).
            if (engines.size > 1) {
                val names = engines.map { (pkg, label) -> pkg to VoiceCatalog.engineName(pkg, label) }
                SettingRow(
                    label = string(StringKey.SET_TTS_ENGINE),
                    value = names.firstOrNull { it.first == currentEngine }?.second ?: names.first().second,
                    onClick = { viewModel.setVoice(null, VoiceSettingsRules.nextEngine(currentEngine, engines.map { it.first })) },
                    state = true,
                    maxTouchHeight = pitch,
                    modifier = Modifier.align(Alignment.TopCenter).offset(y = dial(nextCenter - 22f) - rowExtra)
                )
                nextCenter += ROW_PITCH
            }
            if (moreVoices != null) {
                SettingRow(
                    label = string(StringKey.SET_MORE_VOICES),
                    value = string(StringKey.MORE_VOICES_VALUE),
                    onClick = { runCatching { context.startActivity(moreVoices) } },
                    opens = true,
                    maxTouchHeight = pitch,
                    modifier = Modifier.align(Alignment.TopCenter).offset(y = dial(nextCenter - 22f) - rowExtra)
                )
                nextCenter += ROW_PITCH
            }
            val creditsCenter = nextCenter
            SettingRow(
                label = string(StringKey.SET_CREDITS),
                value = "OpenStreetMap · CARTO · Overture",
                onClick = onOpenCredits,
                opens = true,
                maxTouchHeight = pitch,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = dial(creditsCenter - 22f) - rowExtra)
            )
        }
        PositionIndicator(scrollState = scroll)
    }
}


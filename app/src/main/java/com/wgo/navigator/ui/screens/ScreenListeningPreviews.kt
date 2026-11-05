package com.wgo.navigator.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import com.wgo.navigator.Language
import com.wgo.navigator.ui.LocalLanguage
import com.wgo.navigator.ui.components.MicState
import com.wgo.navigator.ui.theme.WgoTheme

@Composable
private fun ListeningPreview(dark: Boolean, state: MicState, language: Language = Language.ES, transcript: String = "") {
    WgoTheme(isDarkMode = dark) {
        CompositionLocalProvider(LocalLanguage provides language) {
            ScreenListening(
                micState = state,
                onMicClick = {},
                onCancel = {},
                onOpenSettings = {},
                onToggleLanguage = {},
                transcript = transcript
            )
        }
    }
}

private const val LARGE = "id:wearos_large_round"
private const val SMALL = "id:wearos_small_round"

@Preview(name = "Idle ES Daylight", device = LARGE) @Composable
private fun ListeningIdleDaylight() = ListeningPreview(false, MicState.Idle)

@Preview(name = "Idle ES Night", device = LARGE) @Composable
private fun ListeningIdleNight() = ListeningPreview(true, MicState.Idle)

@Preview(name = "Idle EN Daylight", device = LARGE) @Composable
private fun ListeningIdleEnDaylight() = ListeningPreview(false, MicState.Idle, Language.EN)

@Preview(name = "Idle EN Night", device = LARGE) @Composable
private fun ListeningIdleEnNight() = ListeningPreview(true, MicState.Idle, Language.EN)

@Preview(name = "Listening Daylight", device = LARGE) @Composable
private fun ListeningListeningDaylight() = ListeningPreview(false, MicState.Listening, transcript = "Centro Histórico")

@Preview(name = "Listening Night", device = LARGE) @Composable
private fun ListeningListeningNight() = ListeningPreview(true, MicState.Listening, transcript = "Centro Histórico")

@Preview(name = "Processing Daylight", device = LARGE) @Composable
private fun ListeningProcessingDaylight() = ListeningPreview(false, MicState.Processing)

@Preview(name = "Processing Night", device = LARGE) @Composable
private fun ListeningProcessingNight() = ListeningPreview(true, MicState.Processing)

@Preview(name = "Listening small Daylight", device = SMALL) @Composable
private fun ListeningSmallDaylight() = ListeningPreview(false, MicState.Listening, transcript = "Centro Histórico")

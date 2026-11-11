package com.wgo.navigator.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.wgo.navigator.Language
import com.wgo.navigator.ui.LocalLanguage
import com.wgo.navigator.ui.components.MicState
import com.wgo.navigator.ui.components.WgoWatchPreviews
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


@WgoWatchPreviews @Composable
private fun ListeningIdleDaylight() = ListeningPreview(false, MicState.Idle)

@WgoWatchPreviews @Composable
private fun ListeningIdleNight() = ListeningPreview(true, MicState.Idle)

@WgoWatchPreviews @Composable
private fun ListeningIdleEnDaylight() = ListeningPreview(false, MicState.Idle, Language.EN)

@WgoWatchPreviews @Composable
private fun ListeningIdleEnNight() = ListeningPreview(true, MicState.Idle, Language.EN)

@WgoWatchPreviews @Composable
private fun ListeningListeningDaylight() = ListeningPreview(false, MicState.Listening, transcript = "Centro Histórico")

@WgoWatchPreviews @Composable
private fun ListeningListeningNight() = ListeningPreview(true, MicState.Listening, transcript = "Centro Histórico")

@WgoWatchPreviews @Composable
private fun ListeningPreparingDaylight() = ListeningPreview(false, MicState.Preparing)

@WgoWatchPreviews @Composable
private fun ListeningPreparingEnNight() = ListeningPreview(true, MicState.Preparing, Language.EN)

@WgoWatchPreviews @Composable
private fun ListeningProcessingDaylight() = ListeningPreview(false, MicState.Processing)

@WgoWatchPreviews @Composable
private fun ListeningProcessingNight() = ListeningPreview(true, MicState.Processing)


package com.wgo.navigator.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.wgo.navigator.Language
import com.wgo.navigator.TravelMode
import com.wgo.navigator.ui.LocalLanguage
import com.wgo.navigator.ui.components.WgoWatchPreviews
import com.wgo.navigator.ui.theme.WgoTheme

@Composable
private fun ModePreview(dark: Boolean, pressed: TravelMode? = null, language: Language = Language.ES) {
    WgoTheme(isDarkMode = dark) {
        CompositionLocalProvider(LocalLanguage provides language) {
            ScreenModeSelection(onSelectMode = {}, pressedMode = pressed)
        }
    }
}


@WgoWatchPreviews @Composable
private fun ModeNormalDaylight() = ModePreview(false)

@WgoWatchPreviews @Composable
private fun ModeNormalNight() = ModePreview(true)

@WgoWatchPreviews @Composable
private fun ModePressedWalkDaylight() = ModePreview(false, TravelMode.Walk)

@WgoWatchPreviews @Composable
private fun ModePressedWalkNight() = ModePreview(true, TravelMode.Walk)

@WgoWatchPreviews @Composable
private fun ModePressedDriveDaylight() = ModePreview(false, TravelMode.Drive)

@WgoWatchPreviews @Composable
private fun ModePressedDriveNight() = ModePreview(true, TravelMode.Drive)

@WgoWatchPreviews @Composable
private fun ModeEnglishDaylight() = ModePreview(false, language = Language.EN)


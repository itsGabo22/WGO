package com.wgo.navigator.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.tooling.preview.Preview
import com.wgo.navigator.Language
import com.wgo.navigator.TravelMode
import com.wgo.navigator.ui.LocalLanguage
import com.wgo.navigator.ui.theme.WgoTheme

@Composable
private fun ModePreview(dark: Boolean, pressed: TravelMode? = null, language: Language = Language.ES) {
    WgoTheme(isDarkMode = dark) {
        CompositionLocalProvider(LocalLanguage provides language) {
            ScreenModeSelection(onSelectMode = {}, pressedMode = pressed)
        }
    }
}

private const val LARGE = "id:wearos_large_round"
private const val SMALL = "id:wearos_small_round"

@Preview(name = "Normal Daylight", device = LARGE) @Composable
private fun ModeNormalDaylight() = ModePreview(false)

@Preview(name = "Normal Night", device = LARGE) @Composable
private fun ModeNormalNight() = ModePreview(true)

@Preview(name = "Pressed walk Daylight", device = LARGE) @Composable
private fun ModePressedWalkDaylight() = ModePreview(false, TravelMode.Walk)

@Preview(name = "Pressed walk Night", device = LARGE) @Composable
private fun ModePressedWalkNight() = ModePreview(true, TravelMode.Walk)

@Preview(name = "Pressed drive Daylight", device = LARGE) @Composable
private fun ModePressedDriveDaylight() = ModePreview(false, TravelMode.Drive)

@Preview(name = "Pressed drive Night", device = LARGE) @Composable
private fun ModePressedDriveNight() = ModePreview(true, TravelMode.Drive)

@Preview(name = "English Daylight", device = LARGE) @Composable
private fun ModeEnglishDaylight() = ModePreview(false, language = Language.EN)

@Preview(name = "Small Night", device = SMALL) @Composable
private fun ModeSmallNight() = ModePreview(true)

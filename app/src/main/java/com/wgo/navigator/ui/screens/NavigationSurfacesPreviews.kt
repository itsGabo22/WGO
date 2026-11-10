package com.wgo.navigator.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.wgo.navigator.Language
import com.wgo.navigator.ui.LocalLanguage
import com.wgo.navigator.ui.components.WgoWatchPreviews
import com.wgo.navigator.ui.theme.WgoTheme

private const val SampleDestination = "Universidad de Nariño"

@Composable
private fun Frame(dark: Boolean, language: Language, content: @Composable () -> Unit) {
    WgoTheme(isDarkMode = dark) { CompositionLocalProvider(LocalLanguage provides language, content = content) }
}

@WgoWatchPreviews @Composable private fun GpsSearchingDaylight() = Frame(false, Language.ES) { ScreenGpsSearching(onCancel = {}) }
@WgoWatchPreviews @Composable private fun GpsSearchingEnNight() = Frame(true, Language.EN) { ScreenGpsSearching(onCancel = {}) }

@WgoWatchPreviews @Composable private fun GpsLostDaylight() = Frame(false, Language.ES) { GpsLostOverlay(onRetry = {}) }
@WgoWatchPreviews @Composable private fun GpsLostEnNight() = Frame(true, Language.EN) { GpsLostOverlay(onRetry = {}) }

@WgoWatchPreviews @Composable private fun LostRouteDaylight() = Frame(false, Language.ES) { ScreenLostRoute(onRetry = {}, onEnd = {}) }
@WgoWatchPreviews @Composable private fun LostRouteEnNight() = Frame(true, Language.EN) { ScreenLostRoute(onRetry = {}, onEnd = {}) }

@WgoWatchPreviews @Composable private fun EndNavigationNight() =
    Frame(true, Language.ES) { ScreenEndNavigation(SampleDestination, onEnd = {}, onKeep = {}) }
@WgoWatchPreviews @Composable private fun EndNavigationEnDaylight() =
    Frame(false, Language.EN) { ScreenEndNavigation(SampleDestination, onEnd = {}, onKeep = {}) }

@WgoWatchPreviews @Composable private fun ArrivedDaylight() = Frame(false, Language.ES) { ScreenArrived(SampleDestination, onFinish = {}) }
@WgoWatchPreviews @Composable private fun ArrivedEnNight() = Frame(true, Language.EN) { ScreenArrived(SampleDestination, onFinish = {}) }

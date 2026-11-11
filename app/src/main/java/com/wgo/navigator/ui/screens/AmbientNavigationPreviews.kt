package com.wgo.navigator.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.wgo.navigator.Language
import com.wgo.navigator.core.Maneuver
import com.wgo.navigator.core.RouteStep
import com.wgo.navigator.nav.NavState
import com.wgo.navigator.ui.LocalLanguage
import com.wgo.navigator.ui.components.WgoWatchPreviews
import com.wgo.navigator.ui.map.GeoCoord
import com.wgo.navigator.ui.theme.WgoTheme

private val SampleNav = NavState.Navigating(
    progressM = 420.0, remainingM = 815.0, etaS = 610.0,
    next = RouteStep(Maneuver.LEFT, "Carrera 22", GeoCoord(1.2310, -77.2940), 0.0),
    distanceToNextM = 180.0, then = null, distanceFromRouteM = 2.0, snapped = GeoCoord(1.2300, -77.2942)
)

@Composable
private fun Frame(dark: Boolean, language: Language, content: @Composable () -> Unit) {
    WgoTheme(isDarkMode = dark) { CompositionLocalProvider(LocalLanguage provides language, content = content) }
}

// Ambient is always black in both themes; both are previewed to show the theme does not leak into it.
@WgoWatchPreviews @Composable private fun AmbientTurnEsDaylight() = Frame(false, Language.ES) { AmbientNavigationScreen(SampleNav) }
@WgoWatchPreviews @Composable private fun AmbientTurnEnNight() = Frame(true, Language.EN) { AmbientNavigationScreen(SampleNav) }
@WgoWatchPreviews @Composable private fun AmbientGpsLostEs() = Frame(true, Language.ES) { AmbientNavigationScreen(NavState.GpsLost(SampleNav)) }
@WgoWatchPreviews @Composable private fun AmbientLowBitEn() =
    Frame(true, Language.EN) { AmbientNavigationScreen(SampleNav, AmbientInfo(lowBit = true, burnInProtection = true)) }

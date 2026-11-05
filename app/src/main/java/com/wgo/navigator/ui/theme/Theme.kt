package com.wgo.navigator.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

/** Wear Material 2 colors derived from the WGO palette (canvas, ink, primary, alert). */
private fun WgoPalette.toWearColors() = Colors(
    primary = primary,
    primaryVariant = primary,
    secondary = primary,
    secondaryVariant = primary,
    error = alert,
    onPrimary = onPrimary,
    onSecondary = onPrimary,
    onError = onAlert,
    background = canvas,
    onBackground = ink,
    surface = surface,
    onSurface = ink,
    onSurfaceVariant = ink
)

@Composable
fun WgoTheme(
    isDarkMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val palette = if (isDarkMode) NightPalette else DaylightPalette

    CompositionLocalProvider(
        LocalWgoPalette provides palette,
        LocalWgoReduceMotion provides rememberWgoReduceMotion()
    ) {
        MaterialTheme(
            colors = palette.toWearColors(),
            typography = WgoTypography,
            content = content
        )
    }
}

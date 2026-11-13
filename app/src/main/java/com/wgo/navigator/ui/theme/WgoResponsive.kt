package com.wgo.navigator.ui.theme

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlin.math.roundToInt

private val LocalResponsiveApplied = compositionLocalOf { false }

/**
 * Scales the whole tree uniformly to the 227 dp reference canvas by overriding [LocalDensity]:
 * `density = base * s`, `fontScale = base * textS / s` (so text ends up scaled by the clamped `textS` and the user's
 * system font scale is preserved). [LocalConfiguration] is overridden with the matching dp sizes so components that read
 * the screen size (ScalingLazyColumn, Scaffold) stay consistent. Applying it twice is a no-op.
 */
@Composable
fun WgoResponsive(content: @Composable () -> Unit) {
    if (LocalResponsiveApplied.current) {
        content()
        return
    }
    val base = LocalDensity.current
    val configuration = LocalConfiguration.current
    val s = wgoScaleFor(minOf(configuration.screenWidthDp, configuration.screenHeightDp).toFloat())
    val textScale = wgoTextScaleFor(s)

    val density = remember(base, s) { Density(density = base.density * s, fontScale = base.fontScale * textScale / s) }
    val scaledConfiguration = remember(configuration, s) {
        Configuration(configuration).apply {
            screenWidthDp = (configuration.screenWidthDp / s).roundToInt()
            screenHeightDp = (configuration.screenHeightDp / s).roundToInt()
            smallestScreenWidthDp = (configuration.smallestScreenWidthDp / s).roundToInt()
            densityDpi = (configuration.densityDpi * s).roundToInt()
        }
    }

    CompositionLocalProvider(
        LocalResponsiveApplied provides true,
        LocalDensity provides density,
        LocalConfiguration provides scaledConfiguration,
        LocalWgoScale provides s,
        content = content
    )
}

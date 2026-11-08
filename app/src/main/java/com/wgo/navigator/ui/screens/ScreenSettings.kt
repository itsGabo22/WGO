package com.wgo.navigator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Text
import com.wgo.navigator.Language
import com.wgo.navigator.SettingsViewModel
import com.wgo.navigator.ui.StringKey
import com.wgo.navigator.ui.components.MapRow
import com.wgo.navigator.ui.components.SegmentedToggle
import com.wgo.navigator.ui.string
import com.wgo.navigator.ui.theme.Wgo
import com.wgo.navigator.ui.theme.WgoType
import com.wgo.navigator.ui.theme.wgoTouchSide

private const val REFERENCE_SIZE = 227f

/** Centre-to-centre pitch of the three stacked controls: 48 real dp hit boxes (56.7 dp at s = 0.846) never overlap. */
private const val ROW_PITCH = 57f

/**
 * Settings, no scroll and no close button (leave with Back / swipe from the left edge). Positions are the spec's `top`
 * values on the 227 dp canvas: title @18, theme toggle @40, language toggle @97, map row @152 (centres 57 dp apart).
 * Every hit box is 48 real dp down to s = 0.846; below that (the 0.8 clamp) they are capped at the 57 dp pitch so
 * they still never overlap. Theme and language apply live and persist through [viewModel]; the map row opens the picker.
 */
@Composable
fun ScreenSettings(
    viewModel: SettingsViewModel,
    onOpenMapPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = Wgo.palette
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val language by viewModel.language.collectAsState()
    val mapStyle by viewModel.mapStyle.collectAsState()

    BoxWithConstraints(modifier = modifier.fillMaxSize().background(palette.canvas)) {
        val scale = maxWidth.value / REFERENCE_SIZE
        fun dial(value: Float): Dp = (value * scale).dp

        Text(
            text = string(StringKey.SETTINGS_HEADER).uppercase(),
            color = palette.ink,
            style = WgoType.labelMd,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = dial(18f))
        )

        // Hit boxes grow to 48 real dp around the 40 / 44 dp visuals (capped at the pitch); visuals stay on position.
        val pitch = dial(ROW_PITCH)
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
    }
}

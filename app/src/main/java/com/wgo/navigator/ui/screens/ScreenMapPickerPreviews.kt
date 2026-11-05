package com.wgo.navigator.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import com.wgo.navigator.ui.components.WgoWatchPreviews
import com.wgo.navigator.ui.map.MapStyleIdProvider
import com.wgo.navigator.ui.theme.MapStyleId
import com.wgo.navigator.ui.theme.WgoTheme

@Composable
private fun PickerPreview(dark: Boolean, id: MapStyleId) {
    WgoTheme(isDarkMode = dark) {
        ScreenMapPicker(currentStyle = id, onUse = {}, onStep = {})
    }
}

@WgoWatchPreviews
@Composable
private fun MapPickerDaylight(@PreviewParameter(MapStyleIdProvider::class) id: MapStyleId) = PickerPreview(false, id)

@WgoWatchPreviews
@Composable
private fun MapPickerNight(@PreviewParameter(MapStyleIdProvider::class) id: MapStyleId) = PickerPreview(true, id)

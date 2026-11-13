package com.wgo.navigator.ui.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.wgo.navigator.ui.components.WgoPreviewFrame
import com.wgo.navigator.ui.components.WgoWatchPreviews
import com.wgo.navigator.ui.theme.MapStyleId
import com.wgo.navigator.ui.theme.Wgo
import com.wgo.navigator.ui.theme.wgoMapStyle

class MapStyleIdProvider : PreviewParameterProvider<MapStyleId> {
    override val values: Sequence<MapStyleId> = MapStyleId.entries.asSequence()
}

@Composable
private fun SchematicPreview(dark: Boolean, id: MapStyleId) {
    WgoPreviewFrame(dark) {
        RadarSchematic(
            style = wgoMapStyle(id, Wgo.palette),
            live = true,
            crop = FullSchematicCrop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@WgoWatchPreviews
@Composable
private fun SchematicDaylight(@PreviewParameter(MapStyleIdProvider::class) id: MapStyleId) = SchematicPreview(false, id)

@WgoWatchPreviews
@Composable
private fun SchematicNight(@PreviewParameter(MapStyleIdProvider::class) id: MapStyleId) = SchematicPreview(true, id)

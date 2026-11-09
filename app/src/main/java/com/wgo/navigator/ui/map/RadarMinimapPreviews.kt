package com.wgo.navigator.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wgo.navigator.ui.components.WgoWatchPreviews
import com.wgo.navigator.ui.theme.MapStyleId
import com.wgo.navigator.ui.theme.Wgo
import com.wgo.navigator.ui.theme.WgoTheme
import com.wgo.navigator.ui.theme.rememberWgoClock
import com.wgo.navigator.ui.theme.wgoMapStyle

/** Previews use a flat `map-ground` fill instead of the real tiles, so overlays can be reviewed without network. */
@Composable
private fun RadarPreview(dark: Boolean, styleId: MapStyleId = MapStyleId.COBALTO, showNorth: Boolean = false) {
    WgoTheme(isDarkMode = dark) {
        val style = wgoMapStyle(styleId, Wgo.palette)
        // 70 m into the demo route, heading east just before the first turn.
        val radar = DemoRadarSource.stateAt(70.0)
        RadarMinimap(
            style = style,
            userLocation = radar.location,
            bearing = radar.bearing,
            route = radar.route,
            blips = radar.blips,
            maneuver = radar.maneuver,
            clock = rememberWgoClock(),
            showNorth = showNorth,
            tileLayer = { modifier -> Box(modifier.fillMaxSize().background(style.palette.ground)) }
        )
    }
}

@WgoWatchPreviews @Composable
private fun RadarDaylight() = RadarPreview(false, showNorth = true)

@WgoWatchPreviews @Composable
private fun RadarNight() = RadarPreview(true, showNorth = true)

@WgoWatchPreviews @Composable
private fun RadarNeonNight() = RadarPreview(true, MapStyleId.NEON)

@WgoWatchPreviews @Composable
private fun RadarPergaminoDaylight() = RadarPreview(false, MapStyleId.PERGAMINO)

package com.wgo.navigator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.wgo.navigator.ui.StringKey
import com.wgo.navigator.ui.string
import com.wgo.navigator.ui.theme.WgoBlack
import com.wgo.navigator.ui.theme.WgoPrimary
import com.wgo.navigator.ui.map.RadarMinimap
import com.wgo.navigator.ui.map.DemoRadarSource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.wgo.navigator.TravelMode
import com.wgo.navigator.core.Fix
import com.wgo.navigator.core.LocationSource
import com.wgo.navigator.core.permissions.PermissionStatus
import com.wgo.navigator.core.permissions.WgoPermission
import com.wgo.navigator.ui.components.PermissionDenied
import com.wgo.navigator.ui.map.GeoCoord
import com.wgo.navigator.ui.map.LiveRadarSource
import com.wgo.navigator.ui.map.RadarSource
import com.wgo.navigator.ui.theme.MapStyleId
import com.wgo.navigator.ui.theme.Wgo
import com.wgo.navigator.ui.theme.rememberWgoClock
import com.wgo.navigator.ui.theme.wgoMapStyle

/**
 * Screen 03. With a [locationSource] the radar follows live fixes ([LiveRadarSource]: user only, no blips, no route
 * until routing exists), collected only while the app is STARTED so updates stop in the background and with the
 * screen off. Without one (previews) it shows [DemoRadarSource]. A refused location permission replaces the screen
 * with the [PermissionDenied] surface (retry, or the app settings after "don't ask again").
 */
@Composable
fun ScreenActiveNavigation(
    onCancel: () -> Unit,
    mapStyleId: MapStyleId = MapStyleId.COBALTO,
    locationSource: LocationSource? = null,
    travelMode: TravelMode? = null,
    origin: Fix? = null,
    locationPermission: PermissionStatus = PermissionStatus.Granted,
    onRetryLocation: () -> Unit = {},
    onOpenLocationSettings: () -> Unit = {}
) {
    if (locationSource != null &&
        (locationPermission == PermissionStatus.Denied || locationPermission == PermissionStatus.PermanentlyDenied)
    ) {
        PermissionDenied(
            permission = WgoPermission.LOCATION,
            status = locationPermission,
            onRetry = onRetryLocation,
            onOpenSettings = onOpenLocationSettings
        )
        return
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
    ) {
        // Z-Layer 0: RADAR MINIMAP (osmdroid tiles + Compose overlays). One clock for every looping element.
        val clock = rememberWgoClock()
        val scope = rememberCoroutineScope()
        val radarSource: RadarSource = remember(locationSource, travelMode) {
            if (locationSource == null) DemoRadarSource(scope)
            else LiveRadarSource(locationSource, travelMode, origin?.let { GeoCoord(it.lat, it.lon) })
        }
        if (radarSource is LiveRadarSource) {
            LaunchedEffect(radarSource, origin) { origin?.let(radarSource::seed) }
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            val granted = locationPermission == PermissionStatus.Granted
            LaunchedEffect(radarSource, lifecycle, granted) {
                if (granted) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { radarSource.track() }
            }
        }
        val radar by radarSource.state.collectAsState()
        var zoom by remember { mutableDoubleStateOf(17.0) }

        RadarMinimap(
            style = wgoMapStyle(mapStyleId, Wgo.palette),
            userLocation = radar.location,
            bearing = radar.bearing,
            route = radar.route,
            blips = radar.blips,
            maneuver = radar.maneuver,
            zoom = zoom,
            clock = clock
        )

        // Z-Layer 2: TOP NAVIGATION HUD (Floating card)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp)
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colors.surface.copy(alpha = 0.90f))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowUpward,
                        contentDescription = string(StringKey.GO_STRAIGHT),
                        tint = WgoPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "200m",
                        color = MaterialTheme.colors.onSurface,
                        style = MaterialTheme.typography.title3.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Text(
                    text = string(StringKey.NAV_INSTRUCTION),
                    color = MaterialTheme.colors.onSurface,
                    style = MaterialTheme.typography.caption2.copy(fontWeight = FontWeight.Medium),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Z-Layer 2.5: ZOOM CONTROLS
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { zoom = (zoom + 1.0).coerceAtMost(18.0) },
                modifier = Modifier.size(36.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colors.onSurface
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = string(StringKey.ZOOM_IN),
                    modifier = Modifier.size(20.dp)
                )
            }

            Button(
                onClick = { zoom = (zoom - 1.0).coerceAtLeast(15.0) },
                modifier = Modifier.size(36.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colors.onSurface
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Remove,
                    contentDescription = string(StringKey.ZOOM_OUT),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Z-Layer 3: BOTTOM ACTION HUD
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        ) {
            Chip(
                onClick = onCancel,
                colors = ChipDefaults.chipColors(
                    backgroundColor = MaterialTheme.colors.surface,
                    contentColor = MaterialTheme.colors.onBackground
                ),
                label = {
                    Text(
                        text = string(StringKey.BTN_CANCEL),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(40.dp)
            )
        }
    }
}

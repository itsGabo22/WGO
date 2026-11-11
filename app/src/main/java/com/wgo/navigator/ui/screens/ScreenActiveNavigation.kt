package com.wgo.navigator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import com.wgo.navigator.ui.map.RadarState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
import com.wgo.navigator.core.Maneuver
import com.wgo.navigator.core.permissions.PermissionStatus
import com.wgo.navigator.core.permissions.WgoPermission
import com.wgo.navigator.nav.NavState
import com.wgo.navigator.route.ManeuverText
import com.wgo.navigator.ui.LocalLanguage
import com.wgo.navigator.ui.StringKey
import com.wgo.navigator.ui.components.PermissionDenied
import com.wgo.navigator.ui.components.WgoIcons
import com.wgo.navigator.ui.formatDistance
import com.wgo.navigator.ui.map.DemoRadarSource
import com.wgo.navigator.ui.map.RadarMinimap
import com.wgo.navigator.ui.map.RadarSource
import com.wgo.navigator.ui.minutesOf
import com.wgo.navigator.ui.string
import com.wgo.navigator.ui.theme.LocalWgoReduceMotion
import com.wgo.navigator.ui.theme.MapStyleId
import com.wgo.navigator.ui.theme.Wgo
import com.wgo.navigator.ui.theme.WgoType
import com.wgo.navigator.ui.theme.rememberWgoClock
import com.wgo.navigator.ui.theme.wgoMapStyle

private val STRAIGHT_LIKE = setOf(Maneuver.STRAIGHT, Maneuver.DEPART, Maneuver.ARRIVE)
private val LEFT_LIKE = setOf(Maneuver.SLIGHT_LEFT, Maneuver.LEFT, Maneuver.SHARP_LEFT, Maneuver.U_TURN)

/** The guidance a state still carries (the last one before going off route, rerouting or losing GPS). */
private fun NavState.guidance(): NavState.Navigating? = when (this) {
    is NavState.Navigating -> this
    is NavState.OffRoute -> last
    is NavState.Rerouting -> last
    is NavState.LostRoute -> last
    is NavState.GpsLost -> last
    else -> null
}

/**
 * Screen 03, driven by the navigation engine's [navState] (the layout is the pre-P6 one; its rebuild is P6).
 * - WaitingForGps: the GPS SEARCHING surface (CANCEL ends the trip).
 * - Navigating / OffRoute / Rerouting: radar with the remaining route ([radarSource]) under the HUD: next maneuver
 *   (icon, distance, instruction), remaining distance and ETA; off route and rerouting replace the instruction with a
 *   live-region status.
 * - GpsLost / LostRoute / [endConfirm]: their surface covers the radar, which stays composed (no osmdroid rebuild on
 *   KEEP) with its animations stopped (reduce motion forced under an opaque surface) and its data frozen (no state
 *   updates, so no recomposition below the surface until it goes away).
 * The CANCEL chip and Back ask END NAVIGATION? first. A refused location permission shows [PermissionDenied].
 * Without [navState] (previews) the radar runs on [DemoRadarSource].
 */
@Composable
fun ScreenActiveNavigation(
    onCancel: () -> Unit,
    mapStyleId: MapStyleId = MapStyleId.COBALTO,
    navState: NavState? = null,
    radarSource: RadarSource? = null,
    destination: String = "",
    endConfirm: Boolean = false,
    onEnd: () -> Unit = {},
    onKeep: () -> Unit = {},
    onRetryReroute: () -> Unit = {},
    onRetryGps: () -> Unit = {},
    locationPermission: PermissionStatus = PermissionStatus.Granted,
    onRetryLocation: () -> Unit = {},
    onOpenLocationSettings: () -> Unit = {}
) {
    if (navState != null &&
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
    if (navState == NavState.WaitingForGps) {
        if (endConfirm) ScreenEndNavigation(destination, onEnd = onEnd, onKeep = onKeep) else ScreenGpsSearching(onCancel = onEnd)
        return
    }
    val covered = endConfirm || navState is NavState.LostRoute || navState is NavState.GpsLost

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Wgo.palette.canvas)
    ) {
        // One clock for every looping element of the screen (radar pieces and the GPS-lost overlay).
        val clock = rememberWgoClock()
        val scope = rememberCoroutineScope()
        val source = radarSource ?: remember { DemoRadarSource(scope) }
        // Under a modal surface the radar is frozen: no subscription to its state, so new fixes recompose nothing
        // below the surface; the snapshot taken when it covered the radar is drawn until it goes away.
        val radar: RadarState = if (covered) {
            remember { source.state.value }
        } else {
            source.state.collectAsState().value
        }
        var zoom by remember { mutableDoubleStateOf(17.0) }

        // Z-Layer 0: RADAR MINIMAP. Under an opaque surface its loops stop (no frames for an invisible sweep).
        CompositionLocalProvider(LocalWgoReduceMotion provides (LocalWgoReduceMotion.current || covered)) {
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
        }

        if (!covered) {
            NavigationHud(navState, Modifier.align(Alignment.TopCenter))

            // Z-Layer 2.5: ZOOM CONTROLS (known pre-P6 debt: 36 dp buttons, under the 48 real dp minimum)
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
                    Icon(imageVector = Icons.Rounded.Add, contentDescription = string(StringKey.ZOOM_IN), modifier = Modifier.size(20.dp))
                }
                Button(
                    onClick = { zoom = (zoom - 1.0).coerceAtLeast(15.0) },
                    modifier = Modifier.size(36.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.8f),
                        contentColor = MaterialTheme.colors.onSurface
                    )
                ) {
                    Icon(imageVector = Icons.Rounded.Remove, contentDescription = string(StringKey.ZOOM_OUT), modifier = Modifier.size(20.dp))
                }
            }

            // Z-Layer 3: BOTTOM ACTION HUD (CANCEL asks END NAVIGATION? first)
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

        when {
            endConfirm -> ScreenEndNavigation(destination, onEnd = onEnd, onKeep = onKeep)
            navState is NavState.LostRoute -> ScreenLostRoute(onRetry = onRetryReroute, onEnd = onEnd)
            navState is NavState.GpsLost -> GpsLostOverlay(onRetry = onRetryGps, clock = clock)
        }
    }
}

/** The floating HUD card: next maneuver, distance to it, instruction (or the off-route status), remaining and ETA. */
@Composable
private fun NavigationHud(navState: NavState?, modifier: Modifier) {
    val palette = Wgo.palette
    val language = LocalLanguage.current
    val nav = navState?.guidance()
    val next = nav?.next
    val status = when (navState) {
        is NavState.OffRoute -> StringKey.NAV_OFF_ROUTE
        is NavState.Rerouting -> StringKey.NAV_REROUTING
        else -> null
    }
    Box(
        modifier = modifier
            .padding(top = 28.dp)
            .fillMaxWidth(0.9f)
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colors.surface.copy(alpha = 0.90f))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                val maneuver = next?.maneuver
                Icon(
                    imageVector = if (maneuver == null || maneuver in STRAIGHT_LIKE) Icons.Rounded.ArrowUpward else WgoIcons.TurnRight,
                    contentDescription = null,
                    tint = palette.primaryInk,
                    modifier = Modifier
                        .size(24.dp)
                        // Left turns mirror the right-turn glyph.
                        .graphicsLayer { scaleX = if (maneuver in LEFT_LIKE) -1f else 1f }
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = nav?.let { formatDistance(it.distanceToNextM, language) } ?: "200m",
                    color = MaterialTheme.colors.onSurface,
                    style = MaterialTheme.typography.title3.copy(fontWeight = FontWeight.Bold)
                )
            }
            if (status != null) {
                Text(
                    text = string(status),
                    color = palette.primaryInk,
                    style = WgoType.labelMd,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }
                )
            } else {
                Text(
                    text = next?.let { ManeuverText.instruction(it, language) } ?: string(StringKey.NAV_INSTRUCTION),
                    color = MaterialTheme.colors.onSurface,
                    style = MaterialTheme.typography.caption2.copy(fontWeight = FontWeight.Medium),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (nav != null) Text(
                // Remaining distance and ETA, live from the engine.
                text = String.format(string(StringKey.NAV_TOTALS), formatDistance(nav.remainingM, language), minutesOf(nav.etaS)),
                color = MaterialTheme.colors.onSurface,
                style = MaterialTheme.typography.caption3,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

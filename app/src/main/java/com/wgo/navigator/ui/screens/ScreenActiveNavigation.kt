package com.wgo.navigator.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.wgo.navigator.ui.map.RadarState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import com.wgo.navigator.ui.components.WgoChip
import com.wgo.navigator.ui.theme.LocalWgoScale
import com.wgo.navigator.ui.components.HudContent
import com.wgo.navigator.ui.components.NavigationHud
import com.wgo.navigator.ui.map.RadarProjection
import com.wgo.navigator.metrics.WgoMetrics
import com.wgo.navigator.ui.theme.WgoMotion
import com.wgo.navigator.ui.theme.atLeastRealDp
import com.wgo.navigator.ui.theme.wgoMinTouch
import com.wgo.navigator.ui.theme.wgoTouchSide
import kotlinx.coroutines.delay
import androidx.wear.compose.material.Icon
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

private const val MIN_ZOOM = 15.0
private const val MAX_ZOOM = 18.0

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
 *   KEEP) with its animations stopped (reduce motion forced under an opaque surface). Its data keeps updating: freezing
 *   it was measured in P6-B and gave no gain (END NAVIGATION? / GPS LOST jank identical with and without).
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
    onOpenLocationSettings: () -> Unit = {},
    /** Previews only: show this HUD state instead of the rule's. */
    forcedHud: HudMode? = null,
    /** Driving trip: CANCELAR gets the 60 real dp `hit-drive` touch height. */
    driving: Boolean = false
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
        val radar: RadarState = source.state.collectAsState().value
        var zoom by remember { mutableDoubleStateOf(17.0) }
        val reduceMotion = LocalWgoReduceMotion.current
        // Explore mode (spec section 7): UI state only, dropped with this screen (trip end, ambient).
        val explore = remember { ExploreController { SystemClock.elapsedRealtime() } }
        var tileSize by remember { mutableDoubleStateOf(RadarProjection.DEFAULT_TILE_SIZE_PX) }

        // Z-Layer 0: RADAR MINIMAP. Under an opaque surface its loops stop (no frames for an invisible sweep).
        CompositionLocalProvider(LocalWgoReduceMotion provides (reduceMotion || covered)) {
            RadarMinimap(
                style = wgoMapStyle(mapStyleId, Wgo.palette),
                userLocation = radar.location,
                bearing = radar.bearing,
                route = radar.route,
                blips = radar.blips,
                maneuver = radar.maneuver,
                zoom = zoom,
                clock = clock,
                viewCenter = explore.viewCenter(radar.location),
                viewBearing = explore.viewBearing(radar.bearing),
                onTileSize = { tileSize = it }
            )
        }

        // Explore: back to following 10 s after the last touch (paused while a finger is down), with a 420 ms glide.
        LaunchedEffect(explore.exploring, explore.touching, explore.lastTouchMs, explore.glideFrom) {
            if (!explore.exploring || explore.glideFrom != null) return@LaunchedEffect
            val wait = ExploreRules.autoReturnInMs(explore.lastTouchMs, explore.touching, SystemClock.elapsedRealtime())
                ?: return@LaunchedEffect
            delay(wait)
            explore.startReturn()
        }
        LaunchedEffect(explore.glideFrom) {
            if (explore.glideFrom == null) return@LaunchedEffect
            if (!reduceMotion) {
                animate(0f, 1f, animationSpec = tween(ExploreRules.RETURN_GLIDE_MS, easing = WgoMotion.EmphasizedDecel)) { v, _ ->
                    explore.glide = v
                }
            }
            explore.finish()
        }

        if (!covered) {
            // Z-Layer 0.5: the one-finger pan surface (above the map, below every control). Taps pass through.
            val latestRadar by rememberUpdatedState(radar)
            val latestZoom by rememberUpdatedState(zoom)
            val latestTile by rememberUpdatedState(tileSize)
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(explore) {
                        val slop = viewConfiguration.touchSlop
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            explore.onTouch(true)
                            var travelled = Offset.Zero
                            var panning = false
                            var applied = Offset.Zero
                            var events = 0
                            var endReason = "up"
                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    events++
                                    // Only the first finger pans; a second one is ignored (no pinch).
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: run { endReason = "lost"; null } ?: break
                                    if (change.isConsumed) { endReason = "claimed"; break } // edge swipe-back
                                    // The release can carry the last stretch of the drag: apply it before stopping.
                                    val delta = change.positionChange()
                                    val released = !change.pressed
                                    if (!panning) {
                                        travelled += delta
                                        if (travelled.getDistance() < slop) { if (released) break else continue }
                                        panning = true
                                    }
                                    val step = if (panning && travelled != Offset.Zero) travelled.also { travelled = Offset.Zero } else delta
                                    val r = latestRadar
                                    explore.pan(step.x, step.y, r.location, r.bearing, r.route, latestZoom, latestTile)
                                    applied += step
                                    change.consume()
                                    if (released) break
                                }
                            } finally {
                                explore.onTouch(false)
                                if (panning) WgoMetrics.event(
                                    "explore_drag px=${applied.x.toInt()},${applied.y.toInt()} events=$events end=$endReason " +
                                        "tile=${latestTile.toInt()} center_from_user_m=" +
                                        (explore.center?.let { RadarProjection.distanceMeters(it, latestRadar.location).toInt() } ?: -1)
                                )
                            }
                        }
                    }
            )
        }

        if (!covered) {
            // Adaptive HUD (spec section 7): the state is HudRules' pure function; taps and the end of the reroute
            // window bump hudTick so the rule runs again.
            val tracker = remember { HudTracker() }
            var hudTick by remember { mutableIntStateOf(0) }
            hudTick.let { } // read: a tap or the window's end recomposes this scope
            val nav = navState?.guidance()
            val input = HudRules.Input(
                distanceToNextM = nav?.distanceToNextM,
                maneuver = nav?.next,
                rerouting = navState is NavState.OffRoute || navState is NavState.Rerouting,
                exploring = explore.exploring,
                nowMs = SystemClock.elapsedRealtime()
            )
            val hud = tracker.update(input)
            val windowEnd = hud.newRouteAtMs
            LaunchedEffect(windowEnd) {
                val left = HudRules.rerouteWindowLeftMs(hud, SystemClock.elapsedRealtime()) ?: return@LaunchedEffect
                delay(left + 20)
                hudTick++
            }
            NavigationHud(
                mode = forcedHud ?: hud.shown,
                content = hudContent(navState),
                onTap = {
                    // While exploring a tap on the HUD recenters; otherwise it forces the other state.
                    if (explore.exploring) explore.startReturn()
                    else if (tracker.toggle(SystemClock.elapsedRealtime())) hudTick++
                },
                clock = clock,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // RECENTER (explore only): 40 dp `primary` circle centred at (113.5, 98), 48 real dp touch.
            if (explore.exploring) {
                RecenterButton(
                    onClick = { explore.startReturn() },
                    modifier = Modifier.align(Alignment.TopCenter).offset(y = 98.dp - wgoTouchSide(40.dp) / 2)
                )
            }

            // Z-Layer 2.5: ZOOM - at (41.5, 150) and + at (185.5, 150): fixed 40 dp circles, 48 real dp touch (fit 99.4).
            val controlTouch = wgoTouchSide(40.dp)
            MapControlButton(
                icon = WgoIcons.Minus,
                description = string(StringKey.ZOOM_OUT),
                onClick = { zoom = (zoom - 1.0).coerceAtLeast(MIN_ZOOM) },
                modifier = Modifier.align(Alignment.TopStart).offset(x = 41.5.dp - controlTouch / 2, y = 150.dp - controlTouch / 2)
            )
            MapControlButton(
                icon = WgoIcons.Plus,
                description = string(StringKey.ZOOM_IN),
                onClick = { zoom = (zoom + 1.0).coerceAtMost(MAX_ZOOM) },
                modifier = Modifier.align(Alignment.TopStart).offset(x = 185.5.dp - controlTouch / 2, y = 150.dp - controlTouch / 2)
            )

            // Z-Layer 3: CANCELAR 86x32 @176 (fit 99.0), touch 48 real dp walking / 60 (hit-drive) driving; asks
            // END NAVIGATION? first. 86 dp keeps its touch box clear of the zoom buttons' at every scale.
            val cancelTouchReal = if (driving) 60.dp else 48.dp
            val cancelTouch = maxOf(32.dp, cancelTouchReal / LocalWgoScale.current)
            WgoChip(
                label = string(StringKey.BTN_CANCEL),
                onClick = onCancel,
                width = 86.dp,
                touchHeight = cancelTouchReal,
                modifier = Modifier.align(Alignment.TopCenter).offset(y = 192.dp - cancelTouch / 2)
            )
        }

        when {
            endConfirm -> ScreenEndNavigation(destination, onEnd = onEnd, onKeep = onKeep)
            navState is NavState.LostRoute -> ScreenLostRoute(onRetry = onRetryReroute, onEnd = onEnd)
            navState is NavState.GpsLost -> GpsLostOverlay(onRetry = onRetryGps, clock = clock)
        }
    }
}

/**
 * Map control: a fixed 40 dp circle with a 48 REAL dp touch box (spec section 7). [primary] fills it with `primary`
 * (recenter); otherwise `surface` + `edge-control` (zoom). Pressing inverts instantly. Never resized.
 */
@Composable
internal fun MapControlButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    iconSize: Dp = 20.dp
) {
    val palette = Wgo.palette
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill = when {
        pressed -> palette.ink
        primary -> palette.primary
        else -> palette.surface
    }
    val tint = when {
        pressed -> palette.inkInverse
        primary -> palette.onPrimary
        else -> palette.ink
    }
    Box(
        modifier = modifier
            .wgoMinTouch()
            .semantics { contentDescription = description }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(fill, CircleShape)
                .border(1.5.dp.atLeastRealDp(), palette.ink, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
        }
    }
}

@Composable
private fun RecenterButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    MapControlButton(
        icon = WgoIcons.Locate,
        description = string(StringKey.RECENTER),
        onClick = onClick,
        modifier = modifier,
        primary = true,
        iconSize = 22.dp
    )
}

/** What the HUD shows, from the engine state (previews without a state get a sample instruction). */
@Composable
private fun hudContent(navState: NavState?): HudContent {
    val language = LocalLanguage.current
    val nav = navState?.guidance()
    val next = nav?.next
    val status = when (navState) {
        is NavState.OffRoute -> string(StringKey.NAV_OFF_ROUTE)
        is NavState.Rerouting -> string(StringKey.NAV_REROUTING)
        else -> null
    }
    // "250 m" / "1,2 km": number and unit apart, for the odometer.
    val distance = formatDistance(nav?.distanceToNextM ?: 200.0, language)
    val split = distance.lastIndexOf(' ')
    return HudContent(
        maneuver = next?.maneuver ?: if (nav == null) Maneuver.RIGHT else null,
        distanceM = nav?.distanceToNextM,
        distanceNumber = if (split > 0) distance.substring(0, split) else distance,
        distanceUnit = if (split > 0) distance.substring(split + 1) else "",
        instruction = next?.let { ManeuverText.instruction(it, language) } ?: string(StringKey.NAV_INSTRUCTION),
        status = status,
        totals = nav?.let {
            String.format(string(StringKey.NAV_TOTALS), formatDistance(it.remainingM, language), minutesOf(it.etaS))
        }
    )
}

/**
 * Keeps the [HudRules] state between frames. [update] runs during composition: [HudRules.step] is idempotent, so a
 * repeated composition with the same input changes nothing.
 */
private class HudTracker {
    var state: HudRules.State? = null
        private set
    private var lastInput: HudRules.Input? = null

    fun update(input: HudRules.Input): HudRules.State {
        lastInput = input
        return (state?.let { HudRules.step(it, input) } ?: HudRules.initial(input)).also { state = it }
    }

    /** A tap with the latest input; false when there is nothing to toggle yet. */
    fun toggle(nowMs: Long): Boolean {
        val s = state ?: return false
        val input = lastInput?.copy(nowMs = nowMs) ?: return false
        state = HudRules.toggle(s, input)
        return true
    }
}

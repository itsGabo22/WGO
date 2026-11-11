package com.wgo.navigator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.wgo.navigator.PipelineError
import com.wgo.navigator.WorkStage
import com.wgo.navigator.core.Place
import com.wgo.navigator.core.permissions.PermissionStatus
import com.wgo.navigator.core.permissions.WgoPermission
import com.wgo.navigator.ui.LocalLanguage
import com.wgo.navigator.ui.StringKey
import com.wgo.navigator.ui.components.BezelGauge
import com.wgo.navigator.ui.components.MassiveButton
import com.wgo.navigator.ui.components.MassiveButtonVariant
import com.wgo.navigator.ui.components.MicOrb
import com.wgo.navigator.ui.components.MicState
import com.wgo.navigator.ui.components.PermissionDenied
import androidx.compose.ui.semantics.contentDescription
import com.wgo.navigator.ui.components.StatusBadge
import com.wgo.navigator.ui.components.StatusBadgeVariant
import com.wgo.navigator.ui.components.WgoChip
import com.wgo.navigator.ui.components.WgoIcons
import com.wgo.navigator.ui.formatDistance
import com.wgo.navigator.ui.string
import com.wgo.navigator.ui.theme.Wgo
import com.wgo.navigator.ui.theme.WgoClock
import com.wgo.navigator.ui.theme.WgoType
import com.wgo.navigator.ui.theme.rememberWgoClock
import com.wgo.navigator.ui.theme.wgoRiseIn
import com.wgo.navigator.ui.theme.wgoTouchSide

/*
 * The three pipeline surfaces. Geometry is in reference dp on the 227 dp canvas (WgoResponsive scales it); each box
 * lists its fit value against the Galaxy Watch5 Pro limit of 103 (spec section 4, `wgoFit(w, h, top)`).
 */

/** Places the visual centre of a box whose touch side is [touch] on the spec line [centreY]. */
private fun topFor(centreY: Float, touch: Dp): Dp = centreY.dp - touch / 2

/**
 * WORKING, one surface for every stage. Badge 80x18 @16 (fit 102.8, as on screen 01); stage label `label-md` 130x14 @38 (96.8, live
 * region); detail `body-md` one line 150x18 @54 (92.1); processing orb 64 @92 (42.5); CANCEL chip 104x32 @176 (102.4).
 * The bezel shows the stage as a static fill (1/3, 2/3, 9/10); only the orb's arc loops (screen clock, full rate), and
 * reduce motion stops it.
 */
@Composable
fun ScreenWorking(
    stage: WorkStage,
    detail: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    clock: WgoClock = rememberWgoClock()
) {
    val palette = Wgo.palette
    val fill = when (stage) {
        WorkStage.Interpreting -> 1f / 3f
        WorkStage.Resolving -> 2f / 3f
        WorkStage.Routing -> 0.9f
    }
    val label = string(
        when (stage) {
            WorkStage.Interpreting -> StringKey.STAGE_INTERPRETING
            WorkStage.Resolving -> StringKey.STAGE_RESOLVING
            WorkStage.Routing -> StringKey.TITLE_ROUTING
        }
    )
    Box(modifier.fillMaxSize().background(palette.canvas)) {
        BezelGauge(progress = { fill })
        StatusBadge(
            text = "WGO • NAV",
            variant = StatusBadgeVariant.Neutral,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = 16.dp)
        )
        Text(
            text = label,
            color = palette.primaryInk,
            style = WgoType.labelMd,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 38.dp)
                .width(130.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
        )
        if (detail.isNotEmpty()) Text(
            text = detail,
            color = palette.ink,
            style = WgoType.bodyMd,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = 54.dp).width(150.dp)
        )
        MicOrb(
            state = MicState.Processing,
            onClick = null,
            clock = clock,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = 92.dp)
        )
        val chipTouch = wgoTouchSide(32.dp)
        WgoChip(
            label = string(StringKey.BTN_CANCEL),
            onClick = onCancel,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = topFor(192f, chipTouch))
        )
    }
}

/**
 * CONFIRM PLACE (P6-B). Name `headline-sm` 2 lines 130x40 @28 (99.5, live region, announced as "DESTINO: name");
 * subtitle `body-md` 150x36 @70 (80.4): line 1 "a 1,8 km", line 2 the neighbourhood / address, ellipsis at 2 lines
 * (the old one-line "a 1,8 km · street" truncated the neighbourhood; the "DESTINO" badge made room); GO MassiveButton 120 wide
 * centred at 134 (65.7 at s = 1, 66.1 with the 56.7 dp touch height of s = 0.846); "© OPENSTREETMAP" `label-sm`
 * 130x12 @164 (87.7, one line, no wrap); OTHER chip "OTRO 1/3" 104x32 @176 (102.4), shown only with more than one result. The GO and
 * OTHER hit boxes never overlap (105.6-162.4 and 163.6-220.3 at s = 0.846). Static: no clock.
 * Values are checked by PipelineSurfacesFitTest.
 */
@Composable
fun ScreenConfirmPlace(
    place: Place,
    distanceM: Double?,
    index: Int,
    count: Int,
    onGo: () -> Unit,
    onOther: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = Wgo.palette
    val language = LocalLanguage.current
    // The "DESTINO" badge is gone (P6-B: the two-line subtitle needs its room); screen readers still hear it.
    val spokenName = "${string(StringKey.CONFIRM_BADGE)}: ${place.name}"
    // Distance on line 1, neighbourhood / address on line 2; without a distance the address may use both lines.
    val subtitle = listOfNotNull(
        distanceM?.let { String.format(string(StringKey.DIST_AWAY), formatDistance(it, language)) },
        place.address.takeIf { it.isNotBlank() }
    ).joinToString("\n")
    val option = String.format(string(StringKey.CONFIRM_OPTION), index + 1, count)

    Box(modifier.fillMaxSize().background(palette.canvas)) {
        Text(
            text = place.name,
            color = palette.ink,
            style = WgoType.headlineSm,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 28.dp)
                .width(130.dp)
                .semantics {
                    contentDescription = spokenName
                    liveRegion = LiveRegionMode.Polite
                }
                .wgoRiseIn()
        )
        if (subtitle.isNotEmpty()) Text(
            text = subtitle,
            color = palette.inkMuted,
            style = WgoType.bodyMd,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = 70.dp).width(150.dp).wgoRiseIn()
        )
        val goTouch = wgoTouchSide(52.dp)
        MassiveButton(
            onClick = onGo,
            label = string(StringKey.BTN_GO),
            icon = WgoIcons.ArrowUpward,
            variant = MassiveButtonVariant.Primary,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = topFor(134f, goTouch))
                .width(120.dp)
                .wgoRiseIn(delayMs = 80)
        )
        Text(
            text = string(StringKey.OSM_ATTRIBUTION).uppercase(),
            color = palette.inkMuted,
            style = WgoType.labelSm,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = 164.dp).width(130.dp)
        )
        if (count > 1) {
            val chipTouch = wgoTouchSide(32.dp)
            WgoChip(
                label = "${string(StringKey.BTN_OTHER)} ${index + 1}/$count",
                onClick = onOther,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = topFor(192f, chipTouch))
                    .semantics { stateDescription = option }
            )
        }
    }
}

/**
 * ERROR, one surface for every failure, with a single retry. The two permission failures reuse [PermissionDenied]
 * (retry, or the app settings after "don't ask again"); the others use the same geometry: icon disc 40 @30 (83.5),
 * title `headline-sm` 150x40 @80 (76.6, live region), hint `body-md` 160x36 @124 (86.2), RETRY chip 120x32 centred at
 * 186 (100.8). Static: no clock.
 */
@Composable
fun ScreenError(
    error: PipelineError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    permissionStatus: PermissionStatus = PermissionStatus.Denied,
    onOpenSettings: () -> Unit = {}
) {
    if (error == PipelineError.MicPermission || error == PipelineError.LocationPermission) {
        PermissionDenied(
            permission = if (error == PipelineError.MicPermission) WgoPermission.MICROPHONE else WgoPermission.LOCATION,
            status = permissionStatus,
            onRetry = onRetry,
            onOpenSettings = onOpenSettings,
            modifier = modifier
        )
        return
    }
    val palette = Wgo.palette
    val (title, hint) = when (error) {
        PipelineError.VoiceUnavailable -> StringKey.ERR_VOICE_UNAVAILABLE to StringKey.HINT_VOICE_UNAVAILABLE
        PipelineError.NotUnderstood -> StringKey.ERR_NOT_UNDERSTOOD to StringKey.HINT_SPEAK_AGAIN
        PipelineError.Offline -> StringKey.ERR_OFFLINE to StringKey.HINT_CHECK_CONNECTION
        PipelineError.PlaceNotFound -> StringKey.ERR_PLACE_NOT_FOUND to StringKey.HINT_OTHER_NAME
        PipelineError.RouteUnavailable -> StringKey.ERR_ROUTE_UNAVAILABLE to StringKey.HINT_ROUTE
        PipelineError.AiUnavailable -> StringKey.ERR_AI_UNAVAILABLE to StringKey.HINT_AI
        PipelineError.Timeout -> StringKey.ERR_TIMEOUT to StringKey.HINT_TIMEOUT
        PipelineError.MicStart -> StringKey.ERR_MIC_START to StringKey.HINT_MIC_START
        PipelineError.MicPermission, PipelineError.LocationPermission -> error("handled above")
    }
    val icon = when (error) {
        PipelineError.VoiceUnavailable, PipelineError.NotUnderstood, PipelineError.MicStart -> WgoIcons.Mic
        PipelineError.PlaceNotFound, PipelineError.RouteUnavailable -> WgoIcons.LocationPin
        else -> WgoIcons.Alert
    }
    Box(modifier.fillMaxSize().background(palette.canvas)) {
        Box(
            Modifier.align(Alignment.TopCenter).offset(y = 30.dp).size(40.dp).clip(CircleShape).background(palette.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = palette.alert, modifier = Modifier.size(24.dp))
        }
        Text(
            text = string(title),
            style = WgoType.headlineSm,
            color = palette.ink,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 80.dp)
                .width(150.dp)
                .semantics { liveRegion = LiveRegionMode.Polite }
        )
        Text(
            text = string(hint),
            style = WgoType.bodyMd,
            color = palette.inkMuted,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = 124.dp).width(160.dp)
        )
        val chipTouch = wgoTouchSide(32.dp)
        WgoChip(
            label = string(StringKey.PERM_RETRY),
            onClick = onRetry,
            width = 120.dp,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = topFor(186f, chipTouch))
        )
    }
}

package com.wgo.navigator.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.wgo.navigator.Language
import com.wgo.navigator.core.permissions.PermissionStatus
import com.wgo.navigator.core.permissions.WgoPermission
import com.wgo.navigator.ui.LocalLanguage
import com.wgo.navigator.ui.StringKey
import com.wgo.navigator.ui.string
import com.wgo.navigator.ui.theme.Wgo
import com.wgo.navigator.ui.theme.WgoType

/**
 * Full-screen state shown when a feature cannot run because its permission is missing. Static (nothing loops, so
 * reduce motion needs no variant). Layout in reference dp (fit values against the 103 limit):
 * icon disc 40 @30 (83.5), title `headline-sm` 150 x 40 @80 (76.6), explanation `body-md` 160 x 36 @124 (86.2),
 * chip 120 x 32 @170 (100.8, 48 real dp touch height through WgoChip).
 *
 * [PermissionStatus.Denied]/[PermissionStatus.NotAsked]: the chip retries (the system dialog shows again).
 * [PermissionStatus.PermanentlyDenied]: a one-line "blocked" explanation and the chip opens the app's settings.
 */
@Composable
fun PermissionDenied(
    permission: WgoPermission,
    status: PermissionStatus,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = Wgo.palette
    val blocked = status == PermissionStatus.PermanentlyDenied
    val title = string(if (permission == WgoPermission.LOCATION) StringKey.PERM_LOCATION_TITLE else StringKey.PERM_MIC_TITLE)
    val why = when {
        blocked -> string(StringKey.PERM_BLOCKED_HINT)
        permission == WgoPermission.LOCATION -> string(StringKey.PERM_LOCATION_WHY)
        else -> string(StringKey.PERM_MIC_WHY)
    }

    Box(modifier.fillMaxSize().background(palette.canvas)) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = 30.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(palette.surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (permission == WgoPermission.LOCATION) WgoIcons.LocationPin else WgoIcons.Mic,
                contentDescription = null,
                tint = palette.ink,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = title,
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
            text = why,
            style = WgoType.bodyMd,
            color = palette.inkMuted,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 124.dp)
                .width(160.dp)
        )
        WgoChip(
            label = string(if (blocked) StringKey.PERM_OPEN_SETTINGS else StringKey.PERM_RETRY),
            onClick = if (blocked) onOpenSettings else onRetry,
            width = 120.dp,
            // the chip's touch box is 48 real dp tall around the 32 dp pill whose centre sits at 186 dp
            modifier = Modifier.align(Alignment.TopCenter).offset(y = 186.dp - wgoChipTouchHalf())
        )
    }
}

/** Half of the chip's touch height in reference dp (48 real dp), so the visual pill stays centred on its spec line. */
@Composable
private fun wgoChipTouchHalf() = com.wgo.navigator.ui.theme.wgoTouchSide(32.dp) / 2

/** Opens this app's system settings page (where a blocked permission can be granted). False when unavailable. */
fun Context.openAppPermissionSettings(): Boolean = try {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
    true
} catch (e: ActivityNotFoundException) {
    false
}

@Composable
private fun DeniedPreview(dark: Boolean, permission: WgoPermission, status: PermissionStatus, language: Language = Language.ES) {
    CompositionLocalProvider(LocalLanguage provides language) {
        WgoPreviewFrame(dark) { PermissionDenied(permission, status, onRetry = {}, onOpenSettings = {}) }
    }
}

@WgoWatchPreviews @Composable
private fun DeniedLocationDaylight() = DeniedPreview(false, WgoPermission.LOCATION, PermissionStatus.Denied)

@WgoWatchPreviews @Composable
private fun DeniedLocationNight() = DeniedPreview(true, WgoPermission.LOCATION, PermissionStatus.Denied)

@WgoWatchPreviews @Composable
private fun BlockedLocationEnNight() = DeniedPreview(true, WgoPermission.LOCATION, PermissionStatus.PermanentlyDenied, Language.EN)

@WgoWatchPreviews @Composable
private fun BlockedMicDaylight() = DeniedPreview(false, WgoPermission.MICROPHONE, PermissionStatus.PermanentlyDenied)

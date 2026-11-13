package com.wgo.navigator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.wgo.navigator.ui.StringKey
import com.wgo.navigator.ui.components.MassiveButton
import com.wgo.navigator.ui.components.MassiveButtonVariant
import com.wgo.navigator.ui.components.WgoChip
import com.wgo.navigator.ui.components.WgoPreviewFrame
import com.wgo.navigator.ui.string
import com.wgo.navigator.ui.theme.Wgo
import com.wgo.navigator.ui.theme.WgoType

/**
 * The one-time cloud transcription notice (P9, docs/design "ScreenSettings and the first-use notice"): shown on the
 * first press in FAST (CLOUD) mode before it was answered. The Credits list layout (a scrolling column 160 dp wide):
 * title, what is sent and to whom, ACCEPT (keeps the cloud mode) and USE SYSTEM (switches the setting to SYSTEM); both
 * go back to screen 01, idle, for the user's next press. Back answers nothing.
 */
@Composable
fun ScreenVoiceNotice(onAccept: () -> Unit, onUseSystem: () -> Unit, modifier: Modifier = Modifier) {
    val palette = Wgo.palette
    val scroll = rememberScrollState()
    Box(modifier.fillMaxSize().background(palette.canvas)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(scroll),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(Modifier.height(22.dp))
            Text(
                string(StringKey.NOTICE_TITLE).uppercase(),
                color = palette.ink,
                style = WgoType.labelMd,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = string(StringKey.NOTICE_BODY),
                color = palette.ink,
                style = WgoType.bodyMd.copy(fontSize = 13.sp, lineHeight = 17.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier.width(160.dp)
            )
            Spacer(Modifier.height(2.dp))
            MassiveButton(
                onClick = onAccept,
                label = string(StringKey.BTN_ACCEPT),
                variant = MassiveButtonVariant.Primary,
                modifier = Modifier.width(120.dp)
            )
            WgoChip(label = string(StringKey.BTN_USE_SYSTEM), onClick = onUseSystem, width = 140.dp)
            Spacer(Modifier.height(70.dp))
        }
        PositionIndicator(scrollState = scroll)
    }
}

@Preview(device = "id:wearos_large_round", showSystemUi = true, name = "Voice notice - Daylight")
@Composable
private fun VoiceNoticeDaylight() = WgoPreviewFrame(false) { ScreenVoiceNotice({}, {}) }

@Preview(device = "id:wearos_large_round", showSystemUi = true, name = "Voice notice - Night")
@Composable
private fun VoiceNoticeNight() = WgoPreviewFrame(true) { ScreenVoiceNotice({}, {}) }

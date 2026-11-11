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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Text
import com.wgo.navigator.BuildConfig
import com.wgo.navigator.ui.StringKey
import com.wgo.navigator.ui.string
import com.wgo.navigator.ui.theme.Wgo
import com.wgo.navigator.ui.theme.WgoType

/**
 * Credits (spec section 7, P7): data and software sources with their licences, the app version and build, and three
 * privacy lines. A scrolling column 160 dp wide (position indicator while scrolling); leave with Back / edge swipe.
 */
@Composable
fun ScreenCredits(modifier: Modifier = Modifier) {
    val palette = Wgo.palette
    val scroll = rememberScrollState()
    Box(modifier.fillMaxSize().background(palette.canvas)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(scroll),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Spacer(Modifier.height(18.dp))
            Text(string(StringKey.SET_CREDITS).uppercase(), color = palette.ink, style = WgoType.labelMd)
            Spacer(Modifier.height(6.dp))
            listOf(StringKey.CR_OSM_D, StringKey.CR_CARTO_D, StringKey.CR_OVERTURE_D, StringKey.CR_OSMDROID_D, StringKey.CR_SERVICES_D)
                .forEach { Block(string(it)) }
            Block(String.format(string(StringKey.CR_VERSION), BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), muted = true)
            Spacer(Modifier.height(4.dp))
            Text(string(StringKey.CR_PRIVACY_H).uppercase(), color = palette.ink, style = WgoType.labelMd)
            listOf(StringKey.CR_PRIVACY_1, StringKey.CR_PRIVACY_2, StringKey.CR_PRIVACY_3).forEach { Block(string(it)) }
            Spacer(Modifier.height(90.dp))
        }
        PositionIndicator(scrollState = scroll)
    }
}

@Composable
private fun Block(text: String, muted: Boolean = false) {
    val palette = Wgo.palette
    Text(
        text = text,
        color = if (muted) palette.inkMuted else palette.ink,
        style = WgoType.bodyMd.copy(fontSize = 13.sp, lineHeight = 17.sp),
        textAlign = TextAlign.Center,
        modifier = Modifier.width(160.dp)
    )
}

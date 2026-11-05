package com.wgo.navigator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.wgo.navigator.ui.theme.Wgo
import com.wgo.navigator.ui.theme.WgoTheme

/** Wear Large Round preview (227 dp). Combine with [WgoPreviewFrame] to choose Daylight or Night. */
@Preview(name = "Large Round 227dp", device = "id:wearos_large_round", showBackground = false)
annotation class WgoPreview

/** Small Round (192 dp) variant for fit checks. */
@Preview(name = "Small Round 192dp", device = "id:wearos_small_round", showBackground = false)
annotation class WgoPreviewSmall

/** Renders [content] inside [WgoTheme] on a round 227 dp canvas filled with the theme canvas color. */
@Composable
fun WgoPreviewFrame(dark: Boolean, content: @Composable () -> Unit) {
    WgoTheme(isDarkMode = dark) {
        Box(
            modifier = Modifier
                .size(227.dp)
                .clip(CircleShape)
                .background(Wgo.palette.canvas),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

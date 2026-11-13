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

/**
 * Multi-preview over the target watches: Small Round (192 dp, s 0.846), Large Round (227 dp, s 1.0),
 * a Galaxy Watch5 Pro-like screen (450 px at 321 dpi, about 225 dp, s 0.991) and a 240 dp screen (s 1.057).
 * Pair it with one composable per theme (Daylight / Night).
 */
@Preview(name = "Small Round 192dp", device = "id:wearos_small_round", showBackground = false)
@Preview(name = "Large Round 227dp", device = "id:wearos_large_round", showBackground = false)
@Preview(name = "Watch5 Pro 225dp", device = "spec:width=450px,height=450px,dpi=321,isRound=true", showBackground = false)
@Preview(name = "Large 240dp", device = "spec:width=480px,height=480px,dpi=240,isRound=true", showBackground = false)
annotation class WgoWatchPreviews

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

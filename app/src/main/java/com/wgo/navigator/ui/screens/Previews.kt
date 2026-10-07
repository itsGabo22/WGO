package com.wgo.navigator.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material.Scaffold
import com.wgo.navigator.ui.theme.WgoTheme

@Preview(device = "id:wearos_small_round", showSystemUi = true)
@Composable
fun ScreenListeningPreview() {
    WgoTheme {
        Scaffold {
            ScreenListening(
                isListening = false,
                onToggleListen = {},
                onCancel = {},
                onOpenSettings = {}
            )
        }
    }
}

@Preview(device = "id:wearos_small_round", showSystemUi = true)
@Composable
fun ScreenModeSelectionPreview() {
    WgoTheme {
        Scaffold {
            ScreenModeSelection(
                onSelectMode = {}
            )
        }
    }
}

@Preview(device = "id:wearos_small_round", showSystemUi = true)
@Composable
fun ScreenActiveNavigationPreview() {
    WgoTheme {
        Scaffold {
            ScreenActiveNavigation(
                onCancel = {}
            )
        }
    }
}

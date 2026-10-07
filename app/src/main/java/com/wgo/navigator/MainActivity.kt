package com.wgo.navigator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText
import com.wgo.navigator.ui.screens.ScreenActiveNavigation
import com.wgo.navigator.ui.screens.ScreenListening
import com.wgo.navigator.ui.screens.ScreenModeSelection
import com.wgo.navigator.ui.theme.WgoTheme

enum class ScreenState {
    Listening, ModeSelection, Navigation
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WgoTheme {
                var currentScreen by remember { mutableStateOf(ScreenState.Listening) }
                var isListening by remember { mutableStateOf(false) }

                Scaffold(
                    timeText = { 
                        // Only show time on certain screens if desired, or omit for pure UI focus
                    }
                ) {
                    when (currentScreen) {
                        ScreenState.Listening -> {
                            ScreenListening(
                                isListening = isListening,
                                onToggleListen = {
                                    isListening = true
                                    // Simulate voice processing delay
                                    window.decorView.postDelayed({
                                        currentScreen = ScreenState.ModeSelection
                                        isListening = false
                                    }, 2000)
                                },
                                onCancel = {
                                    isListening = false
                                }
                            )
                        }
                        ScreenState.ModeSelection -> {
                            ScreenModeSelection(
                                onSelectMode = { _ ->
                                    currentScreen = ScreenState.Navigation
                                }
                            )
                        }
                        ScreenState.Navigation -> {
                            ScreenActiveNavigation(
                                onCancel = {
                                    currentScreen = ScreenState.Listening
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

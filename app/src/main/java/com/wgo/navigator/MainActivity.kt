package com.wgo.navigator

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.wear.compose.material.Scaffold
import com.wgo.navigator.ui.LocalLanguage
import com.wgo.navigator.ui.screens.ScreenActiveNavigation
import com.wgo.navigator.ui.screens.ScreenListening
import com.wgo.navigator.ui.screens.ScreenModeSelection
import com.wgo.navigator.ui.screens.ScreenSettings
import com.wgo.navigator.ui.theme.DaylightPalette
import com.wgo.navigator.ui.theme.NightPalette
import com.wgo.navigator.ui.theme.WgoTheme
import org.osmdroid.config.Configuration

enum class ScreenState {
    Listening, ModeSelection, Navigation, Settings
}

class MainActivity : ComponentActivity() {
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize osmdroid configuration globally
        Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", android.content.Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        setContent {
            val isDarkMode by settingsViewModel.isDarkMode.collectAsState()
            val language by settingsViewModel.language.collectAsState()

            // Window background follows the in-app theme (not the system one): no white flash in Night.
            val canvas = (if (isDarkMode) NightPalette else DaylightPalette).canvas
            SideEffect { window.setBackgroundDrawable(ColorDrawable(canvas.toArgb())) }

            CompositionLocalProvider(LocalLanguage provides language) {
                WgoTheme(isDarkMode = isDarkMode) {
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
                                    onOpenSettings = {
                                        currentScreen = ScreenState.Settings
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
                            ScreenState.Settings -> {
                                ScreenSettings(
                                    viewModel = settingsViewModel,
                                    onClose = {
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
}

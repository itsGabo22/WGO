package com.wgo.navigator

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.wgo.navigator.haptics.HapticPlayer
import com.wgo.navigator.ui.components.MicState
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.wgo.navigator.ui.theme.LocalWgoReduceMotion
import com.wgo.navigator.ui.wgoScreenTransition
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
import com.wgo.navigator.ui.screens.ScreenMapPicker
import com.wgo.navigator.ui.screens.ScreenModeSelection
import com.wgo.navigator.ui.screens.ScreenSettings
import com.wgo.navigator.ui.map.WgoMapConfig
import com.wgo.navigator.ui.theme.DaylightPalette
import com.wgo.navigator.ui.theme.NightPalette
import com.wgo.navigator.ui.theme.WgoTheme
import kotlinx.coroutines.runBlocking

enum class ScreenState {
    Listening, ModeSelection, Navigation, Settings, MapPicker
}

class MainActivity : ComponentActivity() {
    private val settingsRepository by lazy { SettingsRepository(applicationContext) }

    // Seeded from the one-time blocking read in onCreate (runs before the first frame).
    private lateinit var initialSettings: WgoSettings

    private val appViewModel: AppViewModel by viewModels()

    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModel.Factory(settingsRepository, initialSettings)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Blocking read of a tiny DataStore file so theme and language are right on the first frame
        // (no flash between themes or languages). Cost: a few ms on the main thread at launch.
        initialSettings = runBlocking { settingsRepository.load() }
        val initialCanvas = (if (initialSettings.isDarkMode) NightPalette else DaylightPalette).canvas
        window.setBackgroundDrawable(ColorDrawable(initialCanvas.toArgb()))
        
        // Haptics: ViewModels emit events, only the Activity touches the vibrator.
        val hapticPlayer = HapticPlayer(applicationContext)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                merge(appViewModel.haptics, settingsViewModel.haptics).collect { hapticPlayer.play(it) }
            }
        }

        // osmdroid: package User-Agent and a small cache in the app cache dir
        WgoMapConfig.apply(applicationContext)

        setContent {
            val isDarkMode by settingsViewModel.isDarkMode.collectAsState()
            val language by settingsViewModel.language.collectAsState()

            // Window background follows the in-app theme (not the system one): no white flash in Night.
            val canvas = (if (isDarkMode) NightPalette else DaylightPalette).canvas
            SideEffect { window.setBackgroundDrawable(ColorDrawable(canvas.toArgb())) }

            CompositionLocalProvider(LocalLanguage provides language) {
                WgoTheme(isDarkMode = isDarkMode) {
                    val currentScreen by appViewModel.screen.collectAsState()
                    val micState by appViewModel.micState.collectAsState()
                    val selectedMode by appViewModel.selectedMode.collectAsState()
                    val mapStyleId by settingsViewModel.mapStyle.collectAsState()
                    val reduceMotion = LocalWgoReduceMotion.current

                    // Back leaves mode selection, navigation and settings for the listening screen.
                    BackHandler(enabled = currentScreen != ScreenState.Listening) {
                        if (currentScreen == ScreenState.MapPicker) appViewModel.backToSettings() else appViewModel.backToListening()
                    }

                    Scaffold(
                        timeText = { 
                            // Only show time on certain screens if desired, or omit for pure UI focus
                        }
                    ) {
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = {
                                wgoScreenTransition(initialState, targetState, selectedMode, reduceMotion)
                            },
                            modifier = Modifier.fillMaxSize(),
                            label = "screens"
                        ) { screen ->
                        when (screen) {
                            ScreenState.Listening -> {
                                ScreenListening(
                                    micState = micState,
                                    onMicClick = appViewModel::onMicTapped,
                                    onCancel = appViewModel::cancelListening,
                                    onOpenSettings = appViewModel::openSettings,
                                    onToggleLanguage = settingsViewModel::toggleLanguage
                                )
                            }
                            ScreenState.ModeSelection -> {
                                ScreenModeSelection(
                                    onSelectMode = appViewModel::selectMode,
                                    // The chosen pad stays inverted until the zoom transition finishes.
                                    pressedMode = selectedMode
                                )
                            }
                            ScreenState.Navigation -> {
                                ScreenActiveNavigation(
                                    onCancel = appViewModel::backToListening,
                                    mapStyleId = mapStyleId
                                )
                            }
                            ScreenState.MapPicker -> {
                                ScreenMapPicker(
                                    currentStyle = mapStyleId,
                                    onUse = { style ->
                                        settingsViewModel.commitMapStyle(style)
                                        appViewModel.backToSettings()
                                    },
                                    onStep = settingsViewModel::onMapPickerStep
                                )
                            }
                            ScreenState.Settings -> {
                                ScreenSettings(
                                    viewModel = settingsViewModel,
                                    onClose = appViewModel::backToListening
                                )
                            }
                        }
                        }
                    }
                }
            }
        }
    }
}

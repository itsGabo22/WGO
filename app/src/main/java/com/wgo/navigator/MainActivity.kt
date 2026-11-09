package com.wgo.navigator

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.wgo.navigator.haptics.HapticPlayer
import com.wgo.navigator.metrics.WgoMetrics
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
import com.wgo.navigator.ui.wgoEdgeSwipeBack
import com.wgo.navigator.ui.screens.ScreenActiveNavigation
import com.wgo.navigator.ui.screens.ScreenListening
import com.wgo.navigator.ui.screens.ScreenMapPicker
import com.wgo.navigator.ui.screens.ScreenModeSelection
import com.wgo.navigator.ui.screens.ScreenSettings
import com.wgo.navigator.ui.map.WgoMapConfig
import com.wgo.navigator.ui.theme.DaylightPalette
import com.wgo.navigator.ui.theme.NightPalette
import com.wgo.navigator.ui.theme.WgoTheme
import com.wgo.navigator.ui.screens.ScreenIntro
import com.wgo.navigator.core.permissions.PermissionBroker
import com.wgo.navigator.core.permissions.WgoPermission
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

enum class ScreenState {
    Intro, Listening, ModeSelection, Navigation, Settings, MapPicker
}

class MainActivity : ComponentActivity() {
    private val settingsRepository by lazy { SettingsRepository(applicationContext) }

    // Seeded from the one-time blocking read in onCreate (runs before the first frame).
    private lateinit var initialSettings: WgoSettings

    private val appViewModel: AppViewModel by viewModels()

    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModel.Factory(settingsRepository, initialSettings)
    }

    private val permissions by lazy { PermissionBroker.get(applicationContext) }

    // One launcher per permission, registered at construction, so a result delivered to a recreated Activity still
    // knows which permission it answers.
    private val permissionLaunchers = WgoPermission.entries.associateWith { permission ->
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissions.onResult(permission, granted, shouldShowRequestPermissionRationale(permission.manifest))
        }
    }

    /** Debug only: `am start -f 0x20000000 --ez wgo_replay_intro true` replays the intro for measurements. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_REPLAY_INTRO, false)) appViewModel.replayIntroForDebug()
    }

    private companion object {
        const val EXTRA_REPLAY_INTRO = "wgo_replay_intro"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Blocking read of a tiny DataStore file so theme and language are right on the first frame
        // (no flash between themes or languages). Cost: a few ms on the main thread at launch.
        initialSettings = runBlocking { settingsRepository.load() }
        val initialCanvas = (if (initialSettings.isDarkMode) NightPalette else DaylightPalette).canvas
        window.setBackgroundDrawable(ColorDrawable(initialCanvas.toArgb()))
        
        // Background warm-up while the intro plays (nothing here needs a permission): the haptic player with its
        // predefined effects, and osmdroid's configuration plus its tile cache database.
        val appContext = applicationContext
        val hapticPlayer = lifecycleScope.async(Dispatchers.Default) { HapticPlayer(appContext) }
        lifecycleScope.launch(Dispatchers.IO) { WgoMapConfig.warmUp(appContext) }

        // Haptics: ViewModels emit events, only the Activity touches the vibrator.
        lifecycleScope.launch {
            val player = hapticPlayer.await()
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                merge(appViewModel.haptics, settingsViewModel.haptics).collect { player.play(it) }
            }
        }

        // Lazy runtime permissions: features call PermissionBroker.ensure(); the system dialog is shown from here, never
        // while the intro plays. Statuses are refreshed on every start (grants made in the system settings).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WgoPermission.entries.forEach { permissions.refresh(it, shouldShowRequestPermissionRationale(it.manifest)) }
                permissions.requests.collect { permission ->
                    appViewModel.screen.first { it != ScreenState.Intro }
                    permissionLaunchers.getValue(permission).launch(permission.manifest)
                }
            }
        }

        // Debug-only resource counters (no-op in release): frames per screen and a WGO_METRICS line every 30 s.
        WgoMetrics.install(this, lifecycleScope)
        lifecycleScope.launch { appViewModel.screen.collect { WgoMetrics.screen = it.name } }

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
                    val introSkipped by appViewModel.introSkipped.collectAsState()
                    val reduceMotion = LocalWgoReduceMotion.current

                    // Back skips the intro and leaves mode selection, navigation and settings for the listening screen.
                    BackHandler(enabled = currentScreen != ScreenState.Listening) {
                        when (currentScreen) {
                            ScreenState.Intro -> appViewModel.skipIntro()
                            ScreenState.MapPicker -> appViewModel.backToSettings()
                            else -> appViewModel.backToListening()
                        }
                    }

                    Scaffold(
                        timeText = { 
                            // Only show time on certain screens if desired, or omit for pure UI focus
                        }
                    ) {
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = {
                                wgoScreenTransition(initialState, targetState, selectedMode, reduceMotion, introSkipped)
                            },
                            // Edge-only swipe-back on every screen = the same path as hardware Back.
                            modifier = Modifier
                                .fillMaxSize()
                                .wgoEdgeSwipeBack { onBackPressedDispatcher.onBackPressed() },
                            label = "screens"
                        ) { screen ->
                        when (screen) {
                            ScreenState.Intro -> {
                                ScreenIntro(
                                    onLockup = appViewModel::onIntroLockup,
                                    onFinished = appViewModel::finishIntro,
                                    onSkip = appViewModel::skipIntro,
                                    skipped = introSkipped
                                )
                            }
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
                                    onOpenMapPicker = appViewModel::openMapPicker
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

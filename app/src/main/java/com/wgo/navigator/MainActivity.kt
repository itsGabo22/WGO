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
import com.wgo.navigator.core.LocationProfile
import com.wgo.navigator.ui.components.openAppPermissionSettings
import com.wgo.navigator.core.permissions.PermissionBroker
import com.wgo.navigator.core.permissions.WgoPermission
import androidx.activity.result.contract.ActivityResultContracts
import android.content.ActivityNotFoundException
import com.wgo.navigator.ui.components.PermissionDenied
import com.wgo.navigator.ui.map.GeoCoord
import com.wgo.navigator.ui.map.PastoCentre
import com.wgo.navigator.ui.map.RadarProjection
import com.wgo.navigator.ui.minutesOf
import com.wgo.navigator.ui.screens.ScreenConfirmPlace
import com.wgo.navigator.ui.screens.ScreenError
import com.wgo.navigator.ui.screens.ScreenWorking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

enum class ScreenState {
    Intro, Listening, Working, ConfirmPlace, ModeSelection, Navigation, Error, Settings, MapPicker
}

/**
 * Rough per-mode minutes for the mode pads, from the straight-line distance (no extra route request): walking at
 * 1.25 m/s over a 1.3x detour, driving at 6.5 m/s (about 23 km/h in town) over a 1.4x detour plus one minute.
 */
private fun etaMinutes(from: GeoCoord?, to: GeoCoord): Pair<Int, Int> {
    val crow = RadarProjection.distanceMeters(from ?: PastoCentre, to)
    return minutesOf(crow * 1.3 / 1.25) to minutesOf(crow * 1.4 / 6.5 + 60)
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

    // Fallback voice strategy: the system speech activity, launched for a result on the ViewModel's request.
    private val speechDialogLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        appViewModel.voiceDialog.onResult(result.resultCode, result.data)
    }

    override fun onStop() {
        super.onStop()
        appViewModel.onAppStopped()
    }

    /** Debug only: `am start -f 0x20000000 --ez wgo_replay_intro true` replays the intro for measurements. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (!BuildConfig.DEBUG) return
        if (intent.getBooleanExtra(EXTRA_REPLAY_INTRO, false)) appViewModel.replayIntroForDebug()
        intent.getStringExtra(EXTRA_LOCATION_PROFILE)
            ?.let { name -> LocationProfile.entries.firstOrNull { it.name == name } }
            ?.let(appViewModel::debugSetLocationProfile)
    }

    private companion object {
        const val EXTRA_REPLAY_INTRO = "wgo_replay_intro"
        /** Debug only: `--es wgo_location_profile AMBIENT` forces a location profile for measurements. */
        const val EXTRA_LOCATION_PROFILE = "wgo_location_profile"
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

        // Voice: the interpreter uses the UI language; the system speech dialog (fallback) is launched from here.
        appViewModel.language = { settingsViewModel.language.value }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                appViewModel.voiceDialog.requests.collect { intent ->
                    try {
                        speechDialogLauncher.launch(intent)
                    } catch (e: ActivityNotFoundException) {
                        appViewModel.voiceDialog.onUnavailable()
                    }
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

                    val micDenied by appViewModel.micDenied.collectAsState()

                    // Back skips the intro and leaves mode selection, navigation and settings for the listening screen.
                    // On screen 01 it ends a voice session (or closes the microphone-denied surface) before leaving.
                    val listeningBusy = micState != MicState.Idle || micDenied
                    BackHandler(enabled = currentScreen != ScreenState.Listening || listeningBusy) {
                        when (currentScreen) {
                            ScreenState.Intro -> appViewModel.skipIntro()
                            ScreenState.MapPicker -> appViewModel.backToSettings()
                            ScreenState.Listening ->
                                if (micDenied) appViewModel.dismissMicDenied() else appViewModel.cancelListening()
                            ScreenState.Working, ScreenState.ConfirmPlace, ScreenState.ModeSelection, ScreenState.Error ->
                                appViewModel.backInPipeline()
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
                                val transcript by appViewModel.transcript.collectAsState()
                                val micPermission by appViewModel.micPermission.collectAsState()
                                if (micDenied) {
                                    PermissionDenied(
                                        permission = WgoPermission.MICROPHONE,
                                        status = micPermission,
                                        onRetry = appViewModel::onMicTapped,
                                        onOpenSettings = { openAppPermissionSettings() }
                                    )
                                } else {
                                    val level = appViewModel.voiceLevel
                                    ScreenListening(
                                        micState = micState,
                                        onMicClick = appViewModel::onMicTapped,
                                        onCancel = appViewModel::cancelListening,
                                        onOpenSettings = appViewModel::openSettings,
                                        onToggleLanguage = settingsViewModel::toggleLanguage,
                                        transcript = transcript,
                                        level = { level.value }
                                    )
                                }
                            }
                            ScreenState.Working -> {
                                val stage by appViewModel.stage.collectAsState()
                                val transcript by appViewModel.transcript.collectAsState()
                                val detail = if (stage == WorkStage.Routing) appViewModel.currentPlace?.name.orEmpty() else transcript
                                ScreenWorking(stage = stage, detail = detail, onCancel = appViewModel::backInPipeline)
                            }
                            ScreenState.ConfirmPlace -> {
                                val places by appViewModel.places.collectAsState()
                                val index by appViewModel.placeIndex.collectAsState()
                                val near by appViewModel.near.collectAsState()
                                places.getOrNull(index)?.let { place ->
                                    ScreenConfirmPlace(
                                        place = place,
                                        distanceM = near?.let { RadarProjection.distanceMeters(it, place.coord) },
                                        index = index,
                                        count = places.size,
                                        onGo = appViewModel::onGo,
                                        onOther = appViewModel::onOtherPlace
                                    )
                                }
                            }
                            ScreenState.ModeSelection -> {
                                val near by appViewModel.near.collectAsState()
                                val place = appViewModel.currentPlace
                                if (place != null) {
                                    val (walk, drive) = etaMinutes(near, place.coord)
                                    ScreenModeSelection(
                                        onSelectMode = appViewModel::selectMode,
                                        destination = place.name,
                                        address = place.address,
                                        walkMinutes = walk,
                                        driveMinutes = drive,
                                        pressedMode = selectedMode
                                    )
                                }
                            }
                            ScreenState.Error -> {
                                val error by appViewModel.error.collectAsState()
                                val locationPermission by appViewModel.locationPermission.collectAsState()
                                error?.let {
                                    ScreenError(
                                        error = it,
                                        onRetry = appViewModel::retry,
                                        permissionStatus = locationPermission,
                                        onOpenSettings = { openAppPermissionSettings() }
                                    )
                                }
                            }
                            ScreenState.Navigation -> {
                                val locationPermission by appViewModel.locationPermission.collectAsState()
                                val origin by appViewModel.origin.collectAsState()
                                ScreenActiveNavigation(
                                    onCancel = appViewModel::backToListening,
                                    mapStyleId = mapStyleId,
                                    locationSource = appViewModel.locationSource,
                                    travelMode = selectedMode,
                                    origin = origin,
                                    locationPermission = locationPermission,
                                    onRetryLocation = appViewModel::retryLocation,
                                    onOpenLocationSettings = { openAppPermissionSettings() }
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

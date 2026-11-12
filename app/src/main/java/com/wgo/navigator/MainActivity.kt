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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import androidx.wear.ambient.AmbientLifecycleObserver
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import com.wgo.navigator.ui.screens.AmbientInfo
import com.wgo.navigator.ui.screens.AmbientNavigationScreen
import com.wgo.navigator.ui.theme.Wgo
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
import com.wgo.navigator.ui.screens.ScreenArrived
import com.wgo.navigator.ui.screens.ArrivalSummary
import com.wgo.navigator.ui.screens.AmbientArrivalScreen
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
import android.os.SystemClock
import android.util.Base64
import com.wgo.navigator.voice.DebugUtterances
import com.wgo.navigator.ui.components.PermissionDenied
import com.wgo.navigator.ui.map.GeoCoord
import com.wgo.navigator.ui.map.PastoCentre
import com.wgo.navigator.geo.PlaceResolver
import com.wgo.navigator.ui.map.RadarProjection
import com.wgo.navigator.route.ModeEstimate
import com.wgo.navigator.ui.screens.ScreenConfirmPlace
import com.wgo.navigator.ui.screens.ScreenError
import com.wgo.navigator.ui.screens.ScreenPlaceNotFound
import com.wgo.navigator.ui.screens.ScreenVoicePicker
import com.wgo.navigator.ui.screens.ScreenCredits
import com.wgo.navigator.ui.screens.ScreenWorking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

enum class ScreenState {
    Intro, Listening, Working, ConfirmPlace, ModeSelection, Navigation, Arrived, Error, Settings, MapPicker, VoicePicker, Credits
}

/** Per-mode minutes for the mode pads from the straight-line distance (no route request; see [ModeEstimate]). */
private fun etaMinutes(from: GeoCoord?, to: GeoCoord): Pair<Int, Int> {
    val origin = from ?: PastoCentre
    return ModeEstimate.minutes(origin, to, TravelMode.Walk) to ModeEstimate.minutes(origin, to, TravelMode.Drive)
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
            val elapsed = SystemClock.elapsedRealtime() - (permissionAskedAt[permission] ?: 0L)
            WgoMetrics.event("permission_result ${permission.name} granted=$granted ms=$elapsed")
            permissions.onResult(permission, granted, shouldShowRequestPermissionRationale(permission.manifest))
        }
    }

    /** Non-null while the system is in ambient mode. */
    private val ambientInfo = MutableStateFlow<AmbientInfo?>(null)

    /** When each permission dialog was launched (debug timing only). */
    private val permissionAskedAt = HashMap<WgoPermission, Long>()

    // Fallback voice strategy: the system speech activity, launched for a result on the ViewModel's request.
    private val speechDialogLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        appViewModel.voiceDialog.onResult(result.resultCode, result.data)
    }

    override fun onStart() {
        super.onStart()
        appViewModel.onAppStarted()
    }

    override fun onStop() {
        super.onStop()
        appViewModel.onAppStopped()
    }

    /**
     * Test builds only (debug and benchmark): `am start -f 0x20000000 --ez wgo_replay_intro true` replays the intro, and
     * `--es wgo_utterance_b64 <UTF-8 Base64>` injects an utterance (the benchmark build has no adb receiver).
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (!BuildConfig.TEST_HOOKS) return
        if (intent.getBooleanExtra(EXTRA_REPLAY_INTRO, false)) appViewModel.replayIntroForDebug()
        intent.getStringExtra(EXTRA_UTTERANCE_B64)?.let { encoded ->
            runCatching { String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8) }.getOrNull()
                ?.let { DebugUtterances.inject(it, intent.getStringExtra(EXTRA_UTTERANCE_LANG)) }
        }
        if (!BuildConfig.DEBUG) return
        intent.getStringExtra(EXTRA_LOCATION_PROFILE)
            ?.let { name -> LocationProfile.entries.firstOrNull { it.name == name } }
            ?.let(appViewModel::debugSetLocationProfile)
    }

    private companion object {
        const val EXTRA_REPLAY_INTRO = "wgo_replay_intro"
        const val EXTRA_UTTERANCE_B64 = "wgo_utterance_b64"
        const val EXTRA_UTTERANCE_LANG = "wgo_lang"
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
                    permissionAskedAt[permission] = SystemClock.elapsedRealtime()
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

        // Ambient (always-on). The observer enables ambient for the whole Activity (the API has no per-screen switch):
        // during a trip the minimal ambient navigation screen is shown; on any other screen the app draws nothing (a
        // black screen, no loops) and the system returns to the watch face after its usual timeout.
        lifecycle.addObserver(AmbientLifecycleObserver(this, object : AmbientLifecycleObserver.AmbientLifecycleCallback {
            override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
                ambientInfo.value = AmbientInfo(ambientDetails.deviceHasLowBitAmbient, ambientDetails.burnInProtectionRequired)
                appViewModel.onAmbientChanged(true)
            }

            override fun onUpdateAmbient() = appViewModel.onAmbientTick()

            override fun onExitAmbient() {
                ambientInfo.value = null
                appViewModel.onAmbientChanged(false)
            }
        }))

        // Debug-only resource counters (no-op in release): frames per screen and a WGO_METRICS line every 30 s.
        // Frames drawn in ambient are counted under "Ambient" (the frame-loop check).
        WgoMetrics.install(this, lifecycleScope)
        lifecycleScope.launch {
            combine(appViewModel.screen, ambientInfo) { screen, ambient -> if (ambient != null) "Ambient" else screen.name }
                .collect { WgoMetrics.screen = it }
        }

        setContent {
            val isDarkMode by settingsViewModel.isDarkMode.collectAsState()
            val language by settingsViewModel.language.collectAsState()

            // Window background follows the in-app theme (not the system one): no white flash in Night.
            val canvas = (if (isDarkMode) NightPalette else DaylightPalette).canvas
            SideEffect { window.setBackgroundDrawable(ColorDrawable(canvas.toArgb())) }

            CompositionLocalProvider(LocalLanguage provides language) {
                WgoTheme(isDarkMode = isDarkMode) {
                    val ambient by ambientInfo.collectAsState()
                    val screenNow by appViewModel.screen.collectAsState()
                    // Ambient replaces the whole interactive tree (no frame loop, no map); leaving it rebuilds the
                    // current screen as it is now, with no screen transition or intro.
                    val info = ambient
                    if (info != null) {
                        if (screenNow == ScreenState.Navigation) {
                            val shown by appViewModel.ambientNavState.collectAsState()
                            AmbientNavigationScreen(shown, info = info)
                        } else if (screenNow == ScreenState.Arrived) {
                            val arrivedAt by appViewModel.destinationName.collectAsState()
                            AmbientArrivalScreen(arrivedAt)
                        } else {
                            Box(Modifier.fillMaxSize().background(Wgo.palette.ambient.canvas))
                        }
                        return@WgoTheme
                    }
                    val currentScreen by appViewModel.screen.collectAsState()
                    val micState by appViewModel.micState.collectAsState()
                    val selectedMode by appViewModel.selectedMode.collectAsState()
                    val mapStyleId by settingsViewModel.mapStyle.collectAsState()
                    val introSkipped by appViewModel.introSkipped.collectAsState()
                    val reduceMotion = LocalWgoReduceMotion.current

                    val micDenied by appViewModel.micDenied.collectAsState()

                    // Back skips the intro and leaves the pipeline one step at a time. During navigation it asks
                    // END NAVIGATION? (Back again = KEEP); on the arrival it goes home. On screen 01 it ends a voice
                    // session (or closes the microphone-denied surface) before leaving.
                    val listeningBusy = micState != MicState.Idle || micDenied
                    val endConfirm by appViewModel.endConfirm.collectAsState()
                    BackHandler(enabled = currentScreen != ScreenState.Listening || listeningBusy) {
                        when (currentScreen) {
                            ScreenState.Intro -> appViewModel.skipIntro()
                            ScreenState.MapPicker, ScreenState.VoicePicker, ScreenState.Credits -> appViewModel.backToSettings()
                            ScreenState.Listening ->
                                if (micDenied) appViewModel.dismissMicDenied() else appViewModel.cancelListening("back")
                            ScreenState.Working, ScreenState.ConfirmPlace, ScreenState.ModeSelection, ScreenState.Error ->
                                appViewModel.backInPipeline()
                            ScreenState.Navigation ->
                                if (endConfirm) appViewModel.keepNavigating() else appViewModel.requestEndNavigation()
                            ScreenState.Arrived -> appViewModel.finishArrival()
                            ScreenState.Settings -> appViewModel.backToListening()
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
                                        onMicPress = appViewModel::onMicTapped,
                                        onCancel = { appViewModel.cancelListening("chip") },
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
                                    // Far = over 25 km from the fix, or from the Pasto centre without one (then shown).
                                    val fromAnchor = RadarProjection.distanceMeters(near ?: PastoCentre, place.coord)
                                    val far = fromAnchor > PlaceResolver.FAR_KM * 1000
                                    ScreenConfirmPlace(
                                        place = place,
                                        distanceM = near?.let { RadarProjection.distanceMeters(it, place.coord) }
                                            ?: fromAnchor.takeIf { far },
                                        far = far,
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
                                val notFound by appViewModel.notFound.collectAsState()
                                val missing = notFound
                                if (error == PipelineError.PlaceNotFound && missing != null) {
                                    ScreenPlaceNotFound(
                                        name = missing.name,
                                        suggestions = missing.suggestions,
                                        onSuggestion = appViewModel::onNotFoundSuggestion,
                                        onSayStreet = appViewModel::onSayStreet
                                    )
                                } else error?.let {
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
                                val navState by appViewModel.navState.collectAsState()
                                val radar by appViewModel.radar.collectAsState()
                                val destination by appViewModel.destinationName.collectAsState()
                                ScreenActiveNavigation(
                                    onCancel = appViewModel::requestEndNavigation,
                                    mapStyleId = mapStyleId,
                                    navState = navState,
                                    radarSource = radar,
                                    driving = radar?.mode == TravelMode.Drive,
                                    destination = destination,
                                    endConfirm = endConfirm,
                                    onEnd = appViewModel::endNavigation,
                                    onKeep = appViewModel::keepNavigating,
                                    onRetryReroute = appViewModel::retryReroute,
                                    onRetryGps = appViewModel::retryGps,
                                    locationPermission = locationPermission,
                                    onRetryLocation = appViewModel::retryLocation,
                                    onOpenLocationSettings = { openAppPermissionSettings() }
                                )
                            }
                            ScreenState.Arrived -> {
                                val destination by appViewModel.destinationName.collectAsState()
                                val trip by appViewModel.tripSummary.collectAsState()
                                val tripLine = trip?.let { ArrivalSummary.text(it.distanceM, it.durationS, language) }
                                ScreenArrived(destination = destination, onFinish = appViewModel::finishArrival, summary = tripLine)
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
                                    onOpenMapPicker = appViewModel::openMapPicker,
                                    onOpenVoicePicker = appViewModel::openVoicePicker,
                                    onOpenCredits = appViewModel::openCredits
                                )
                            }
                            ScreenState.Credits -> ScreenCredits()
                            ScreenState.VoicePicker -> {
                                val voiceName by settingsViewModel.voiceName.collectAsState()
                                ScreenVoicePicker(
                                    language = language,
                                    selected = voiceName,
                                    onChoose = settingsViewModel::setVoiceName
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

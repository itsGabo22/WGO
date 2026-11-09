package com.wgo.navigator.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.wgo.navigator.Language
import com.wgo.navigator.PipelineError
import com.wgo.navigator.WorkStage
import com.wgo.navigator.core.Place
import com.wgo.navigator.core.permissions.PermissionStatus
import com.wgo.navigator.ui.LocalLanguage
import com.wgo.navigator.ui.components.WgoWatchPreviews
import com.wgo.navigator.ui.map.GeoCoord
import com.wgo.navigator.ui.theme.WgoTheme

private val SamplePlace = Place("Universidad de Nariño", "Acceso Universidad, Universitario", GeoCoord(1.23253, -77.29339))

@Composable
private fun Frame(dark: Boolean, language: Language, content: @Composable () -> Unit) {
    WgoTheme(isDarkMode = dark) { CompositionLocalProvider(LocalLanguage provides language, content = content) }
}

@Composable
private fun WorkingPreview(dark: Boolean, stage: WorkStage, language: Language = Language.ES) = Frame(dark, language) {
    ScreenWorking(stage = stage, detail = "llévame a la Universidad de Nariño a pie", onCancel = {})
}

@Composable
private fun ConfirmPreview(dark: Boolean, count: Int, language: Language = Language.ES) = Frame(dark, language) {
    ScreenConfirmPlace(SamplePlace, distanceM = 2_360.0, index = 0, count = count, onGo = {}, onOther = {})
}

@Composable
private fun ErrorPreview(dark: Boolean, error: PipelineError, language: Language = Language.ES) = Frame(dark, language) {
    ScreenError(error = error, onRetry = {}, permissionStatus = PermissionStatus.Denied)
}

@WgoWatchPreviews @Composable private fun WorkingInterpretingDaylight() = WorkingPreview(false, WorkStage.Interpreting)
@WgoWatchPreviews @Composable private fun WorkingResolvingNight() = WorkingPreview(true, WorkStage.Resolving)
@WgoWatchPreviews @Composable private fun WorkingRoutingEnDaylight() = WorkingPreview(false, WorkStage.Routing, Language.EN)
@WgoWatchPreviews @Composable private fun WorkingRoutingNight() = WorkingPreview(true, WorkStage.Routing)

@WgoWatchPreviews @Composable private fun ConfirmDaylight() = ConfirmPreview(false, 3)
@WgoWatchPreviews @Composable private fun ConfirmNight() = ConfirmPreview(true, 3)
@WgoWatchPreviews @Composable private fun ConfirmSingleEnNight() = ConfirmPreview(true, 1, Language.EN)

@WgoWatchPreviews @Composable private fun ErrorPlaceDaylight() = ErrorPreview(false, PipelineError.PlaceNotFound)
@WgoWatchPreviews @Composable private fun ErrorOfflineNight() = ErrorPreview(true, PipelineError.Offline)
@WgoWatchPreviews @Composable private fun ErrorRouteEnDaylight() = ErrorPreview(false, PipelineError.RouteUnavailable, Language.EN)
@WgoWatchPreviews @Composable private fun ErrorAiEnNight() = ErrorPreview(true, PipelineError.AiUnavailable, Language.EN)
@WgoWatchPreviews @Composable private fun ErrorVoiceDaylight() = ErrorPreview(false, PipelineError.VoiceUnavailable)
@WgoWatchPreviews @Composable private fun ErrorNotUnderstoodNight() = ErrorPreview(true, PipelineError.NotUnderstood)
@WgoWatchPreviews @Composable private fun ErrorLocationDaylight() = ErrorPreview(false, PipelineError.LocationPermission)

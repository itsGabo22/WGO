package com.wgo.navigator.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wgo.navigator.Language

/**
 * One preview per piece in Daylight and Night, so the whole kit can be reviewed in Android Studio.
 * Per-state previews live next to each piece.
 */
@Composable
private fun Gallery(dark: Boolean, piece: @Composable () -> Unit) = WgoPreviewFrame(dark) { piece() }

@Composable
private fun ButtonPiece() {
    Column(
        modifier = Modifier.padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
    ) {
        MassiveButton(onClick = {}, label = "Conducir", icon = WgoIcons.DirectionsCar, eta = "8 min")
        MassiveButton(onClick = {}, label = "Caminar", variant = MassiveButtonVariant.Secondary, icon = WgoIcons.DirectionsWalk)
    }
}

@Composable
private fun BadgePiece() = StatusBadge("Voice on", variant = StatusBadgeVariant.Soft, equalizer = true)

@Composable
private fun BezelPiece() = BezelGauge(progress = { 0.65f })

@Composable
private fun MicPiece() = MicOrb(state = MicState.Listening, onClick = {})

@Composable
private fun TurnPiece() = TurnCard(distanceMeters = 200, instruction = "Gira a la derecha en Calle 18")

@Composable
private fun TogglePiece() = SegmentedToggle(listOf("Claro", "Oscuro"), selectedIndex = 0, onSelect = {})

@Composable
private fun LanguagePiece() = LanguageSwitch(language = Language.ES, onToggle = {})

@WgoWatchPreviews @Composable private fun GalleryButtonDaylight() = Gallery(false) { ButtonPiece() }
@WgoWatchPreviews @Composable private fun GalleryButtonNight() = Gallery(true) { ButtonPiece() }
@WgoWatchPreviews @Composable private fun GalleryBadgeDaylight() = Gallery(false) { BadgePiece() }
@WgoWatchPreviews @Composable private fun GalleryBadgeNight() = Gallery(true) { BadgePiece() }
@WgoWatchPreviews @Composable private fun GalleryBezelDaylight() = Gallery(false) { BezelPiece() }
@WgoWatchPreviews @Composable private fun GalleryBezelNight() = Gallery(true) { BezelPiece() }
@WgoWatchPreviews @Composable private fun GalleryMicDaylight() = Gallery(false) { MicPiece() }
@WgoWatchPreviews @Composable private fun GalleryMicNight() = Gallery(true) { MicPiece() }
@WgoWatchPreviews @Composable private fun GalleryTurnDaylight() = Gallery(false) { TurnPiece() }
@WgoWatchPreviews @Composable private fun GalleryTurnNight() = Gallery(true) { TurnPiece() }
@WgoWatchPreviews @Composable private fun GalleryToggleDaylight() = Gallery(false) { TogglePiece() }
@WgoWatchPreviews @Composable private fun GalleryToggleNight() = Gallery(true) { TogglePiece() }
@WgoWatchPreviews @Composable private fun GalleryLanguageDaylight() = Gallery(false) { LanguagePiece() }
@WgoWatchPreviews @Composable private fun GalleryLanguageNight() = Gallery(true) { LanguagePiece() }

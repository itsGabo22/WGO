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

@WgoPreview @Composable private fun GalleryButtonDaylight() = Gallery(false) { ButtonPiece() }
@WgoPreview @Composable private fun GalleryButtonNight() = Gallery(true) { ButtonPiece() }
@WgoPreview @Composable private fun GalleryBadgeDaylight() = Gallery(false) { BadgePiece() }
@WgoPreview @Composable private fun GalleryBadgeNight() = Gallery(true) { BadgePiece() }
@WgoPreview @Composable private fun GalleryBezelDaylight() = Gallery(false) { BezelPiece() }
@WgoPreview @Composable private fun GalleryBezelNight() = Gallery(true) { BezelPiece() }
@WgoPreview @Composable private fun GalleryMicDaylight() = Gallery(false) { MicPiece() }
@WgoPreview @Composable private fun GalleryMicNight() = Gallery(true) { MicPiece() }
@WgoPreview @Composable private fun GalleryTurnDaylight() = Gallery(false) { TurnPiece() }
@WgoPreview @Composable private fun GalleryTurnNight() = Gallery(true) { TurnPiece() }
@WgoPreview @Composable private fun GalleryToggleDaylight() = Gallery(false) { TogglePiece() }
@WgoPreview @Composable private fun GalleryToggleNight() = Gallery(true) { TogglePiece() }
@WgoPreview @Composable private fun GalleryLanguageDaylight() = Gallery(false) { LanguagePiece() }
@WgoPreview @Composable private fun GalleryLanguageNight() = Gallery(true) { LanguagePiece() }

package com.wgo.navigator.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.wgo.navigator.ui.StringKey
import com.wgo.navigator.ui.map.RadarSchematic
import com.wgo.navigator.ui.mapStyleName
import com.wgo.navigator.ui.string
import com.wgo.navigator.ui.theme.LocalWgoReduceMotion
import com.wgo.navigator.ui.theme.MapStyleId
import com.wgo.navigator.ui.theme.Wgo
import com.wgo.navigator.ui.theme.WgoMotion
import com.wgo.navigator.ui.theme.WgoType
import com.wgo.navigator.ui.theme.wgoMapStyle
import com.wgo.navigator.ui.theme.wgoMinTouch

/**
 * Settings row that opens the map picker: 132 x 44 pill (`surface` + `edge-control`), a 34 dp round schematic thumbnail
 * of the active style at 5 dp inset, a text column ("MAPA" over the style name) and a chevron. The thumbnail pops when
 * the style changes. The whole row is the touch target (48 real dp minimum height).
 */
@Composable
fun MapRow(styleId: MapStyleId, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = Wgo.palette
    val reduceMotion = LocalWgoReduceMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val background = if (pressed) palette.ink else palette.surface
    val primaryText = if (pressed) palette.inkInverse else palette.ink
    val mutedText = if (pressed) palette.inkInverse else palette.inkMuted

    val pop = remember { Animatable(1f) }
    LaunchedEffect(styleId) {
        if (!reduceMotion) {
            pop.snapTo(0.6f)
            pop.animateTo(1f, WgoMotion.pop())
        }
    }
    val mapLabel = string(StringKey.MAP_LABEL)
    val name = mapStyleName(styleId)

    Box(
        modifier = modifier
            .width(132.dp)
            .wgoMinTouch(height = 48.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$mapLabel: $name" }
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .size(132.dp, 44.dp)
                .clip(CircleShape)
                .background(background)
                .border(1.5.dp, palette.ink, CircleShape)
                .padding(start = 5.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            RadarSchematic(
                style = wgoMapStyle(styleId, palette),
                live = false,
                modifier = Modifier
                    .size(34.dp)
                    .graphicsLayer {
                        val s = pop.value
                        scaleX = s
                        scaleY = s
                    }
                    .clip(CircleShape)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mapLabel.uppercase(),
                    color = mutedText,
                    style = WgoType.labelSm.copy(fontSize = 9.sp, letterSpacing = 0.12.em),
                    maxLines = 1
                )
                Text(
                    text = name.uppercase(),
                    color = primaryText,
                    style = WgoType.labelMd,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(imageVector = WgoIcons.ChevronRight, contentDescription = null, tint = primaryText, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun MapRowPreview(dark: Boolean, id: MapStyleId) {
    WgoPreviewFrame(dark) { MapRow(styleId = id, onClick = {}) }
}

@WgoPreview
@Composable
private fun MapRowDaylight() = MapRowPreview(false, MapStyleId.COBALTO)

@WgoPreview
@Composable
private fun MapRowNight() = MapRowPreview(true, MapStyleId.NEON)

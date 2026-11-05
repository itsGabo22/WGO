package com.wgo.navigator.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Text
import com.wgo.navigator.ui.theme.LocalWgoReduceMotion
import com.wgo.navigator.ui.theme.Wgo
import com.wgo.navigator.ui.theme.WgoMotion
import com.wgo.navigator.ui.theme.WgoType
import com.wgo.navigator.ui.theme.orCut
import kotlin.math.roundToInt

private val PillWidth = 156.dp
private val PillHeight = 40.dp
private val HitHeight = 48.dp
private val KnobWidth = 75.dp
private val KnobHeight = 34.dp
private val KnobInset = 3.dp

/**
 * Two-option pill: 156 x 40 dp visual (48 dp touch height), knob 75 x 34 dp that slides with a spring.
 * The label color flips at mid-travel. Exposed as a selectable group (radio buttons) so TalkBack announces
 * the selected option.
 */
@Composable
fun SegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    require(options.size == 2) { "SegmentedToggle supports exactly two options" }
    val palette = Wgo.palette
    val reduceMotion = LocalWgoReduceMotion.current

    val position by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = WgoMotion.base<Float>().orCut(reduceMotion),
        label = "segmentedKnob"
    )
    // Only invalidates when the rounded position flips (mid-travel), not on every frame.
    val activeIndex by remember { derivedStateOf { position.roundToInt().coerceIn(0, 1) } }

    Box(
        modifier = modifier
            .size(PillWidth, HitHeight)
            .selectableGroup(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(PillWidth, PillHeight)
                .clip(CircleShape)
                .background(palette.surface)
                .border(1.5.dp, palette.ink, CircleShape)
        ) {
            Box(
                modifier = Modifier
                    .padding(start = KnobInset, top = KnobInset)
                    .size(KnobWidth, KnobHeight)
                    .graphicsLayer { translationX = position * KnobWidth.toPx() }
                    .clip(CircleShape)
                    .background(palette.primary)
            )
        }
        Row(modifier = Modifier.size(PillWidth, HitHeight).padding(horizontal = KnobInset)) {
            options.forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .width(KnobWidth)
                        .fillMaxHeight()
                        .selectable(
                            selected = selectedIndex == index,
                            role = Role.RadioButton,
                            onClick = { onSelect(index) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label.uppercase(),
                        style = WgoType.labelMd,
                        color = if (activeIndex == index) palette.onPrimary else palette.ink,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun TogglePreview(dark: Boolean, selected: Int) {
    WgoPreviewFrame(dark) {
        SegmentedToggle(options = listOf("Claro", "Oscuro"), selectedIndex = selected, onSelect = {})
    }
}

@WgoPreview
@Composable
private fun SegmentedToggleFirstDaylight() = TogglePreview(false, 0)

@WgoPreview
@Composable
private fun SegmentedToggleSecondDaylight() = TogglePreview(false, 1)

@WgoPreview
@Composable
private fun SegmentedToggleFirstNight() = TogglePreview(true, 0)

@WgoPreview
@Composable
private fun SegmentedToggleSecondNight() = TogglePreview(true, 1)

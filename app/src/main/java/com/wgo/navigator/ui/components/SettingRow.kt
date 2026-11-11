package com.wgo.navigator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.wgo.navigator.ui.theme.Wgo
import com.wgo.navigator.ui.theme.WgoType
import com.wgo.navigator.ui.theme.wgoMinTouch

/**
 * Settings row (spec section 7, P7): 156 x 44 pill, `surface` + `edge-control`, a small `inkMuted` label (9 sp bold,
 * tracking .12 em) over the value (12 sp bold), an optional chevron when it opens a screen. Pressing inverts instantly.
 * The whole row is the touch target (48 real dp, capped by [maxTouchHeight]). [state] is announced as the state
 * (cycling rows); otherwise the value is part of the description.
 */
@Composable
fun SettingRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    opens: Boolean = false,
    state: Boolean = false,
    maxTouchHeight: Dp = Dp.Unspecified
) {
    val palette = Wgo.palette
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val background = if (pressed) palette.ink else palette.surface
    val primaryText = if (pressed) palette.inkInverse else palette.ink
    val mutedText = if (pressed) palette.inkInverse else palette.inkMuted
    Box(
        modifier = modifier
            .width(156.dp)
            .then(if (maxTouchHeight.isSpecified) Modifier.heightIn(max = maxTouchHeight) else Modifier)
            .wgoMinTouch(height = 48.dp)
            .semantics(mergeDescendants = true) {
                if (state) {
                    contentDescription = label
                    stateDescription = value
                } else {
                    contentDescription = "$label: $value"
                }
            }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .size(156.dp, 44.dp)
                .clip(CircleShape)
                .background(background)
                .border(1.5.dp, palette.ink, CircleShape)
                .padding(start = 18.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label.uppercase(),
                    color = mutedText,
                    style = WgoType.labelSm.copy(fontSize = 9.sp, letterSpacing = 0.12.em),
                    maxLines = 1
                )
                Text(
                    text = value,
                    color = primaryText,
                    style = WgoType.labelMd.copy(fontSize = 12.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (opens) {
                Icon(WgoIcons.ChevronRight, contentDescription = null, tint = primaryText, modifier = Modifier.size(16.dp))
            }
        }
    }
}

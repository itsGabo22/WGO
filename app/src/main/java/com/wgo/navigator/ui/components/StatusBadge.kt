package com.wgo.navigator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Text
import com.wgo.navigator.ui.theme.ClockRate
import com.wgo.navigator.ui.theme.Wgo
import com.wgo.navigator.ui.theme.WgoClock
import com.wgo.navigator.ui.theme.WgoType
import com.wgo.navigator.ui.theme.rememberWgoClock

enum class StatusBadgeVariant {
    Neutral, Soft, Primary, Go, Alert
}

private val BadgeHeight = 18.dp
private val BarWidth = 2.dp
private val BarMaxHeight = 10.dp
private val BarPeriodsMs = intArrayOf(500, 700, 900)

/**
 * 18 dp pill with a `label-sm` uppercase [text]. With [equalizer] (the VOICE ON badge) it adds a
 * 3-bar equalizer driven by the shared [clock] (or its own when none is given).
 */
@Composable
fun StatusBadge(
    text: String,
    modifier: Modifier = Modifier,
    variant: StatusBadgeVariant = StatusBadgeVariant.Primary,
    equalizer: Boolean = false,
    clock: WgoClock? = null
) {
    val palette = Wgo.palette
    val background = when (variant) {
        StatusBadgeVariant.Neutral, StatusBadgeVariant.Soft -> palette.surface
        StatusBadgeVariant.Primary -> palette.primary
        StatusBadgeVariant.Go -> palette.go
        StatusBadgeVariant.Alert -> palette.alert
    }
    val content = when (variant) {
        StatusBadgeVariant.Neutral -> palette.ink
        StatusBadgeVariant.Soft -> palette.primaryInk
        StatusBadgeVariant.Primary -> palette.onPrimary
        StatusBadgeVariant.Go -> palette.onGo
        StatusBadgeVariant.Alert -> palette.onAlert
    }

    Row(
        modifier = modifier
            .height(BadgeHeight)
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (equalizer) {
            Equalizer(color = content, clock = clock ?: rememberWgoClock())
        }
        Text(text = text.uppercase(), color = content, style = WgoType.labelSm, maxLines = 1)
    }
}

@Composable
private fun Equalizer(color: androidx.compose.ui.graphics.Color, clock: WgoClock) {
    clock.Use(ClockRate.Ambience)
    Canvas(modifier = Modifier.size(width = 12.dp, height = BarMaxHeight)) {
        val w = BarWidth.toPx()
        val maxH = BarMaxHeight.toPx()
        val gap = (size.width - 3 * w) / 2f
        BarPeriodsMs.forEachIndexed { i, period ->
            val h = maxH * (0.3f + 0.7f * clock.pingPong(period))
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (w + gap), (maxH - h) / 2f),
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2f)
            )
        }
    }
}

@Composable
private fun BadgesPreview(dark: Boolean) {
    WgoPreviewFrame(dark) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StatusBadge("WGO • NAV", variant = StatusBadgeVariant.Neutral)
            StatusBadge("Soft", variant = StatusBadgeVariant.Soft)
            StatusBadge("Primary", variant = StatusBadgeVariant.Primary)
            StatusBadge("Go", variant = StatusBadgeVariant.Go)
            StatusBadge("En pausa", variant = StatusBadgeVariant.Alert)
            StatusBadge("Voice on", variant = StatusBadgeVariant.Soft, equalizer = true)
        }
    }
}

@WgoPreview
@Composable
private fun StatusBadgeDaylight() = BadgesPreview(false)

@WgoPreview
@Composable
private fun StatusBadgeNight() = BadgesPreview(true)

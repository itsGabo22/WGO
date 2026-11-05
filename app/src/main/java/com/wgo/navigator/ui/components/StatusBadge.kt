package com.wgo.navigator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.wgo.navigator.ui.theme.Wgo

enum class StatusBadgeVariant {
    Primary, Neutral
}

@Composable
fun StatusBadge(
    text: String,
    modifier: Modifier = Modifier,
    variant: StatusBadgeVariant = StatusBadgeVariant.Primary
) {
    val palette = Wgo.palette
    val backgroundColor = when (variant) {
        StatusBadgeVariant.Primary -> palette.primaryInk.copy(alpha = 0.1f)
        StatusBadgeVariant.Neutral -> palette.ink.copy(alpha = 0.05f)
    }

    val contentColor = when (variant) {
        StatusBadgeVariant.Primary -> palette.primaryInk
        StatusBadgeVariant.Neutral -> palette.ink
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.caption2.copy(fontWeight = FontWeight.Bold)
        )
    }
}

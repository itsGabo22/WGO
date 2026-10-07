package com.wgo.navigator.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.wgo.navigator.ui.components.StatusBadge
import com.wgo.navigator.ui.components.StatusBadgeVariant
import com.wgo.navigator.ui.theme.WgoBlack
import com.wgo.navigator.ui.theme.WgoPrimary
import com.wgo.navigator.ui.theme.WgoWhite

@Composable
fun ScreenListening(
    isListening: Boolean,
    onToggleListen: () -> Unit,
    onCancel: () -> Unit
) {
    val statusText = if (isListening) "TRAZANDO RUTA..." else "¿A dónde vamos?"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WgoWhite)
            .padding(top = 24.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // TOP ZONE
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(top = 4.dp)
        ) {
            StatusBadge(text = "WGO • NAV", variant = StatusBadgeVariant.Neutral)
            Spacer(modifier = Modifier.height(4.dp))
            if (isListening) {
                Text(
                    text = statusText,
                    color = WgoPrimary,
                    style = MaterialTheme.typography.caption1.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = statusText,
                    color = WgoBlack,
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }

        // CENTER ZONE (Mic)
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center
        ) {
            PrimaryMicButton(
                isListening = isListening,
                onClick = onToggleListen
            )
        }

        // BOTTOM ZONE
        Box(
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Chip(
                onClick = onCancel,
                colors = ChipDefaults.chipColors(
                    backgroundColor = androidx.compose.ui.graphics.Color(0xFFF2F4F7),
                    contentColor = WgoBlack
                ),
                label = {
                    Text(
                        text = "CANCELAR",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(40.dp)
            )
        }
    }
}

@Composable
fun PrimaryMicButton(
    isListening: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "micAnimation")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.2f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micScale"
    )

    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(WgoPrimary.copy(alpha = if (isListening) 0.2f else 0.1f))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Activar micrófono" },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp * scale)
                .clip(CircleShape)
                .background(WgoPrimary),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Mic,
                contentDescription = null,
                tint = WgoWhite,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

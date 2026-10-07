package com.wgo.navigator.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.wgo.navigator.ui.StringKey
import com.wgo.navigator.ui.components.StatusBadge
import com.wgo.navigator.ui.components.StatusBadgeVariant
import com.wgo.navigator.ui.string
import com.wgo.navigator.ui.theme.WgoBlack
import com.wgo.navigator.ui.theme.WgoPrimary
import com.wgo.navigator.ui.theme.WgoWhite

@Composable
fun ScreenListening(
    isListening: Boolean,
    onToggleListen: () -> Unit,
    onCancel: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val statusText = if (isListening) string(StringKey.TITLE_ROUTING) else string(StringKey.TITLE_WHERE_TO)

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 28.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
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
                        color = MaterialTheme.colors.onBackground,
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
                val isLight = MaterialTheme.colors.background == WgoWhite
                Chip(
                    onClick = onCancel,
                    colors = ChipDefaults.chipColors(
                        backgroundColor = if (isLight) androidx.compose.ui.graphics.Color(0xFFF2F4F7) else androidx.compose.ui.graphics.Color(0xFF1E1E1E),
                        contentColor = MaterialTheme.colors.onBackground
                    ),
                    label = {
                        Text(
                            text = string(StringKey.BTN_CANCEL),
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
        
        // Settings Entry Point
        Button(
            onClick = onOpenSettings,
            colors = ButtonDefaults.buttonColors(
                backgroundColor = androidx.compose.ui.graphics.Color.Transparent,
                contentColor = MaterialTheme.colors.onBackground
            ),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp).size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = string(StringKey.SETTINGS),
                modifier = Modifier.size(24.dp)
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
    
    // Ring 1 animation
    val ring1Scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring1Scale"
    )
    val ring1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring1Alpha"
    )

    // Ring 2 animation (staggered by initialValue/targetValue manipulation or by using delayed start, but simple way is offsetting if possible. Actually tween doesn't support delay inside infiniteRepeatable in older versions smoothly without custom logic, so let's use a single infinite transition but offset the values if needed. Wait, in Compose we can use multiple animations with `StartOffset`!)

    val ring2Scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(1000)
        ),
        label = "ring2Scale"
    )
    val ring2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(1000)
        ),
        label = "ring2Alpha"
    )

    // Main mic button breathing scale
    val micScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micScale"
    )

    Box(
        modifier = Modifier
            .size(120.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Activar micrófono" },
        contentAlignment = Alignment.Center
    ) {
        if (isListening) {
            // Ring 1
            Box(
                modifier = Modifier
                    .size(64.dp * ring1Scale)
                    .clip(CircleShape)
                    .background(WgoPrimary.copy(alpha = ring1Alpha))
            )
            // Ring 2
            Box(
                modifier = Modifier
                    .size(64.dp * ring2Scale)
                    .clip(CircleShape)
                    .background(WgoPrimary.copy(alpha = ring2Alpha))
            )
        }

        // Microphone Button
        Box(
            modifier = Modifier
                .size(64.dp * (if (isListening) micScale else 1f))
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

package com.wgo.navigator.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.wgo.navigator.ui.StringKey
import com.wgo.navigator.ui.string
import com.wgo.navigator.ui.theme.WgoBlack
import com.wgo.navigator.ui.theme.WgoPrimary
import com.wgo.navigator.ui.theme.WgoWhite

@Composable
fun ScreenActiveNavigation(
    onCancel: () -> Unit
) {
    var zoomLevel by remember { mutableFloatStateOf(1f) }
    val maxZoom = 2.5f
    val minZoom = 0.75f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
    ) {
        // Z-Layer 0: MAP (Abstract environment)
        WgoMapPlaceholder(zoomLevel = zoomLevel)

        // Z-Layer 2: TOP NAVIGATION HUD (Floating card)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp)
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colors.surface.copy(alpha = 0.90f))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowUpward,
                        contentDescription = string(StringKey.GO_STRAIGHT),
                        tint = WgoPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "200m",
                        color = MaterialTheme.colors.onSurface,
                        style = MaterialTheme.typography.title3.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Text(
                    text = string(StringKey.NAV_INSTRUCTION),
                    color = MaterialTheme.colors.onSurface,
                    style = MaterialTheme.typography.caption2.copy(fontWeight = FontWeight.Medium),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Z-Layer 2.5: ZOOM CONTROLS
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { zoomLevel = (zoomLevel + 0.25f).coerceAtMost(maxZoom) },
                modifier = Modifier.size(36.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colors.onSurface
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = string(StringKey.ZOOM_IN),
                    modifier = Modifier.size(20.dp)
                )
            }

            Button(
                onClick = { zoomLevel = (zoomLevel - 0.25f).coerceAtLeast(minZoom) },
                modifier = Modifier.size(36.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colors.onSurface
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Remove,
                    contentDescription = string(StringKey.ZOOM_OUT),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Z-Layer 3: BOTTOM ACTION HUD
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        ) {
            val isLight = MaterialTheme.colors.background == WgoWhite
            Chip(
                onClick = onCancel,
                colors = ChipDefaults.chipColors(
                    backgroundColor = if (isLight) Color(0xFFF2F4F7) else Color(0xFF1E1E1E),
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
}

@Composable
fun WgoMapPlaceholder(zoomLevel: Float) {
    val isLight = MaterialTheme.colors.background == WgoWhite
    Canvas(modifier = Modifier.fillMaxSize()) {
        scale(scale = zoomLevel, pivot = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.6f)) {
            // Draw secondary abstract street grid
            val streetColor = if (isLight) Color.LightGray.copy(alpha = 0.5f) else Color.DarkGray.copy(alpha = 0.5f)
            
            // Horizontal streets
            drawLine(
                color = streetColor,
                start = androidx.compose.ui.geometry.Offset(0f, size.height * 0.3f),
                end = androidx.compose.ui.geometry.Offset(size.width, size.height * 0.3f),
                strokeWidth = 12f
            )
            drawLine(
                color = streetColor,
                start = androidx.compose.ui.geometry.Offset(0f, size.height * 0.7f),
                end = androidx.compose.ui.geometry.Offset(size.width, size.height * 0.7f),
                strokeWidth = 16f
            )
            
            // Vertical streets
            drawLine(
                color = streetColor,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.2f, 0f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height),
                strokeWidth = 12f
            )
            drawLine(
                color = streetColor,
                start = androidx.compose.ui.geometry.Offset(size.width * 0.8f, 0f),
                end = androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height),
                strokeWidth = 12f
            )

            // Draw active navigation route (Primary Blue)
            val routePath = Path().apply {
                moveTo(size.width * 0.5f, size.height * 0.8f) // Start near bottom
                lineTo(size.width * 0.5f, size.height * 0.5f) // Up to middle
                lineTo(size.width * 0.8f, size.height * 0.5f) // Right turn
                lineTo(size.width * 0.8f, size.height * 0.3f) // Up
            }

            drawPath(
                path = routePath,
                color = WgoPrimary,
                style = Stroke(
                    width = 24f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Current Location Marker (Pulse / Halo + Dot)
            val currentLocX = size.width * 0.5f
            val currentLocY = size.height * 0.8f
            
            drawCircle(
                color = WgoPrimary.copy(alpha = 0.2f),
                radius = 24f,
                center = androidx.compose.ui.geometry.Offset(currentLocX, currentLocY)
            )
            drawCircle(
                color = Color.White,
                radius = 12f,
                center = androidx.compose.ui.geometry.Offset(currentLocX, currentLocY)
            )
            drawCircle(
                color = WgoPrimary,
                radius = 8f,
                center = androidx.compose.ui.geometry.Offset(currentLocX, currentLocY)
            )
        }
    }
}

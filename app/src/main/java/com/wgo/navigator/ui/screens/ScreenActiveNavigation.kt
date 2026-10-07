package com.wgo.navigator.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.wgo.navigator.ui.theme.WgoBlack
import com.wgo.navigator.ui.theme.WgoPrimary
import com.wgo.navigator.ui.theme.WgoWhite

@Composable
fun ScreenActiveNavigation(
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WgoWhite)
            .padding(top = 28.dp, bottom = 12.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // TOP REGION
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 70.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowUpward,
                    contentDescription = "Continuar recto",
                    tint = WgoPrimary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "200m",
                    color = WgoBlack,
                    style = MaterialTheme.typography.display3
                )
            }
            Text(
                text = "Gira a la derecha en Calle 18",
                color = WgoBlack,
                style = MaterialTheme.typography.caption1.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.85f)
            )
        }

        // CENTER REGION (Minimap)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Ensure the path draws within a smaller bounded safe area inside the canvas
                val startY = size.height * 0.9f
                val midY = size.height * 0.4f
                val endX = size.width * 0.8f

                val path = Path().apply {
                    moveTo(size.width * 0.5f, startY)
                    lineTo(size.width * 0.5f, midY)
                    lineTo(endX, midY)
                }

                // Trailed path (past)
                drawPath(
                    path = Path().apply {
                        moveTo(size.width * 0.5f, startY)
                        lineTo(size.width * 0.5f, startY * 0.8f)
                    },
                    color = WgoBlack.copy(alpha = 0.1f),
                    style = Stroke(
                        width = 28f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Active path
                drawPath(
                    path = path,
                    color = WgoPrimary,
                    style = Stroke(
                        width = 28f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }

        // BOTTOM REGION
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

package com.wgo.navigator.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.wgo.navigator.ui.components.MassiveButton
import com.wgo.navigator.ui.components.MassiveButtonVariant
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
            .padding(vertical = 12.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // TOP REGION
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "↑",
                    color = WgoPrimary,
                    style = MaterialTheme.typography.display3
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
                style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }

        // CENTER REGION (Minimap)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val path = Path().apply {
                    moveTo(size.width * 0.5f, size.height)
                    lineTo(size.width * 0.5f, size.height * 0.5f)
                    lineTo(size.width * 0.9f, size.height * 0.5f)
                }

                // Trailed path (past)
                drawPath(
                    path = Path().apply {
                        moveTo(size.width * 0.5f, size.height)
                        lineTo(size.width * 0.5f, size.height * 0.8f)
                    },
                    color = WgoBlack.copy(alpha = 0.1f),
                    style = Stroke(
                        width = 24f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )

                // Active path
                drawPath(
                    path = path,
                    color = WgoPrimary,
                    style = Stroke(
                        width = 24f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }

        // BOTTOM REGION
        Box(
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            MassiveButton(
                onClick = onCancel,
                variant = MassiveButtonVariant.Secondary
            ) {
                Text(text = "CANCELAR")
            }
        }
    }
}

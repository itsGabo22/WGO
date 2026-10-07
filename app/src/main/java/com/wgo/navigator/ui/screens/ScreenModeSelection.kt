package com.wgo.navigator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.wgo.navigator.ui.components.MassiveButton
import com.wgo.navigator.ui.components.MassiveButtonVariant
import com.wgo.navigator.ui.components.StatusBadge
import com.wgo.navigator.ui.components.StatusBadgeVariant
import com.wgo.navigator.ui.theme.WgoBlack
import com.wgo.navigator.ui.theme.WgoWhite

@Composable
fun ScreenModeSelection(
    destination: String = "Centro Histórico",
    onSelectMode: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WgoWhite)
            .padding(vertical = 12.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // TOP ZONE
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(
                text = destination,
                color = WgoBlack,
                style = MaterialTheme.typography.title2,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            StatusBadge(text = "VOICE ON", variant = StatusBadgeVariant.Primary)
        }

        // ACTION ZONE
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            MassiveButton(
                onClick = { onSelectMode("drive") },
                variant = MassiveButtonVariant.Primary
            ) {
                Text(text = "CONDUCIR")
            }

            MassiveButton(
                onClick = { onSelectMode("walk") },
                variant = MassiveButtonVariant.Inverted
            ) {
                Text(text = "CAMINAR")
            }
        }
    }
}

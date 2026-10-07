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
            .padding(top = 28.dp, bottom = 20.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // TOP ZONE
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Text(
                text = destination,
                color = WgoBlack,
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(0.8f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            StatusBadge(text = "VOICE ON", variant = StatusBadgeVariant.Primary)
        }

        Spacer(modifier = Modifier.weight(1f))

        // ACTION ZONE
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        ) {
            MassiveButton(
                onClick = { onSelectMode("drive") },
                variant = MassiveButtonVariant.Primary
            ) {
                Text(text = "CONDUCIR", style = MaterialTheme.typography.button)
            }

            MassiveButton(
                onClick = { onSelectMode("walk") },
                variant = MassiveButtonVariant.Inverted
            ) {
                Text(text = "CAMINAR", style = MaterialTheme.typography.button)
            }
        }
    }
}

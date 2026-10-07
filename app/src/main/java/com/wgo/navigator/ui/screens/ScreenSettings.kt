package com.wgo.navigator.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import com.wgo.navigator.Language
import com.wgo.navigator.SettingsViewModel
import com.wgo.navigator.ui.StringKey
import com.wgo.navigator.ui.string
import com.wgo.navigator.ui.theme.WgoPrimary
import com.wgo.navigator.ui.theme.WgoWhite

@Composable
fun ScreenSettings(
    viewModel: SettingsViewModel,
    onClose: () -> Unit
) {
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val language by viewModel.language.collectAsState()

    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        contentPadding = PaddingValues(
            top = 32.dp,
            bottom = 48.dp,
            start = 16.dp,
            end = 16.dp
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = string(StringKey.SETTINGS),
                color = MaterialTheme.colors.onBackground,
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        item {
            ToggleChip(
                checked = isDarkMode,
                onCheckedChange = { viewModel.toggleTheme() },
                label = { Text(string(StringKey.THEME)) },
                secondaryLabel = { Text(if (isDarkMode) string(StringKey.DARK) else string(StringKey.LIGHT)) },
                toggleControl = {
                    Icon(
                        imageVector = if (isDarkMode) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                },
                colors = ToggleChipDefaults.toggleChipColors(
                    checkedStartBackgroundColor = WgoPrimary.copy(alpha = 0.3f),
                    checkedEndBackgroundColor = WgoPrimary.copy(alpha = 0.1f),
                    checkedToggleControlColor = WgoPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            val isEnglish = language == Language.EN
            ToggleChip(
                checked = isEnglish,
                onCheckedChange = {
                    viewModel.setLanguage(if (it) Language.EN else Language.ES)
                },
                label = { Text(string(StringKey.LANGUAGE)) },
                secondaryLabel = { Text(if (isEnglish) string(StringKey.ENGLISH) else string(StringKey.SPANISH)) },
                toggleControl = {
                    Icon(
                        imageVector = Icons.Rounded.Language,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                },
                colors = ToggleChipDefaults.toggleChipColors(
                    checkedStartBackgroundColor = WgoPrimary.copy(alpha = 0.3f),
                    checkedEndBackgroundColor = WgoPrimary.copy(alpha = 0.1f),
                    checkedToggleControlColor = WgoPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            val isLight = MaterialTheme.colors.background == WgoWhite
            androidx.wear.compose.material.Chip(
                onClick = onClose,
                colors = androidx.wear.compose.material.ChipDefaults.chipColors(
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
                    .fillMaxWidth(0.8f)
                    .height(40.dp)
            )
        }
    }
}

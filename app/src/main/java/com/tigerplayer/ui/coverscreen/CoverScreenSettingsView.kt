package com.tigerplayer.ui.coverscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tigerplayer.data.local.SkipShortAudio
import com.tigerplayer.data.local.ThemeMode
import com.tigerplayer.ui.settings.SettingsViewModel
import com.tigerplayer.ui.theme.TigerCyberCyan
import com.tigerplayer.ui.theme.TigerNeonOrange
import com.tigerplayer.ui.theme.glassEffect

@Composable
fun CoverScreenSettingsView(
    viewModel: SettingsViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(onClick = onClose)
            .testTag("CoverScreenSettingsView")
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(8.dp)
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                .glassEffect(RoundedCornerShape(24.dp)),
            color = Color(0xFF17181D).copy(alpha = 0.85f),
            shape = RoundedCornerShape(24.dp),
            contentColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "App Preferences",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TigerCyberCyan
                    )
                    IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close Settings", tint = Color.White)
                    }
                }

                // Theme Mode Section
                Text(
                    text = "Theme Appearance",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeMode.entries.forEach { mode ->
                        val selected = settingsState.themeMode == mode
                        AssistChip(
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(mode.name) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (selected) TigerNeonOrange.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.08f),
                                labelColor = Color.White
                            )
                        )
                    }
                }

                // Skip Short Audio Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Skip Short Audio",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Ignore voice notes and short audio clips",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = settingsState.skipShortAudio != SkipShortAudio.OFF,
                        onCheckedChange = { enabled ->
                            viewModel.setSkipShortAudio(
                                if (enabled) SkipShortAudio.BELOW_30_SECONDS
                                else SkipShortAudio.OFF
                            )
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TigerNeonOrange,
                            checkedTrackColor = TigerNeonOrange.copy(alpha = 0.35f)
                        )
                    )
                }

                // Rescan Library Button
                FilledTonalButton(
                    onClick = { viewModel.triggerLibraryRescan() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Rescan Local Music Library")
                }
            }
        }
    }
}

package com.example.tigerplayer.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tigerplayer.data.local.DefaultPlayerView
import com.example.tigerplayer.data.local.SkipShortAudio
import com.example.tigerplayer.data.local.TigerAccentStyle
import com.example.tigerplayer.ui.theme.TigerCyberCyan
import com.example.tigerplayer.ui.theme.TigerNeonOrange
import com.example.tigerplayer.ui.theme.TigerSpectralViolet
import com.example.tigerplayer.ui.theme.TigerToxicLime
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.tigerGlow
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val rescan by viewModel.libraryRescanState.collectAsStateWithLifecycle()
    val accent = accentColor(settings.accentStyle)
    var crossfadeSlider by remember(settings.crossfadeDurationSec) {
        mutableFloatStateOf(settings.crossfadeDurationSec.toFloat())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = "CONTROL MATRIX",
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.6.sp
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MatrixSection(title = "Appearance", icon = Icons.Rounded.Palette, accent = accent) {
                ListItem(
                    headlineContent = { Text("Pure AMOLED Black") },
                    supportingContent = { Text("Force #000000 background for OLED efficiency") },
                    leadingContent = { Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = accent) },
                    trailingContent = {
                        Switch(
                            checked = settings.pureAmoledBlack,
                            onCheckedChange = viewModel::setPureAmoledBlack
                        )
                    }
                )

                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Neon Accent", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TigerAccentStyle.entries.forEach { style ->
                            val selected = style == settings.accentStyle
                            val swatch = accentColor(style)
                            Box(
                                modifier = Modifier
                                    .size(if (selected) 44.dp else 38.dp)
                                    .clip(CircleShape)
                                    .background(swatch)
                                    .border(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) Color.White else Color.White.copy(alpha = 0.25f),
                                        shape = CircleShape
                                    )
                                    .then(if (selected) Modifier.tigerGlow(swatch) else Modifier)
                                    .bounceClick { viewModel.setAccentStyle(style) }
                            )
                        }
                    }
                }

                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Default Player View", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    DefaultPlayerView.entries.forEach { option ->
                        val selected = option == settings.defaultPlayerView
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.setDefaultPlayerView(option) },
                            label = {
                                Text(
                                    when (option) {
                                        DefaultPlayerView.ARTWORK_3D -> "3D Artwork"
                                        DefaultPlayerView.FLUID_VORTEX -> "Fluid Vortex"
                                        DefaultPlayerView.SONIC_PRISM -> "Sonic Prism"
                                    }
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }
            }

            MatrixSection(title = "Audio Engine", icon = Icons.Rounded.GraphicEq, accent = accent) {
                ListItem(
                    headlineContent = { Text("Crossfade Duration") },
                    supportingContent = { Text("${crossfadeSlider.roundToInt()} seconds") },
                    leadingContent = { Icon(Icons.Rounded.Timer, contentDescription = null, tint = accent) }
                )

                Slider(
                    value = crossfadeSlider,
                    onValueChange = { crossfadeSlider = it },
                    onValueChangeFinished = { viewModel.setCrossfadeDuration(crossfadeSlider.roundToInt()) },
                    valueRange = 0f..12f,
                    steps = 11,
                    modifier = Modifier.padding(horizontal = 18.dp)
                )

                ListItem(
                    headlineContent = { Text("Gapless Playback") },
                    supportingContent = { Text("Enable seamless transitions with no frame gaps") },
                    leadingContent = { Icon(Icons.Rounded.Memory, contentDescription = null, tint = accent) },
                    trailingContent = {
                        Switch(
                            checked = settings.gaplessPlayback,
                            onCheckedChange = viewModel::setGaplessPlayback
                        )
                    }
                )

                ListItem(
                    headlineContent = { Text("Audio-Reactive Haptics") },
                    supportingContent = { Text("Pulse vibration with kick and bass energy") },
                    leadingContent = { Icon(Icons.Rounded.Vibration, contentDescription = null, tint = accent) },
                    trailingContent = {
                        Switch(
                            checked = settings.audioReactiveHaptics,
                            onCheckedChange = viewModel::setAudioReactiveHaptics
                        )
                    }
                )
            }

            MatrixSection(title = "Library & Behaviors", icon = Icons.Rounded.Headset, accent = accent) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Skip Short Audio", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SkipShortAudio.entries.forEach { option ->
                            val selected = option == settings.skipShortAudio
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.setSkipShortAudio(option) },
                                label = {
                                    Text(
                                        when (option) {
                                            SkipShortAudio.OFF -> "Off"
                                            SkipShortAudio.BELOW_30_SECONDS -> "< 30s"
                                            SkipShortAudio.BELOW_60_SECONDS -> "< 60s"
                                        }
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accent.copy(alpha = 0.2f)
                                )
                            )
                        }
                    }
                }

                ListItem(
                    headlineContent = { Text("Resume on Bluetooth connect") },
                    leadingContent = { Icon(Icons.Rounded.BluetoothAudio, contentDescription = null, tint = accent) },
                    trailingContent = {
                        Switch(
                            checked = settings.resumeOnBluetoothConnect,
                            onCheckedChange = viewModel::setResumeOnBluetoothConnect
                        )
                    }
                )

                ListItem(
                    headlineContent = { Text("Resume on wired headset connect") },
                    leadingContent = { Icon(Icons.Rounded.Headset, contentDescription = null, tint = accent) },
                    trailingContent = {
                        Switch(
                            checked = settings.resumeOnWiredHeadsetConnect,
                            onCheckedChange = viewModel::setResumeOnWiredHeadsetConnect
                        )
                    }
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(accent.copy(alpha = 0.24f), accent.copy(alpha = 0.08f))
                            )
                        )
                        .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                        .tigerGlow(accent)
                        .bounceClick { viewModel.triggerLibraryRescan() }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, tint = accent)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Rescan Library", fontWeight = FontWeight.Black)
                            AnimatedVisibility(visible = rescan.isRunning, enter = fadeIn(), exit = fadeOut()) {
                                Text(
                                    text = "Scanning ${rescan.current}/${rescan.total}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.Bolt, contentDescription = null, tint = accent)
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

@Composable
private fun MatrixSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(22.dp))
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = accent)
            Spacer(modifier = Modifier.width(8.dp))
            Text(title.uppercase(), color = accent, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }
        content()
    }
}

@Composable
private fun accentColor(accentStyle: TigerAccentStyle): Color {
    return when (accentStyle) {
        TigerAccentStyle.NEON_ORANGE -> TigerNeonOrange
        TigerAccentStyle.CYBER_CYAN -> TigerCyberCyan
        TigerAccentStyle.TOXIC_LIME -> TigerToxicLime
        TigerAccentStyle.SPECTRAL_VIOLET -> TigerSpectralViolet
    }
}
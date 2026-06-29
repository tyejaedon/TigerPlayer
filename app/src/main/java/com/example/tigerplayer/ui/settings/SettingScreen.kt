package com.example.tigerplayer.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tigerplayer.data.local.DefaultPlayerView
import com.example.tigerplayer.data.local.SkipShortAudio
import com.example.tigerplayer.data.local.TigerAccentStyle
import com.example.tigerplayer.ui.theme.PremiumGlassCard
import com.example.tigerplayer.ui.theme.TigerCyberCyan
import com.example.tigerplayer.ui.theme.TigerNeonOrange
import com.example.tigerplayer.ui.theme.TigerSpectralViolet
import com.example.tigerplayer.ui.theme.TigerToxicLime
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "CONTROL MATRIX",
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::resetToDefaults) {
                        Icon(Icons.Rounded.Restore, contentDescription = "Reset Defaults")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.glassEffect(RectangleShape)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            
            // --- APPEARANCE SECTION ---
            MatrixSection(title = "Core Visuals", icon = Icons.Rounded.Palette, accent = accent) {
                ListItem(
                    headlineContent = { Text("AMOLED BLACK", fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("Force pure #000000 surfaces") },
                    leadingContent = { Icon(Icons.Rounded.AutoAwesome, null, tint = accent) },
                    trailingContent = {
                        Switch(checked = settings.pureAmoledBlack, onCheckedChange = viewModel::setPureAmoledBlack)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = Color.White.copy(alpha = 0.05f))

                Column(modifier = Modifier.padding(16.dp)) {
                    Text("NEON ACCENT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = accent, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        TigerAccentStyle.entries.forEach { style ->
                            val selected = style == settings.accentStyle
                            val swatch = accentColor(style)
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(swatch)
                                    .border(
                                        width = if (selected) 2.5.dp else 1.dp,
                                        color = if (selected) Color.White else Color.White.copy(alpha = 0.3f),
                                        shape = CircleShape
                                    )
                                    .then(if (selected) Modifier.tigerGlow(swatch) else Modifier)
                                    .bounceClick { viewModel.setAccentStyle(style) }
                            )
                        }
                    }
                }
            }

            // --- AUDIO ENGINE SECTION ---
            MatrixSection(title = "Neural DSP", icon = Icons.Rounded.GraphicEq, accent = accent) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Timer, null, tint = accent, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("CROSSFADE", fontWeight = FontWeight.Bold)
                        }
                        Text("${crossfadeSlider.roundToInt()}s", color = accent, fontWeight = FontWeight.Black)
                    }
                    Slider(
                        value = crossfadeSlider,
                        onValueChange = { crossfadeSlider = it },
                        onValueChangeFinished = { viewModel.setCrossfadeDuration(crossfadeSlider.roundToInt()) },
                        valueRange = 0f..12f,
                        steps = 11,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = Color.White.copy(alpha = 0.05f))

                ListItem(
                    headlineContent = { Text("GAPLESS FLOW", fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("Zero-latency track transitions") },
                    leadingContent = { Icon(Icons.Rounded.Memory, null, tint = accent) },
                    trailingContent = {
                        Switch(checked = settings.gaplessPlayback, onCheckedChange = viewModel::setGaplessPlayback)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                ListItem(
                    headlineContent = { Text("HAPTIC RESONANCE", fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("Kick-drum mechanical feedback") },
                    leadingContent = { Icon(Icons.Rounded.Vibration, null, tint = accent) },
                    trailingContent = {
                        Switch(checked = settings.audioReactiveHaptics, onCheckedChange = viewModel::setAudioReactiveHaptics)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            // --- LIBRARY UTILITIES ---
            MatrixSection(title = "Archive Protocol", icon = Icons.Rounded.Refresh, accent = accent) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(accent.copy(alpha = 0.08f))
                        .border(1.dp, accent.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                        .bounceClick { viewModel.triggerLibraryRescan() }
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(accent.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Refresh, null, tint = accent, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("SCAN ARCHIVES", fontWeight = FontWeight.Black, letterSpacing = 0.5.sp)
                            AnimatedVisibility(
                                visible = rescan.isRunning,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Text(
                                    text = "PROCESSING ${rescan.current} / ${rescan.total}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accent.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(Icons.Rounded.Bolt, null, tint = accent.copy(alpha = 0.5f))
                    }
                }
                
                ListItem(
                    headlineContent = { Text("BLUETOOTH RESUME", fontWeight = FontWeight.Bold) },
                    leadingContent = { Icon(Icons.Rounded.BluetoothAudio, null, tint = accent) },
                    trailingContent = {
                        Switch(checked = settings.resumeOnBluetoothConnect, onCheckedChange = viewModel::setResumeOnBluetoothConnect)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            TextButton(
                onClick = viewModel::resetToDefaults,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("RESTORE PROTOCOL DEFAULTS", color = Color.White.copy(alpha = 0.4f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
            }
            
            Spacer(modifier = Modifier.height(40.dp))
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
    PremiumGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        borderWidth = 1.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(accent.copy(alpha = 0.05f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp
                )
            }
            content()
        }
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

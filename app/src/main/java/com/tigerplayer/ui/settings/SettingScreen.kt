package com.tigerplayer.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import com.tigerplayer.BuildConfig
import com.tigerplayer.data.backup.RestoreStrategy
import com.tigerplayer.data.local.AudioReactiveHapticsProfile
import com.tigerplayer.data.local.DefaultPlayerView
import com.tigerplayer.data.local.SkipShortAudio
import com.tigerplayer.data.local.ThemeMode
import com.tigerplayer.data.local.TigerAccentStyle
import com.tigerplayer.service.HapticsDebugEvent
import com.tigerplayer.service.HapticsDebugState
import com.tigerplayer.ui.theme.PremiumGlassCard
import com.tigerplayer.ui.theme.SpotifyGreen
import com.tigerplayer.ui.theme.TigerCyberCyan
import com.tigerplayer.ui.theme.TigerNeonOrange
import com.tigerplayer.ui.theme.TigerSpectralViolet
import com.tigerplayer.ui.theme.TigerToxicLime
import com.tigerplayer.ui.theme.bounceClick
import com.tigerplayer.ui.theme.glassEffect
import com.tigerplayer.ui.theme.tigerGlow
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val rescan by viewModel.libraryRescanState.collectAsStateWithLifecycle()
    val hapticsDebug by viewModel.hapticsDebugState.collectAsStateWithLifecycle()
    val backupEvent by viewModel.backupEvent.collectAsStateWithLifecycle()
    val accent = accentColor(settings.accentStyle)

    val context = LocalContext.current
    LaunchedEffect(backupEvent) {
        val event = backupEvent ?: return@LaunchedEffect
        val message = when (event) {
            is BackupUiEvent.ExportSucceeded ->
                "Backup saved: ${event.playlists} playlists, ${event.history} history rows"
            is BackupUiEvent.ImportSucceeded ->
                "Restored: ${event.playlists} playlists, ${event.history} history rows"
            is BackupUiEvent.Failed -> "Backup error: ${event.message}"
        }
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        viewModel.consumeBackupEvent()
    }

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
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("THEME MODE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = accent, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(mode.name.replace("_", " ")) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accent.copy(alpha = 0.2f),
                                    selectedLabelColor = accent
                                )
                            )
                        }
                    }
                }

                HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

                ListItem(
                    headlineContent = { Text("AMOLED BLACK", fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("Force pure #000000 surfaces") },
                    leadingContent = { Icon(Icons.Rounded.AutoAwesome, null, tint = accent) },
                    trailingContent = {
                        Switch(checked = settings.pureAmoledBlack, onCheckedChange = viewModel::setPureAmoledBlack)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

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
                                        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                        shape = CircleShape
                                    )
                                    .then(if (selected) Modifier.tigerGlow(swatch) else Modifier)
                                    .bounceClick { viewModel.setAccentStyle(style) }
                            )
                        }
                    }
                }

                HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

                Column(modifier = Modifier.padding(16.dp)) {
                    Text("DEFAULT PLAYER VIEW", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = accent, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DefaultPlayerView.entries
                            .filter { it != DefaultPlayerView.SONIC_PRISM }
                            .forEach { view ->
                            FilterChip(
                                selected = settings.defaultPlayerView == view,
                                onClick = { viewModel.setDefaultPlayerView(view) },
                                label = { Text(view.name.replace("_", " ")) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accent.copy(alpha = 0.2f),
                                    selectedLabelColor = accent
                                )
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

                HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

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
                    headlineContent = { Text("SYSTEM DECODER / DSP", fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("Route audio through Android offload path") },
                    leadingContent = { Icon(Icons.Rounded.BluetoothAudio, null, tint = accent) },
                    trailingContent = {
                        Switch(
                            checked = settings.routeToSystemDecoderDsp,
                            onCheckedChange = viewModel::setRouteToSystemDecoderDsp
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                ListItem(
                    headlineContent = { Text("HAPTIC RESONANCE", fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("Kick-drum mechanical feedback (uses app DSP route)") },
                    leadingContent = { Icon(Icons.Rounded.Vibration, null, tint = accent) },
                    trailingContent = {
                        Switch(checked = settings.audioReactiveHaptics, onCheckedChange = viewModel::setAudioReactiveHaptics)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                AnimatedVisibility(
                    visible = settings.audioReactiveHaptics,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Text(
                            text = "HAPTIC INTENSITY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = accent,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AudioReactiveHapticsProfile.entries.forEach { profile ->
                                FilterChip(
                                    selected = settings.audioReactiveHapticsProfile == profile,
                                    onClick = { viewModel.setAudioReactiveHapticsProfile(profile) },
                                    label = {
                                        val label = when (profile) {
                                            AudioReactiveHapticsProfile.SUBTLE -> "SUBTLE"
                                            AudioReactiveHapticsProfile.BALANCED -> "BALANCED"
                                            AudioReactiveHapticsProfile.AGGRESSIVE -> "AGGRESSIVE"
                                        }
                                        Text(label)
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accent.copy(alpha = 0.2f),
                                        selectedLabelColor = accent
                                    )
                                )
                            }
                        }
                    }
                }

                if (BuildConfig.DEBUG) {
                    HorizontalDivider(
                        Modifier.padding(horizontal = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    )
                    HapticsDebugPanel(
                        debugState = hapticsDebug,
                        accent = accent
                    )
                }
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

                ListItem(
                    headlineContent = { Text("WIRED RESUME", fontWeight = FontWeight.Bold) },
                    leadingContent = { Icon(Icons.Rounded.Headset, null, tint = accent) },
                    trailingContent = {
                        Switch(checked = settings.resumeOnWiredHeadsetConnect, onCheckedChange = viewModel::setResumeOnWiredHeadsetConnect)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                ListItem(
                    headlineContent = { Text("DISABLE PiP", fontWeight = FontWeight.Bold) },
                    supportingContent = { Text("Stop mini-player mode when app goes to background") },
                    leadingContent = { Icon(Icons.Rounded.Headset, null, tint = accent) },
                    trailingContent = {
                        Switch(checked = settings.disablePip, onCheckedChange = viewModel::setDisablePip)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

                Column(modifier = Modifier.padding(16.dp)) {
                    Text("SKIP SHORT AUDIO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = accent, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SkipShortAudio.entries.forEach { option ->
                            FilterChip(
                                selected = settings.skipShortAudio == option,
                                onClick = { viewModel.setSkipShortAudio(option) },
                                label = {
                                    val label = when (option) {
                                        SkipShortAudio.OFF -> "OFF"
                                        SkipShortAudio.BELOW_30_SECONDS -> "< 30s"
                                        SkipShortAudio.BELOW_60_SECONDS -> "< 60s"
                                    }
                                    Text(label)
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accent.copy(alpha = 0.2f),
                                    selectedLabelColor = accent
                                )
                            )
                        }
                    }
                }
            }

            // --- MUSIC FOLDERS SECTION (issue #50) ---
            MusicFoldersSection(viewModel = viewModel, accent = accent)

            // --- CONNECTED ACCOUNTS (Spotify / Navidrome sign-out) ---
            ConnectedAccountsSection(viewModel = viewModel, accent = accent)

            // --- BACKUP & RESTORE ---
            BackupRestoreSection(viewModel = viewModel, accent = accent)

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(
                onClick = viewModel::resetToDefaults,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("RESTORE PROTOCOL DEFAULTS", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black)
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun MusicFoldersSection(viewModel: SettingsViewModel, accent: Color) {
    val musicFolders by viewModel.musicFolders.collectAsStateWithLifecycle()

    val addFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.addMusicFolder(uri, isExcluded = false)
    }
    val excludeFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.addMusicFolder(uri, isExcluded = true)
    }

    MatrixSection(title = "Music Folders", icon = Icons.Rounded.Folder, accent = accent) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Include folders MediaStore may have missed, or exclude ones you don't want scanned.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { addFolderLauncher.launch(null) }) {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("ADD FOLDER", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = { excludeFolderLauncher.launch(null) }) {
                    Icon(Icons.Rounded.Block, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("EXCLUDE FOLDER", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (musicFolders.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

            musicFolders.forEach { folder ->
                ListItem(
                    headlineContent = { Text(folder.displayName, fontWeight = FontWeight.Bold) },
                    supportingContent = {
                        Text(
                            if (folder.isExcluded) "EXCLUDED" else "INCLUDED",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (folder.isExcluded) MaterialTheme.colorScheme.error else accent
                        )
                    },
                    leadingContent = {
                        Icon(
                            Icons.Rounded.Folder,
                            null,
                            tint = if (folder.isExcluded) MaterialTheme.colorScheme.error else accent
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = { viewModel.removeMusicFolder(folder.uriString) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Remove folder")
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}

@Composable
private fun ConnectedAccountsSection(viewModel: SettingsViewModel, accent: Color) {
    val accounts by viewModel.connectedAccountsState.collectAsStateWithLifecycle()
    var pendingLogout by remember { mutableStateOf<String?>(null) }

    MatrixSection(title = "Connected Accounts", icon = Icons.Rounded.AccountCircle, accent = accent) {
        ListItem(
            headlineContent = { Text("SPOTIFY", fontWeight = FontWeight.Bold) },
            supportingContent = { Text(if (accounts.isSpotifyConnected) "Connected" else "Not connected") },
            leadingContent = { Icon(Icons.Rounded.CloudSync, null, tint = if (accounts.isSpotifyConnected) SpotifyGreen else accent.copy(alpha = 0.4f)) },
            trailingContent = {
                if (accounts.isSpotifyConnected) {
                    TextButton(onClick = { pendingLogout = "Spotify" }) {
                        Text("LOG OUT")
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )

        HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

        ListItem(
            headlineContent = { Text("NAVIDROME", fontWeight = FontWeight.Bold) },
            supportingContent = {
                Text(accounts.navidromeServerUrl?.takeIf { it.isNotBlank() } ?: "Not connected")
            },
            leadingContent = { Icon(Icons.Rounded.Dns, null, tint = if (accounts.isNavidromeConnected) accent else accent.copy(alpha = 0.4f)) },
            trailingContent = {
                if (accounts.isNavidromeConnected) {
                    TextButton(onClick = { pendingLogout = "Navidrome" }) {
                        Text("LOG OUT")
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }

    val serviceToLogOut = pendingLogout
    if (serviceToLogOut != null) {
        AlertDialog(
            onDismissRequest = { pendingLogout = null },
            title = { Text("Log out of $serviceToLogOut?") },
            text = { Text("You'll need to sign back in to use $serviceToLogOut again.") },
            confirmButton = {
                TextButton(onClick = {
                    when (serviceToLogOut) {
                        "Spotify" -> viewModel.logoutSpotify()
                        "Navidrome" -> viewModel.logoutNavidrome()
                    }
                    pendingLogout = null
                }) {
                    Text("LOG OUT")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingLogout = null }) {
                    Text("CANCEL")
                }
            }
        )
    }
}

@Composable
private fun BackupRestoreSection(viewModel: SettingsViewModel, accent: Color) {
    // Merge by default: a restore should never silently wipe what's already on the device.
    var restoreStrategy by remember { mutableStateOf(RestoreStrategy.MERGE) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) viewModel.exportBackup(uri) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) viewModel.importBackup(uri, restoreStrategy) }

    MatrixSection(title = "Backup & Restore", icon = Icons.Rounded.SaveAlt, accent = accent) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Saves playlists, listening history, and app settings to a JSON file you choose. " +
                    "Navidrome/Spotify sign-in is never included â€” you'll sign back in after a restore.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.66f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { exportLauncher.launch("tigerplayer-backup.json") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.SaveAlt, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("EXPORT BACKUP", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }

        HorizontalDivider(Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

        Column(modifier = Modifier.padding(16.dp)) {
            Text("ON RESTORE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = accent, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = restoreStrategy == RestoreStrategy.MERGE,
                    onClick = { restoreStrategy = RestoreStrategy.MERGE },
                    label = { Text("ADD TO EXISTING") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accent.copy(alpha = 0.2f),
                        selectedLabelColor = accent
                    )
                )
                FilterChip(
                    selected = restoreStrategy == RestoreStrategy.REPLACE,
                    onClick = { restoreStrategy = RestoreStrategy.REPLACE },
                    label = { Text("REPLACE ALL") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accent.copy(alpha = 0.2f),
                        selectedLabelColor = accent
                    )
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Upload, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("RESTORE BACKUP", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HapticsDebugPanel(
    debugState: HapticsDebugState,
    accent: Color
) {
    val eventLabel = when (debugState.event) {
        HapticsDebugEvent.IDLE -> "IDLE"
        HapticsDebugEvent.STATUS -> "STATUS"
        HapticsDebugEvent.SKIP -> "SKIP"
        HapticsDebugEvent.FIRE -> "FIRE"
    }

    val reason = debugState.reason.ifBlank { "none" }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = "HAPTICS DEBUG",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = accent,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "event=$eventLabel reason=$reason",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "enabled=${debugState.enabled} playing=${debugState.isPlaying} profile=${debugState.profile.name}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
        )
        Text(
            text = "routeToSystem=${debugState.routeToSystemDecoderDsp} bitPerfect=${debugState.isBitPerfectMode} cooldownLeft=${debugState.cooldownRemainingMs}ms",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
        )
        Text(
            text = "raw=${"%.3f".format(debugState.rawScore)} smooth=${"%.3f".format(debugState.smoothedScore)} min=${"%.3f".format(debugState.minScore)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
        )
        Text(
            text = "bass=${"%.3f".format(debugState.bass)} energy=${"%.3f".format(debugState.energy)} flux=${"%.3f".format(debugState.flux)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
        )
        Text(
            text = "pulses=${debugState.pulseCount} amp=${debugState.amplitude} dur=${debugState.durationMs}ms",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
        )
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
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp
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

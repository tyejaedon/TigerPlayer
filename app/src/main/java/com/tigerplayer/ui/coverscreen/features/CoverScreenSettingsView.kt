package com.tigerplayer.ui.coverscreen

import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tigerplayer.BuildConfig
import com.tigerplayer.data.backup.RestoreStrategy
import com.tigerplayer.data.local.AudioReactiveHapticsProfile
import com.tigerplayer.data.local.DefaultPlayerView
import com.tigerplayer.data.local.SkipShortAudio
import com.tigerplayer.data.local.ThemeMode
import com.tigerplayer.data.local.TigerAccentStyle
import com.tigerplayer.service.HapticsDebugEvent
import com.tigerplayer.service.HapticsDebugState
import com.tigerplayer.ui.settings.BackupUiEvent
import com.tigerplayer.ui.settings.SettingsViewModel
import com.tigerplayer.ui.settings.SpotifyAuthErrorDialog
import com.tigerplayer.ui.theme.SpotifyGreen
import com.tigerplayer.ui.theme.TigerCyberCyan
import com.tigerplayer.ui.theme.TigerNeonOrange
import com.tigerplayer.ui.theme.TigerNeonRed
import com.tigerplayer.ui.theme.TigerSpectralViolet
import com.tigerplayer.ui.theme.TigerToxicLime
import com.tigerplayer.ui.theme.bounceClick
import com.tigerplayer.ui.theme.glassEffect
import com.tigerplayer.ui.theme.rememberTigerAmbientGradient
import com.tigerplayer.ui.theme.tigerGlow
import kotlin.math.roundToInt

@Composable
fun CoverScreenSettingsView(
    viewModel: SettingsViewModel = hiltViewModel(),
    onOpenEqualizer: (() -> Unit)? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val context = LocalContext.current
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
    val rescanState by viewModel.libraryRescanState.collectAsStateWithLifecycle()
    val connectedAccounts by viewModel.connectedAccountsState.collectAsStateWithLifecycle()
    val spotifyAuthError by viewModel.spotifyAuthError.collectAsStateWithLifecycle()
    val musicFolders by viewModel.musicFolders.collectAsStateWithLifecycle()
    val hapticsDebug by viewModel.hapticsDebugState.collectAsStateWithLifecycle()
    val backupEvent by viewModel.backupEvent.collectAsStateWithLifecycle()

    var pendingLogout by remember { mutableStateOf<String?>(null) }
    var restoreStrategy by remember { mutableStateOf(RestoreStrategy.MERGE) }

    val addFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) viewModel.addMusicFolder(uri, isExcluded = false)
    }
    val excludeFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) viewModel.addMusicFolder(uri, isExcluded = true)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportBackup(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importBackup(uri, restoreStrategy)
    }

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

    if (spotifyAuthError != null) {
        SpotifyAuthErrorDialog(
            error = spotifyAuthError!!,
            onDismiss = viewModel::dismissSpotifyAuthError,
            onRetry = {
                viewModel.dismissSpotifyAuthError()
                viewModel.loginSpotify(context)
            },
            isCoverScreen = true
        )
    }

    val serviceToLogOut = pendingLogout
    if (serviceToLogOut != null) {
        AlertDialog(
            onDismissRequest = { pendingLogout = null },
            modifier = Modifier.testTag(CoverScreenTestTags.SETTINGS_LOGOUT_CONFIRM_DIALOG),
            title = {
                Text(
                    text = "Log out of $serviceToLogOut?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "You'll need to sign back in to use $serviceToLogOut again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        when (serviceToLogOut) {
                            "Spotify" -> viewModel.logoutSpotify()
                            "Navidrome" -> viewModel.logoutNavidrome()
                        }
                        pendingLogout = null
                    },
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag(CoverScreenTestTags.SETTINGS_LOGOUT_CONFIRM)
                ) {
                    Text("LOG OUT", color = Color.Red, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                        pendingLogout = null
                    },
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag(CoverScreenTestTags.SETTINGS_LOGOUT_CANCEL)
                ) {
                    Text("CANCEL", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    val accent = accentColor(settingsState.accentStyle)
    var crossfadeSlider by remember(settingsState.crossfadeDurationSec) {
        mutableFloatStateOf(settingsState.crossfadeDurationSec.toFloat())
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag(CoverScreenTestTags.SETTINGS_VIEW)
            .pointerInput(Unit) {
                var dragDistance = 0f
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, amount -> dragDistance += amount },
                    onDragEnd = {
                        if (dragDistance > 80.dp.toPx()) {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onClose()
                        }
                        dragDistance = 0f
                    },
                    onDragCancel = { dragDistance = 0f }
                )
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. TOP BAR NAVIGATION
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassEffect(RectangleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onClose()
                    },
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .minimumInteractiveComponentSize()
                        .semantics {
                            role = Role.Button
                            onClick(label = "Close settings") {
                                onClose()
                                true
                            }
                            contentDescription = "Close settings"
                        }
                        .testTag(CoverScreenTestTags.SETTINGS_BACK)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close settings",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "SETTINGS",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp)
                )

                IconButton(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        viewModel.resetToDefaults()
                    },
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .minimumInteractiveComponentSize()
                        .semantics {
                            role = Role.Button
                            onClick(label = "Reset defaults") {
                                viewModel.resetToDefaults()
                                true
                            }
                            contentDescription = "Reset defaults"
                        }
                        .testTag(CoverScreenTestTags.SETTINGS_RESET)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Restore,
                        contentDescription = "Reset defaults",
                        tint = accent
                    )
                }
            }

            // 2. SCROLLABLE SETTINGS SECTIONS
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(CoverScreenTestTags.SETTINGS_LIST),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // SECTION 1: CORE VISUALS
                item(key = "section_visuals") {
                    CoverSettingsCard(title = "Core Visuals", icon = Icons.Rounded.Palette, accent = accent) {
                        // Theme Mode
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(
                                text = "THEME MODE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = accent,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ThemeMode.entries.forEach { mode ->
                                    val isSelected = settingsState.themeMode == mode
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                            viewModel.setThemeMode(mode)
                                        },
                                        label = { Text(mode.name.replace("_", " ")) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = accent.copy(alpha = 0.22f),
                                            selectedLabelColor = accent
                                        )
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            thickness = 0.5.dp,
                            color = Color.White.copy(alpha = 0.08f)
                        )

                        // Pure AMOLED Black
                        CoverSettingsSwitchRow(
                            headline = "AMOLED Black",
                            supporting = "Force pure #000000 surfaces",
                            icon = Icons.Rounded.AutoAwesome,
                            checked = settingsState.pureAmoledBlack,
                            onCheckedChange = { viewModel.setPureAmoledBlack(it) },
                            accent = accent
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            thickness = 0.5.dp,
                            color = Color.White.copy(alpha = 0.08f)
                        )

                        // Neon Accent
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(
                                text = "NEON ACCENT",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = accent,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TigerAccentStyle.entries.forEach { style ->
                                    val isSelected = settingsState.accentStyle == style
                                    val swatch = accentColor(style)
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(swatch)
                                            .border(
                                                width = if (isSelected) 2.5.dp else 1.dp,
                                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.25f),
                                                shape = CircleShape
                                            )
                                            .then(if (isSelected) Modifier.tigerGlow(swatch) else Modifier)
                                            .bounceClick {
                                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                                viewModel.setAccentStyle(style)
                                            }
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            thickness = 0.5.dp,
                            color = Color.White.copy(alpha = 0.08f)
                        )

                        // Default Player View
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(
                                text = "DEFAULT PLAYER VIEW",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = accent,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                DefaultPlayerView.entries
                                    .filter { it != DefaultPlayerView.SONIC_PRISM }
                                    .forEach { playerView ->
                                        val isSelected = settingsState.defaultPlayerView == playerView
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                                viewModel.setDefaultPlayerView(playerView)
                                            },
                                            label = { Text(playerView.name.replace("_", " ")) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = accent.copy(alpha = 0.22f),
                                                selectedLabelColor = accent
                                            )
                                        )
                                    }
                            }
                        }
                    }
                }

                // SECTION 2: COVER SCREEN & FOLDABLES
                item(key = "section_coverscreen") {
                    CoverSettingsCard(title = "Cover Screen & Foldables", icon = Icons.Rounded.ScreenRotation, accent = accent) {
                        CoverSettingsSwitchRow(
                            headline = "Cover Screen Preview",
                            supporting = "Force compact cover screen UI on standard displays",
                            icon = Icons.Rounded.ScreenRotation,
                            checked = settingsState.coverScreenPreviewMode,
                            onCheckedChange = { viewModel.setCoverScreenPreviewMode(it) },
                            accent = accent
                        )
                    }
                }

                // SECTION 3: NEURAL DSP & AUDIO ENGINE
                item(key = "section_audio") {
                    CoverSettingsCard(title = "Neural DSP & Playback", icon = Icons.Rounded.GraphicEq, accent = accent) {
                        // Crossfade Duration Slider
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Timer,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "CROSSFADE",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "${crossfadeSlider.roundToInt()}s",
                                    color = accent,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp
                                )
                            }
                            Slider(
                                value = crossfadeSlider,
                                onValueChange = { crossfadeSlider = it },
                                onValueChangeFinished = {
                                    viewModel.setCrossfadeDuration(crossfadeSlider.roundToInt())
                                },
                                valueRange = 0f..12f,
                                steps = 11,
                                colors = SliderDefaults.colors(
                                    thumbColor = accent,
                                    activeTrackColor = accent,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                                ),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            thickness = 0.5.dp,
                            color = Color.White.copy(alpha = 0.08f)
                        )

                        // Gapless Playback
                        CoverSettingsSwitchRow(
                            headline = "Gapless Flow",
                            supporting = "Zero-latency track transitions",
                            icon = Icons.Rounded.Memory,
                            checked = settingsState.gaplessPlayback,
                            onCheckedChange = { viewModel.setGaplessPlayback(it) },
                            accent = accent
                        )

                        // Equalizer / AutoEQ Entry Point (parity with SettingScreen)
                        if (onOpenEqualizer != null) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                thickness = 0.5.dp,
                                color = Color.White.copy(alpha = 0.08f)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .minimumInteractiveComponentSize()
                                    .testTag(CoverScreenTestTags.SETTINGS_EQUALIZER)
                                    .clickable {
                                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        onOpenEqualizer()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Tune,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Studio Equalizer & AutoEQ",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "10-band curves, targets, headroom",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Text(
                                    text = "OPEN →",
                                    color = accent,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            thickness = 0.5.dp,
                            color = Color.White.copy(alpha = 0.08f)
                        )

                        // System Decoder / DSP
                        CoverSettingsSwitchRow(
                            headline = "System Decoder / DSP",
                            supporting = "Route audio through Android offload path",
                            icon = Icons.Rounded.BluetoothAudio,
                            checked = settingsState.routeToSystemDecoderDsp,
                            onCheckedChange = { viewModel.setRouteToSystemDecoderDsp(it) },
                            accent = accent
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            thickness = 0.5.dp,
                            color = Color.White.copy(alpha = 0.08f)
                        )

                        // Haptic Resonance
                        CoverSettingsSwitchRow(
                            headline = "Haptic Resonance",
                            supporting = "Kick-drum mechanical feedback (uses DSP)",
                            icon = Icons.Rounded.Vibration,
                            checked = settingsState.audioReactiveHaptics,
                            onCheckedChange = { viewModel.setAudioReactiveHaptics(it) },
                            accent = accent
                        )

                        // Haptic Intensity Profile (if enabled)
                        AnimatedVisibility(
                            visible = settingsState.audioReactiveHaptics,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                                Text(
                                    text = "HAPTIC INTENSITY",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = accent,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AudioReactiveHapticsProfile.entries.forEach { profile ->
                                        val isSelected = settingsState.audioReactiveHapticsProfile == profile
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                                viewModel.setAudioReactiveHapticsProfile(profile)
                                            },
                                            label = { Text(profile.name) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = accent.copy(alpha = 0.22f),
                                                selectedLabelColor = accent
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // Haptics Telemetry Debug Panel (when BuildConfig.DEBUG is active)
                        if (BuildConfig.DEBUG) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                thickness = 0.5.dp,
                                color = Color.White.copy(alpha = 0.08f)
                            )
                            CoverHapticsDebugPanel(
                                debugState = hapticsDebug,
                                accent = accent,
                                modifier = Modifier.testTag(CoverScreenTestTags.SETTINGS_HAPTICS_DEBUG)
                            )
                        }
                    }
                }

                // SECTION 4: ARCHIVE PROTOCOL & HARDWARE
                item(key = "section_protocol") {
                    CoverSettingsCard(title = "Archive Protocol & Hardware", icon = Icons.Rounded.Refresh, accent = accent) {
                        // Scan Archives Action Card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(accent.copy(alpha = 0.10f))
                                .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                                .bounceClick {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    viewModel.triggerLibraryRescan()
                                }
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(accent.copy(alpha = 0.20f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "SCAN ARCHIVES",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp,
                                        letterSpacing = 0.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    AnimatedVisibility(
                                        visible = rescanState.isRunning,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut()
                                    ) {
                                        Text(
                                            text = "PROCESSING ${rescanState.current} / ${rescanState.total}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = accent,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            thickness = 0.5.dp,
                            color = Color.White.copy(alpha = 0.08f)
                        )

                        // Bluetooth Resume
                        CoverSettingsSwitchRow(
                            headline = "Bluetooth Resume",
                            supporting = "Auto-resume when Bluetooth connects",
                            icon = Icons.Rounded.BluetoothAudio,
                            checked = settingsState.resumeOnBluetoothConnect,
                            onCheckedChange = { viewModel.setResumeOnBluetoothConnect(it) },
                            accent = accent
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            thickness = 0.5.dp,
                            color = Color.White.copy(alpha = 0.08f)
                        )

                        // Wired Headset Resume
                        CoverSettingsSwitchRow(
                            headline = "Wired Resume",
                            supporting = "Auto-resume on headset connect",
                            icon = Icons.Rounded.Headset,
                            checked = settingsState.resumeOnWiredHeadsetConnect,
                            onCheckedChange = { viewModel.setResumeOnWiredHeadsetConnect(it) },
                            accent = accent
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            thickness = 0.5.dp,
                            color = Color.White.copy(alpha = 0.08f)
                        )

                        // Disable PiP
                        CoverSettingsSwitchRow(
                            headline = "Disable PiP",
                            supporting = "Stop mini-player when app backgrounds",
                            icon = Icons.Rounded.PictureInPicture,
                            checked = settingsState.disablePip,
                            onCheckedChange = { viewModel.setDisablePip(it) },
                            accent = accent
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            thickness = 0.5.dp,
                            color = Color.White.copy(alpha = 0.08f)
                        )

                        // Online Artwork Lookup
                        CoverSettingsSwitchRow(
                            headline = "Online Artwork Lookup",
                            supporting = "Fetch high-resolution artwork online",
                            icon = Icons.Rounded.CloudSync,
                            checked = settingsState.fetchOnlineArtwork,
                            onCheckedChange = { viewModel.setFetchOnlineArtwork(it) },
                            accent = accent,
                            modifier = Modifier.testTag(CoverScreenTestTags.SETTINGS_ONLINE_ARTWORK)
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            thickness = 0.5.dp,
                            color = Color.White.copy(alpha = 0.08f)
                        )

                        // Auto-Download Lyrics
                        CoverSettingsSwitchRow(
                            headline = "Auto-Download Lyrics",
                            supporting = "Silently fetch synchronized lyrics in background",
                            icon = Icons.Rounded.CloudSync,
                            checked = settingsState.autoDownloadLyrics,
                            onCheckedChange = { viewModel.setAutoDownloadLyrics(it) },
                            accent = accent,
                            modifier = Modifier.testTag(CoverScreenTestTags.SETTINGS_AUTO_LYRICS)
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            thickness = 0.5.dp,
                            color = Color.White.copy(alpha = 0.08f)
                        )

                        // Skip Short Audio
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(
                                text = "SKIP SHORT AUDIO",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = accent,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SkipShortAudio.entries.forEach { option ->
                                    val isSelected = settingsState.skipShortAudio == option
                                    val label = when (option) {
                                        SkipShortAudio.OFF -> "OFF"
                                        SkipShortAudio.BELOW_30_SECONDS -> "< 30s"
                                        SkipShortAudio.BELOW_60_SECONDS -> "< 60s"
                                    }
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                            viewModel.setSkipShortAudio(option)
                                        },
                                        label = { Text(label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = accent.copy(alpha = 0.22f),
                                            selectedLabelColor = accent
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // SECTION 5: MUSIC FOLDERS
                item(key = "section_folders") {
                    CoverSettingsCard(title = "Music Folders", icon = Icons.Rounded.Folder, accent = accent) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(
                                text = "Include or exclude specific directories for scanning.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        addFolderLauncher.launch(null)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .minimumInteractiveComponentSize()
                                        .testTag(CoverScreenTestTags.SETTINGS_ADD_FOLDER),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("ADD", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        excludeFolderLauncher.launch(null)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .minimumInteractiveComponentSize()
                                        .testTag(CoverScreenTestTags.SETTINGS_EXCLUDE_FOLDER),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Block,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("EXCLUDE", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }
                        }

                        if (musicFolders.isNotEmpty()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                thickness = 0.5.dp,
                                color = Color.White.copy(alpha = 0.08f)
                            )
                            musicFolders.forEach { folder ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 48.dp)
                                        .minimumInteractiveComponentSize()
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Folder,
                                            contentDescription = null,
                                            tint = if (folder.isExcluded) MaterialTheme.colorScheme.error else accent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = folder.displayName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = if (folder.isExcluded) "EXCLUDED" else "INCLUDED",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Black,
                                                color = if (folder.isExcluded) MaterialTheme.colorScheme.error else accent,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                            viewModel.removeMusicFolder(folder.uriString)
                                        },
                                        modifier = Modifier
                                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                            .minimumInteractiveComponentSize()
                                            .testTag(CoverScreenTestTags.removeFolder(folder.uriString))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Delete,
                                            contentDescription = "Remove folder",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // SECTION 6: CONNECTED ACCOUNTS
                item(key = "section_accounts") {
                    CoverSettingsCard(title = "Connected Accounts", icon = Icons.Rounded.CloudSync, accent = accent) {
                        // Spotify Account
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "SPOTIFY",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = SpotifyGreen
                                )
                                Text(
                                    text = if (connectedAccounts.isSpotifyConnected) "Account connected" else "Not authenticated",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (connectedAccounts.isSpotifyConnected) {
                                OutlinedButton(
                                    onClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        pendingLogout = "Spotify"
                                    },
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 48.dp)
                                        .minimumInteractiveComponentSize()
                                        .testTag(CoverScreenTestTags.SETTINGS_SPOTIFY_DISCONNECT),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red.copy(alpha = 0.85f)),
                                    border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.35f))
                                ) {
                                    Text("Disconnect", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        viewModel.loginSpotify(context)
                                    },
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 48.dp)
                                        .minimumInteractiveComponentSize(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SpotifyGreen,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Connect", fontSize = 11.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            thickness = 0.5.dp,
                            color = Color.White.copy(alpha = 0.08f)
                        )

                        // Navidrome Server
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "NAVIDROME",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = accent
                                )
                                Text(
                                    text = if (connectedAccounts.isNavidromeConnected)
                                        connectedAccounts.navidromeServerUrl.orEmpty()
                                    else
                                        "Not connected",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (connectedAccounts.isNavidromeConnected) {
                                OutlinedButton(
                                    onClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        pendingLogout = "Navidrome"
                                    },
                                    modifier = Modifier
                                        .defaultMinSize(minHeight = 48.dp)
                                        .minimumInteractiveComponentSize()
                                        .testTag(CoverScreenTestTags.SETTINGS_NAVIDROME_DISCONNECT),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red.copy(alpha = 0.85f)),
                                    border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.35f))
                                ) {
                                    Text("Disconnect", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // SECTION 7: BACKUP & RESTORE
                item(key = "section_backup") {
                    CoverSettingsCard(title = "Backup & Restore", icon = Icons.Rounded.SaveAlt, accent = accent) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(
                                text = "Export or restore playlists, history, and settings as JSON.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    exportLauncher.launch("tigerplayer-backup.json")
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .minimumInteractiveComponentSize()
                                    .testTag(CoverScreenTestTags.SETTINGS_EXPORT_BACKUP),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SaveAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("EXPORT BACKUP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            thickness = 0.5.dp,
                            color = Color.White.copy(alpha = 0.08f)
                        )

                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(
                                text = "ON RESTORE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = accent,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = restoreStrategy == RestoreStrategy.MERGE,
                                    onClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        restoreStrategy = RestoreStrategy.MERGE
                                    },
                                    label = { Text("ADD TO EXISTING") },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag(CoverScreenTestTags.SETTINGS_RESTORE_STRATEGY_MERGE),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accent.copy(alpha = 0.22f),
                                        selectedLabelColor = accent
                                    )
                                )
                                FilterChip(
                                    selected = restoreStrategy == RestoreStrategy.REPLACE,
                                    onClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                        restoreStrategy = RestoreStrategy.REPLACE
                                    },
                                    label = { Text("REPLACE ALL") },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag(CoverScreenTestTags.SETTINGS_RESTORE_STRATEGY_REPLACE),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accent.copy(alpha = 0.22f),
                                        selectedLabelColor = accent
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    importLauncher.launch(arrayOf("application/json"))
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .minimumInteractiveComponentSize()
                                    .testTag(CoverScreenTestTags.SETTINGS_IMPORT_BACKUP),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Upload,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("RESTORE BACKUP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // SECTION 8: RESTORE DEFAULTS BUTTON
                item(key = "section_reset") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        TextButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                viewModel.resetToDefaults()
                            },
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Restore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "RESTORE PROTOCOL DEFAULTS",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverHapticsDebugPanel(
    debugState: HapticsDebugState,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val eventLabel = when (debugState.event) {
        HapticsDebugEvent.IDLE -> "IDLE"
        HapticsDebugEvent.STATUS -> "STATUS"
        HapticsDebugEvent.SKIP -> "SKIP"
        HapticsDebugEvent.FIRE -> "FIRE"
    }

    val reason = debugState.reason.ifBlank { "none" }

    Column(
        modifier = modifier.padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = "HAPTICS TELEMETRY DEBUG",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = accent,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "event=$eventLabel reason=$reason",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
        Text(
            text = "enabled=${debugState.enabled} playing=${debugState.isPlaying} profile=${debugState.profile.name}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
        Text(
            text = "routeToSystem=${debugState.routeToSystemDecoderDsp} bitPerfect=${debugState.isBitPerfectMode} cooldown=${debugState.cooldownRemainingMs}ms",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
        Text(
            text = "raw=${"%.3f".format(debugState.rawScore)} smooth=${"%.3f".format(debugState.smoothedScore)} min=${"%.3f".format(debugState.minScore)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
        Text(
            text = "bass=${"%.3f".format(debugState.bass)} energy=${"%.3f".format(debugState.energy)} flux=${"%.3f".format(debugState.flux)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
        Text(
            text = "pulses=${debugState.pulseCount} amp=${debugState.amplitude} dur=${debugState.durationMs}ms",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun CoverSettingsCard(
    title: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .glassEffect(RoundedCornerShape(18.dp)),
        color = Color.Black.copy(alpha = 0.35f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.04f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    color = accent
                )
            }
            HorizontalDivider(
                thickness = 0.5.dp,
                color = Color.White.copy(alpha = 0.08f)
            )
            content()
        }
    }
}

@Composable
private fun CoverSettingsSwitchRow(
    headline: String,
    supporting: String? = null,
    icon: ImageVector? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .minimumInteractiveComponentSize()
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onCheckedChange(!checked)
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (checked) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
            Column {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onCheckedChange(it)
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = accent,
                checkedTrackColor = accent.copy(alpha = 0.35f),
                uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.12f)
            )
        )
    }
}

private fun accentColor(accentStyle: TigerAccentStyle): Color {
    return when (accentStyle) {
        TigerAccentStyle.NEON_RED -> TigerNeonRed
        TigerAccentStyle.NEON_ORANGE -> TigerNeonOrange
        TigerAccentStyle.CYBER_CYAN -> TigerCyberCyan
        TigerAccentStyle.TOXIC_LIME -> TigerToxicLime
        TigerAccentStyle.SPECTRAL_VIOLET -> TigerSpectralViolet
    }
}

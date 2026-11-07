package com.tigerplayer.ui.equalizer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.tigerplayer.engine.AcousticEnvironmentMode
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tigerplayer.data.model.HeadphoneSearchResult
import com.tigerplayer.ui.theme.TigerCyberCyan
import com.tigerplayer.ui.theme.TigerNeonOrange
import com.tigerplayer.ui.theme.bounceClick
import com.tigerplayer.ui.theme.glassEffect
import java.util.Locale

/**
 * Industry-standard Parametric Equalizer and AutoEq Hub.
 *
 * Configured dynamically for both compact cover screens ([isCoverScreen] = true)
 * and full-screen smartphone studio displays ([isCoverScreen] = false).
 */
@Composable
fun EqualizerHubView(
    modifier: Modifier = Modifier,
    isCoverScreen: Boolean = false,
    viewModel: EqualizerHubViewModel = hiltViewModel(),
    onClose: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAutoEqDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("EqualizerHubView"),
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (isCoverScreen) {
                CoverScreenEqualizerLayout(
                    state = uiState,
                    viewModel = viewModel,
                    onOpenAutoEq = { showAutoEqDialog = true },
                    onClose = onClose
                )
            } else {
                MainAppStudioEqualizerLayout(
                    state = uiState,
                    viewModel = viewModel,
                    onOpenAutoEq = { showAutoEqDialog = true },
                    onOpenImport = { showImportDialog = true },
                    onClose = onClose
                )
            }
        }
    }

    // AutoEq Headphone Catalog Dialog / Sheet
    if (showAutoEqDialog) {
        AutoEqSelectorSheet(
            query = uiState.autoEqQuery,
            isSearching = uiState.isSearchingAutoEq,
            searchResults = uiState.autoEqSearchResults,
            connectedDeviceName = uiState.connectedDeviceName,
            currentBandMode = uiState.bandMode,
            isCoverScreen = isCoverScreen,
            onQueryChanged = viewModel::searchAutoEq,
            onAutoMatchDevice = {
                viewModel.autoMatchConnectedDevice()
                showAutoEqDialog = false
            },
            onSelect = { item, forceParametric ->
                viewModel.applyAutoEqProfile(item, forceParametric = forceParametric)
                showAutoEqDialog = false
            },
            onDismiss = { showAutoEqDialog = false }
        )
    }

    // Raw AutoEq Text Importer Dialog
    if (showImportDialog) {
        AutoEqImportDialog(
            onImport = { rawText, name ->
                viewModel.importRawAutoEq(rawText, name)
                showImportDialog = false
            },
            onDismiss = { showImportDialog = false }
        )
    }
}

/**
 * Compact cover screen layout: High-contrast, touch-optimized for small displays.
 */
@Composable
private fun CoverScreenEqualizerLayout(
    state: EqualizerHubUiState,
    viewModel: EqualizerHubViewModel,
    onOpenAutoEq: () -> Unit,
    onClose: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        // 1. Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (state.isEqEnabled) TigerNeonOrange else Color.Gray)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "PEQ HUB",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 1.sp
                )
                if (state.autoEqModel != null) {
                    Text(
                        text = " • AUTOEQ",
                        style = MaterialTheme.typography.labelSmall,
                        color = TigerCyberCyan,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = state.isEqEnabled,
                    onCheckedChange = viewModel::toggleMasterEq,
                    modifier = Modifier
                        .scale(0.75f)
                        .testTag("EqMasterSwitch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = TigerNeonOrange,
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.DarkGray
                    )
                )

                if (onClose != null) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(30.dp)
                            .testTag("EqCloseButton")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close EQ",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // 2. Frequency Response Curve Preview
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .glassEffect(RoundedCornerShape(14.dp))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                EqualizerFrequencyCurve(
                    points = state.frequencyCurvePoints,
                    bands = state.bands,
                    isInteractive = false,
                    selectedBandIndex = state.selectedBandIndex,
                    audioFrame = state.audioReactiveFrame,
                    modifier = Modifier.fillMaxSize(),
                    curveColor = if (state.isEqEnabled) TigerCyberCyan else Color.Gray
                )

                // Headroom Badge
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.isClippingImminent) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Clipping Warning",
                            tint = TigerNeonOrange,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                    }
                    Text(
                        text = "PK: ${String.format(Locale.US, "%.1f", state.compositePeakDb)} dB",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (state.isClippingImminent) TigerNeonOrange else Color.White.copy(alpha = 0.8f)
                    )
                }

                // Active Preset / AutoEq Label
                Text(
                    text = state.activePresetName,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 3. Band Selector & Fine-Tune Cards
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(
                items = state.bands,
                key = { index, band -> "${band.id}_$index" }
            ) { index, band ->
                val isSelected = index == state.selectedBandIndex

                Box(
                    modifier = Modifier
                        .width(76.dp)
                        .bounceClick { viewModel.setSelectedBand(index) }
                        .testTag("EqBandCard_$index")
                        .glassEffect(RoundedCornerShape(12.dp))
                        .then(
                            if (isSelected) Modifier.border(1.5.dp, TigerCyberCyan, RoundedCornerShape(12.dp))
                            else Modifier.border(0.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = band.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = band.color,
                            fontSize = 10.sp
                        )

                        Text(
                            text = String.format(Locale.US, "%+.1f", band.gainDb),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            fontSize = 13.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        IconButton(
                            onClick = { viewModel.stepBandGain(index, 0.5f) },
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color.White.copy(alpha = 0.08f), CircleShape)
                                .testTag("EqBandInc_$index")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Boost",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        IconButton(
                            onClick = { viewModel.stepBandGain(index, -0.5f) },
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color.White.copy(alpha = 0.08f), CircleShape)
                                .testTag("EqBandDec_$index")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Cut",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 4. Quick Presets & AutoEq Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onOpenAutoEq,
                colors = ButtonDefaults.buttonColors(containerColor = TigerCyberCyan),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier
                    .height(30.dp)
                    .testTag("EqAutoEqButton")
            ) {
                Icon(
                    imageVector = Icons.Default.Headphones,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("AutoEq", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            if (state.connectedDeviceName.isNotBlank()) {
                OutlinedButton(
                    onClick = viewModel::autoMatchConnectedDevice,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp),
                    border = BorderStroke(1.dp, TigerCyberCyan.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        tint = TigerCyberCyan,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("Match", color = TigerCyberCyan, fontSize = 10.sp)
                }
            }

            OutlinedButton(
                onClick = viewModel::resetToFlat,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier
                    .height(30.dp)
                    .testTag("EqResetFlatButton"),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text("Flat", color = Color.White, fontSize = 10.sp)
            }

            listOf(
                EqualizerPresets.BASS_BOOST,
                EqualizerPresets.VOCAL_BOOSTER,
                EqualizerPresets.TREBLE_BOOST,
                EqualizerPresets.ELECTRONIC,
                EqualizerPresets.ROCK,
                EqualizerPresets.NIGHT_DRIVE
            ).forEach { presetName ->
                val isSelected = state.activePresetName == presetName
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.applyPreset(presetName) },
                    label = { Text(presetName, fontSize = 10.sp) },
                    modifier = Modifier.height(30.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TigerNeonOrange,
                        selectedLabelColor = Color.White,
                        containerColor = DarkCardBg,
                        labelColor = Color.White.copy(alpha = 0.85f)
                    )
                )
            }
        }
    }
}

/**
 * Expansive Main App Studio Equalizer Layout.
 */
@Composable
private fun MainAppStudioEqualizerLayout(
    state: EqualizerHubUiState,
    viewModel: EqualizerHubViewModel,
    onOpenAutoEq: () -> Unit,
    onOpenImport: () -> Unit,
    onClose: (() -> Unit)?
) {
    var showFaderOverlay by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 1. Studio Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = TigerCyberCyan,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "STUDIO EQUALIZER",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 1.2.sp
                    )
                }
                Text(
                    text = "TARGET: ${state.connectedDeviceName.ifBlank { "SPEAKER / WIRED" }.uppercase(Locale.US)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f),
                    letterSpacing = 0.8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (state.connectedDeviceName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "AUTO-MATCH",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TigerCyberCyan,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(TigerCyberCyan.copy(alpha = 0.14f))
                            .clickable { viewModel.autoMatchConnectedDevice() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = state.isEqEnabled,
                    onCheckedChange = viewModel::toggleMasterEq,
                    modifier = Modifier.testTag("MainEqMasterSwitch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = TigerNeonOrange
                    )
                )

                if (onClose != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // DSP Signal Chain Pill
        DspSignalChainPill(state = state)

        Spacer(modifier = Modifier.height(10.dp))

        EngineSourceToggle(
            title = "PARAMETRIC EQ ENGINE",
            isActive = state.activeEngineSource == EqualizerEngineSource.PARAMETRIC_BANDS,
            onToggle = { viewModel.setActiveEngineSource(EqualizerEngineSource.PARAMETRIC_BANDS) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Mode Selector & Actions Bar (TigerPlayer Command Palette Style)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glassEffect(RoundedCornerShape(12.dp))
                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Band Mode Toggle Group
                Row(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    EqualizerBandMode.entries.forEach { mode ->
                        val selected = state.bandMode == mode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selected) TigerNeonOrange else Color.Transparent)
                                .clickable { viewModel.setBandMode(mode) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode.title,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color.White else Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                // AutoEq & Import Tools
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(TigerCyberCyan)
                            .clickable { onOpenAutoEq() }
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Headphones,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "AutoEq",
                                color = Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    IconButton(
                        onClick = onOpenImport,
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Import AutoEq Text",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Interactive Frequency Response Curve
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .glassEffect(RoundedCornerShape(16.dp))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                EqualizerFrequencyCurve(
                    points = state.frequencyCurvePoints,
                    bands = state.bands,
                    isInteractive = true,
                    selectedBandIndex = state.selectedBandIndex,
                    audioFrame = state.audioReactiveFrame,
                    onNodeDragged = { idx, newFreq, newGain ->
                        if (state.bandMode == EqualizerBandMode.AUTOEQ_PARAMETRIC) {
                            viewModel.updateBandFrequency(idx, newFreq)
                        }
                        viewModel.updateBandGain(idx, newGain)
                    },
                    onBandSelected = viewModel::setSelectedBand,
                    modifier = Modifier.fillMaxSize(),
                    curveColor = if (state.isEqEnabled) TigerCyberCyan else Color.Gray
                )

                // Headroom Telemetry
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.isClippingImminent) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Clipping Imminent",
                            tint = TigerNeonOrange,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = "Composite Peak: ${String.format(Locale.US, "%+.1f", state.compositePeakDb)} dB",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (state.isClippingImminent) TigerNeonOrange else EmeraldNeon
                    )
                }

                // Active Preset / Status Bar
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PROFILE: ${state.activePresetName.uppercase(Locale.US)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (state.statusMessage?.isNotBlank() ?: false) {
                        Text(
                            text = state.statusMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = TigerCyberCyan,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 4. Preamp & Headroom Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .glassEffect(RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PREAMP",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Slider(
                value = state.preampDb,
                onValueChange = viewModel::setPreamp,
                valueRange = -15f..6f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = TigerCyberCyan,
                    activeTrackColor = TigerCyberCyan
                )
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = String.format(Locale.US, "%+.1f dB", state.preampDb),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (state.preampDb > 0f) TigerNeonOrange else Color.White
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 5. Glowing Capsule "TUNE FREQUENCY BANDS" Action Button (Same Width as Preamp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .shadow(16.dp, RoundedCornerShape(26.dp), spotColor = TigerCyberCyan)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            TigerCyberCyan.copy(alpha = 0.25f),
                            TigerNeonOrange.copy(alpha = 0.2f),
                            TigerCyberCyan.copy(alpha = 0.25f)
                        )
                    ),
                    shape = RoundedCornerShape(26.dp)
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(TigerCyberCyan, TigerNeonOrange, TigerCyberCyan)
                    ),
                    shape = RoundedCornerShape(26.dp)
                )
                .bounceClick { showFaderOverlay = true }
                .testTag("TuneFrequencyBandsButton"),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = TigerCyberCyan,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "TUNE FREQUENCY BANDS",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 1.2.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 6. Preset Selector Bar
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                OutlinedButton(
                    onClick = viewModel::resetToFlat,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    Text("Flat / Reset", fontSize = 11.sp, color = Color.White)
                }
            }

            items(
                items = EqualizerPresets.ALL_PRESETS,
                key = { it }
            ) { preset ->
                val selected = state.activePresetName == preset
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.applyPreset(preset) },
                    label = { Text(preset, fontSize = 11.sp) },
                    modifier = Modifier.height(32.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TigerNeonOrange,
                        selectedLabelColor = Color.White,
                        containerColor = DarkCardBg,
                        labelColor = Color.White.copy(alpha = 0.8f)
                    )
                )
            }
        }
    }

    if (showFaderOverlay) {
        FrequencyBandsTuningOverlay(
            state = state,
            viewModel = viewModel,
            onDismiss = { showFaderOverlay = false }
        )
    }
}

/**
 * Dedicated Opaque Overlay Sheet hosting optimal-width side-scrollable precision faders.
 */
@Composable
private fun FrequencyBandsTuningOverlay(
    state: EqualizerHubUiState,
    viewModel: EqualizerHubViewModel,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF090B10))
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Overlay Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(TigerCyberCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = TigerCyberCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "FREQUENCY BAND PRECISION TUNING",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "MODE: ${state.bandMode.title} • ${state.bands.size} BANDS",
                                style = MaterialTheme.typography.labelSmall,
                                color = TigerCyberCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.1f), CircleShape)
                            .testTag("CloseFaderOverlayButton")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Done",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                Spacer(modifier = Modifier.height(14.dp))

                // Precision Frequency Response Curve Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .glassEffect(RoundedCornerShape(14.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                ) {
                    EqualizerFrequencyCurve(
                        points = state.frequencyCurvePoints,
                        bands = state.bands,
                        isInteractive = true,
                        selectedBandIndex = state.selectedBandIndex,
                        audioFrame = state.audioReactiveFrame,
                        onNodeDragged = { idx, newFreq, newGain ->
                            if (state.bandMode == EqualizerBandMode.AUTOEQ_PARAMETRIC) {
                                viewModel.updateBandFrequency(idx, newFreq)
                            }
                            viewModel.updateBandGain(idx, newGain)
                        },
                        onBandSelected = viewModel::setSelectedBand,
                        modifier = Modifier.fillMaxSize(),
                        curveColor = if (state.isEqEnabled) TigerCyberCyan else Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Precision Faders Header
                Text(
                    text = "PRECISION FADERS (SIDE-SCROLLABLE)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.5f),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Side-Scrollable Studio Fader Bank with Optimal Width per Fader
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    itemsIndexed(
                        items = state.bands,
                        key = { index, band -> "${band.id}_$index" }
                    ) { index, band ->
                        val isSelected = index == state.selectedBandIndex
                        StudioFaderStrip(
                            band = band,
                            isSelected = isSelected,
                            onSelected = { viewModel.setSelectedBand(index) },
                            onGainChanged = { viewModel.updateBandGain(index, it) },
                            onToggleEnabled = { viewModel.toggleBandEnabled(index) },
                            modifier = Modifier.width(76.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Overlay Footer Action Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = viewModel::resetToFlat,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Reset Flat", color = Color.White, fontSize = 12.sp)
                    }

                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = TigerNeonOrange),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Done Tuning", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

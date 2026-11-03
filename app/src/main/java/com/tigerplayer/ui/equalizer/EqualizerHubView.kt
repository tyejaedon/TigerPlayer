package com.tigerplayer.ui.equalizer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tigerplayer.data.model.HeadphoneSearchResult
import com.tigerplayer.engine.AcousticEnvironmentMode
import com.tigerplayer.engine.FilterType
import com.tigerplayer.ui.theme.TigerCyberCyan
import com.tigerplayer.ui.theme.TigerNeonOrange
import com.tigerplayer.ui.theme.TigerSurfaceCharcoal
import com.tigerplayer.ui.theme.bounceClick
import java.util.Locale

private val EmeraldNeon = Color(0xFF67F7B1)
private val DarkCardBg = Color(0xFF14151B)

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

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("EqualizerHubView"),
        color = Color(0xFF090A0E)
    ) {
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

    // AutoEq Headphone Catalog Dialog / Sheet
    if (showAutoEqDialog) {
        AutoEqSelectorSheet(
            query = uiState.autoEqQuery,
            isSearching = uiState.isSearchingAutoEq,
            searchResults = uiState.autoEqSearchResults,
            isCoverScreen = isCoverScreen,
            onQueryChanged = viewModel::searchAutoEq,
            onSelect = {
                viewModel.applyAutoEqProfile(it)
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
                // Master Bypass Switch
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
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardBg),
            shape = RoundedCornerShape(14.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                EqualizerFrequencyCurve(
                    points = state.frequencyCurvePoints,
                    bands = state.bands,
                    isInteractive = false,
                    selectedBandIndex = state.selectedBandIndex,
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
            itemsIndexed(state.bands) { index, band ->
                val isSelected = index == state.selectedBandIndex

                Card(
                    modifier = Modifier
                        .width(76.dp)
                        .bounceClick { viewModel.setSelectedBand(index) }
                        .testTag("EqBandCard_$index"),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) Color(0xFF1E212B) else DarkCardBg
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = if (isSelected) BorderStroke(1.5.dp, TigerCyberCyan) else BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f))
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

                        // Increment Button (+)
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

                        // Decrement Button (-)
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
            // AutoEq Database Quick Launch
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

            // Reset Flat
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

            // Quick Preset Pills
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
            Column {
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
                    text = "TARGET: ${state.connectedDeviceName.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f),
                    letterSpacing = 0.8.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Master Bypass
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

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Mode Selector & Actions Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EqualizerBandMode.entries.forEach { mode ->
                    val selected = state.bandMode == mode
                    Button(
                        onClick = { viewModel.setBandMode(mode) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selected) TigerNeonOrange else DarkCardBg
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(
                            text = mode.title,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // AutoEq Database Browser
                Button(
                    onClick = onOpenAutoEq,
                    colors = ButtonDefaults.buttonColors(containerColor = TigerCyberCyan),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Headphones,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("AutoEq", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Import Text Button
                IconButton(
                    onClick = onOpenImport,
                    modifier = Modifier
                        .size(34.dp)
                        .background(DarkCardBg, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentPaste,
                        contentDescription = "Import AutoEq Text",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Interactive Frequency Response Curve
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCardBg),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                EqualizerFrequencyCurve(
                    points = state.frequencyCurvePoints,
                    bands = state.bands,
                    isInteractive = true,
                    selectedBandIndex = state.selectedBandIndex,
                    onNodeDragged = { idx, newFreq, newGain ->
                        viewModel.updateBandFrequency(idx, newFreq)
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

                // Active Preset
                Text(
                    text = "PROFILE: ${state.activePresetName.uppercase()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 4. Preamp & Headroom Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkCardBg, RoundedCornerShape(12.dp))
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

        // 5. Vertical Studio Precision Fader Bank
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(state.bands) { index, band ->
                val isSelected = index == state.selectedBandIndex
                StudioFaderStrip(
                    band = band,
                    isSelected = isSelected,
                    onSelected = { viewModel.setSelectedBand(index) },
                    onGainChanged = { viewModel.updateBandGain(index, it) },
                    onToggleEnabled = { viewModel.toggleBandEnabled(index) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

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

            items(EqualizerPresets.ALL_PRESETS) { preset ->
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
}

/**
 * Individual Studio vertical fader strip.
 */
@Composable
private fun StudioFaderStrip(
    band: ParametricBand,
    isSelected: Boolean,
    onSelected: () -> Unit,
    onGainChanged: (Float) -> Unit,
    onToggleEnabled: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(62.dp)
            .fillMaxHeight()
            .clickable(onClick = onSelected),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF1B1E28) else DarkCardBg
        ),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) BorderStroke(1.5.dp, TigerCyberCyan) else BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Frequency Header
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = band.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = band.color,
                    fontSize = 11.sp
                )
                Text(
                    text = String.format(Locale.US, "%+.1f", band.gainDb),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Black,
                    color = if (band.gainDb != 0f) Color.White else Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp
                )
            }

            // Vertical Slider emulation via Box & touch or standard Slider
            Box(
                modifier = Modifier
                    .weight(1f)
                    .width(36.dp),
                contentAlignment = Alignment.Center
            ) {
                // Vertical track
                Box(
                    modifier = Modifier
                        .fillMaxHeight(0.85f)
                        .width(4.dp)
                        .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(2.dp))
                )

                // Stepper Buttons (+ / -)
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(
                        onClick = { onGainChanged((band.gainDb + 0.5f).coerceIn(-15f, 15f)) },
                        modifier = Modifier
                            .size(26.dp)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Boost", tint = Color.White, modifier = Modifier.size(12.dp))
                    }

                    // Visual Gain Indicator Dot
                    val normFraction = ((band.gainDb + 15f) / 30f).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(if (band.gainDb != 0f) TigerCyberCyan else Color.Gray, CircleShape)
                    )

                    IconButton(
                        onClick = { onGainChanged((band.gainDb - 0.5f).coerceIn(-15f, 15f)) },
                        modifier = Modifier
                            .size(26.dp)
                            .background(Color.White.copy(alpha = 0.08f), CircleShape)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Cut", tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                }
            }

            // Bypass / Toggle indicator
            Text(
                text = if (band.isEnabled) "ON" else "BYP",
                style = MaterialTheme.typography.labelSmall,
                color = if (band.isEnabled) EmeraldNeon else Color.Gray,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable(onClick = onToggleEnabled)
                    .padding(2.dp)
            )
        }
    }
}

/**
 * AutoEq Headphone Measurement Selector Sheet.
 */
@Composable
private fun AutoEqSelectorSheet(
    query: String,
    isSearching: Boolean,
    searchResults: List<HeadphoneSearchResult>,
    isCoverScreen: Boolean,
    onQueryChanged: (String) -> Unit,
    onSelect: (HeadphoneSearchResult) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(if (isCoverScreen) 0.95f else 0.85f),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF14151B)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = null,
                            tint = TigerCyberCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AutoEq Profiles",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Search field
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChanged,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search brand or model...", fontSize = 12.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = TigerCyberCyan)
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = TigerCyberCyan,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (isSearching) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = TigerCyberCyan, modifier = Modifier.size(24.dp))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(searchResults) { item ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelect(item) },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E212B)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.modelName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${item.source} • ${item.targetCurve}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TigerCyberCyan,
                                            fontSize = 10.sp
                                        )
                                    }
                                    Text(
                                        text = item.formFactor,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Raw AutoEq Text Importer Dialog.
 */
@Composable
private fun AutoEqImportDialog(
    onImport: (rawText: String, profileName: String) -> Unit,
    onDismiss: () -> Unit
) {
    var rawText by remember { mutableStateOf("") }
    var profileName by remember { mutableStateOf("Imported AutoEq") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF14151B)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Import ParametricEQ Text",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    label = { Text("Profile Name") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    label = { Text("Paste AutoEq / Peace EQ text") },
                    placeholder = {
                        Text(
                            "Preamp: -4.5 dB\nFilter 1: ON PK Fc 28 Hz Gain 4.8 dB Q 1.41\nFilter 2: ON PK Fc 105 Hz Gain -2.1 dB Q 0.71",
                            fontSize = 11.sp
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onImport(rawText, profileName) },
                        colors = ButtonDefaults.buttonColors(containerColor = TigerNeonOrange),
                        enabled = rawText.isNotBlank()
                    ) {
                        Text("Apply EQ", color = Color.White)
                    }
                }
            }
        }
    }
}

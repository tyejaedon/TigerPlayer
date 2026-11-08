package com.tigerplayer.ui.equalizer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.tigerplayer.data.model.HeadphoneSearchResult
import com.tigerplayer.engine.AcousticEnvironmentMode
import com.tigerplayer.ui.theme.TigerCyberCyan
import com.tigerplayer.ui.theme.TigerNeonOrange
import com.tigerplayer.ui.theme.glassEffect
import java.util.Locale

internal val EmeraldNeon = Color(0xFF67F7B1)
internal val DarkCardBg = Color(0xFF14151B)

@Composable
fun AcousticSpacePanel(
    currentMode: AcousticEnvironmentMode,
    onSelectMode: (AcousticEnvironmentMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "ENVIRONMENT SIMULATION",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp,
            color = Color.White.copy(alpha = 0.6f)
        )

        val modes = listOf(
            Triple(
                AcousticEnvironmentMode.STUDIO,
                "Studio",
                "Flat frequency response, zero coloration."
            ),
            Triple(
                AcousticEnvironmentMode.VINYL_WARMTH,
                "Vinyl",
                "Analog warmth, pitch modulation, and surface noise."
            ),
            Triple(
                AcousticEnvironmentMode.CONCERT_HALL,
                "Hall Reverb",
                "Large acoustic space reflection."
            )
        )

        modes.forEach { (mode, title, desc) ->
            val isSelected = currentMode == mode
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassEffect(RoundedCornerShape(14.dp))
                    .border(
                        if (isSelected) 1.5.dp else 0.5.dp,
                        if (isSelected) TigerCyberCyan else Color.White.copy(alpha = 0.08f),
                        RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelectMode(mode) }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) TigerCyberCyan else Color.White
                        )
                        if (isSelected) {
                            Text(
                                text = "ACTIVE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = EmeraldNeon,
                                fontSize = 10.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun StudioFaderStrip(
    band: ParametricBand,
    isSelected: Boolean,
    onSelected: () -> Unit,
    onGainChanged: (Float) -> Unit,
    onToggleEnabled: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentGain by rememberUpdatedState(band.gainDb)
    val currentOnGainChanged by rememberUpdatedState(onGainChanged)

    Box(
        modifier = modifier
            .then(if (modifier == Modifier) Modifier.width(62.dp) else Modifier)
            .fillMaxHeight()
            .glassEffect(RoundedCornerShape(12.dp))
            .border(if (isSelected) 1.5.dp else 0.5.dp, if (isSelected) TigerCyberCyan else Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .clickable(onClick = onSelected)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
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

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
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

                val verticalBias = (-band.gainDb / 15f).coerceIn(-1f, 1f)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .width(36.dp)
                        .padding(vertical = 4.dp)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragStart = { onSelected() },
                                onVerticalDrag = { change, dragAmount ->
                                    val h = size.height.toFloat()
                                    if (h > 0f) {
                                        val deltaDb = -(dragAmount / h) * 30f
                                        val target = (currentGain + deltaDb).coerceIn(-15f, 15f)
                                        currentOnGainChanged(target)
                                        change.consume()
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight(0.9f)
                            .width(6.dp)
                            .background(
                                color = if (band.gainDb != 0f) band.color.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(3.dp)
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(2.dp)
                                .align(Alignment.Center)
                                .background(
                                    color = if (band.gainDb != 0f) band.color.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(1.dp)
                                )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(16.dp)
                            .height(2.dp)
                            .background(Color.White.copy(alpha = 0.5f))
                    )

                    Box(
                        modifier = Modifier
                            .align(BiasAlignment(0f, verticalBias))
                            .size(18.dp)
                            .background(
                                color = if (!band.isEnabled) Color.Gray else if (band.gainDb != 0f) band.color else Color.White,
                                shape = CircleShape
                            )
                            .border(2.dp, Color.Black.copy(alpha = 0.8f), CircleShape)
                    )
                }

                IconButton(
                    onClick = { onGainChanged((band.gainDb - 0.5f).coerceIn(-15f, 15f)) },
                    modifier = Modifier
                        .size(26.dp)
                        .background(Color.White.copy(alpha = 0.08f), CircleShape)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Cut", tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }

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

@Composable
fun AutoEqSelectorSheet(
    query: String,
    isSearching: Boolean,
    searchResults: List<HeadphoneSearchResult>,
    connectedDeviceName: String,
    currentBandMode: EqualizerBandMode,
    isCoverScreen: Boolean,
    onQueryChanged: (String) -> Unit,
    onAutoMatchDevice: () -> Unit,
    onSelect: (item: HeadphoneSearchResult, forceParametric: Boolean) -> Unit,
    onImportRawText: ((rawText: String, profileName: String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var activeTab by remember { mutableIntStateOf(0) } // 0 = Catalog, 1 = Import
    var rawText by remember { mutableStateOf("") }
    var profileName by remember { mutableStateOf("Imported AutoEq") }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(if (isCoverScreen) 0.95f else 0.85f)
                .glassEffect(RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // Header Row
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
                            text = "AutoEq Hub",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tab Switcher Row: [ Catalog | Import ]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassEffect(RoundedCornerShape(10.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (activeTab == 0) TigerCyberCyan.copy(alpha = 0.25f) else Color.Transparent)
                            .border(
                                if (activeTab == 0) 1.dp else 0.dp,
                                if (activeTab == 0) TigerCyberCyan else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { activeTab = 0 }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Headphones,
                                contentDescription = null,
                                tint = if (activeTab == 0) TigerCyberCyan else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Catalog",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (activeTab == 0) TigerCyberCyan else Color.White.copy(alpha = 0.65f),
                                fontSize = 11.sp
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (activeTab == 1) TigerNeonOrange.copy(alpha = 0.25f) else Color.Transparent)
                            .border(
                                if (activeTab == 1) 1.dp else 0.dp,
                                if (activeTab == 1) TigerNeonOrange else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { activeTab = 1 }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = null,
                                tint = if (activeTab == 1) TigerNeonOrange else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Import Text",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (activeTab == 1) TigerNeonOrange else Color.White.copy(alpha = 0.65f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (activeTab == 0) {
                    // TAB 0: CATALOG SEARCH & AUTO-MATCH
                    if (connectedDeviceName.isNotBlank()) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onAutoMatchDevice),
                            color = TigerCyberCyan.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, TigerCyberCyan.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoFixHigh,
                                        contentDescription = null,
                                        tint = TigerCyberCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = connectedDeviceName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "APPLY",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TigerCyberCyan,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChanged,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search brand or model...", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = TigerCyberCyan)
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { onQueryChanged("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                }
                            }
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
                    } else if (searchResults.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "No matches found.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(
                                items = searchResults,
                                key = { it.id }
                            ) { item ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .glassEffect(RoundedCornerShape(10.dp))
                                        .clickable { onSelect(item, true) } // Always default to parametric PEQ
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
                } else {
                    // TAB 1: RAW TEXT IMPORTER
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = profileName,
                            onValueChange = { profileName = it },
                            label = { Text("Profile Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = TigerNeonOrange,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = rawText,
                            onValueChange = { rawText = it },
                            label = { Text("Paste AutoEq / Squig.link text") },
                            placeholder = {
                                Text(
                                    "Preamp: -4.5 dB\nFilter 1: ON PK Fc 28 Hz Gain 4.8 dB Q 1.41",
                                    fontSize = 11.sp
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = TigerNeonOrange,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                onImportRawText?.invoke(rawText, profileName)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TigerNeonOrange),
                            enabled = rawText.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Apply Imported EQ", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DspSignalChainPill(state: EqualizerHubUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassEffect(RoundedCornerShape(8.dp))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "CHAIN",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = Color.White.copy(alpha = 0.4f),
            letterSpacing = 1.sp,
            modifier = Modifier.padding(end = 10.dp)
        )

        val targetName = state.connectedDeviceName.ifBlank { "Speaker" }
        SignalChainBlock(
            icon = Icons.Default.Headphones,
            label = targetName,
            isActive = true
        )

        SignalChainArrow()

        val isSpatial = state.activeEngineSource == EqualizerEngineSource.SPATIAL_NODES
        val isEqActive = state.isEqEnabled
        val dspLabel = when {
            !isEqActive -> "DSP Bypassed"
            isSpatial -> "Spatial Nexus"
            state.autoEqModel != null -> "AutoEq Profile"
            else -> "PEQ Studio"
        }
        val dspBadge = when {
            !isEqActive -> "BYPASSED"
            isSpatial -> "2D MATRIX"
            state.autoEqModel != null -> "AUTOEQ LIVE"
            else -> "LIVE PEQ"
        }
        val dspColor = when {
            !isEqActive -> Color.Gray
            isSpatial -> TigerNeonOrange
            else -> TigerCyberCyan
        }

        SignalChainBlock(
            icon = if (isSpatial) Icons.Default.AutoFixHigh else Icons.Default.GraphicEq,
            label = dspLabel,
            badge = dspBadge,
            isActive = isEqActive,
            color = dspColor
        )

        SignalChainArrow()

        val isSpaceActive = state.environmentMode != AcousticEnvironmentMode.STUDIO
        SignalChainBlock(
            icon = Icons.Default.GraphicEq,
            label = state.environmentMode.name.replace("_", " ").lowercase(Locale.US).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() },
            isActive = true,
            color = if (isSpaceActive) TigerNeonOrange else Color.Gray
        )
    }
}

@Composable
fun SignalChainBlock(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    badge: String? = null,
    isActive: Boolean,
    color: Color = TigerCyberCyan
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .glassEffect(RoundedCornerShape(6.dp))
            .background(if (isActive) color.copy(alpha = 0.15f) else Color.Transparent)
            .border(
                if (isActive) 1.dp else 0.5.dp,
                if (isActive) color.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        if (isActive) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(5.dp))
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isActive) color else Color.Gray,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Column {
            Text(
                text = if (isActive) label else "Bypassed",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isActive) color else Color.Gray
            )
            if (badge != null && isActive) {
                Text(
                    text = badge,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Black,
                    color = color.copy(alpha = 0.85f),
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
fun SignalChainArrow() {
    Text(
        text = "➔",
        fontSize = 12.sp,
        color = Color.White.copy(alpha = 0.3f),
        modifier = Modifier.padding(horizontal = 6.dp)
    )
}

@Composable
fun EngineSourceToggle(
    title: String,
    isActive: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassEffect(RoundedCornerShape(8.dp))
            .background(if (isActive) TigerCyberCyan.copy(alpha = 0.15f) else Color.Transparent)
            .border(1.dp, if (isActive) TigerCyberCyan.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = null,
                tint = if (isActive) TigerCyberCyan else Color.Gray,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isActive) TigerCyberCyan else Color.White.copy(alpha = 0.6f),
                letterSpacing = 1.sp
            )
        }

        if (isActive) {
            Text(
                text = "ACTIVE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = TigerCyberCyan,
                fontSize = 10.sp,
                modifier = Modifier
                    .background(TigerCyberCyan.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        } else {
            Text(
                text = "ENABLE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun TwoWayEngineSourceSwitcher(
    activeSource: EqualizerEngineSource,
    onSelectSource: (EqualizerEngineSource) -> Unit,
    modifier: Modifier = Modifier,
    allowToggleOff: Boolean = true,
    onToggleOff: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glassEffect(RoundedCornerShape(12.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val isPeq = activeSource == EqualizerEngineSource.PARAMETRIC_BANDS
        val isSpatial = activeSource == EqualizerEngineSource.SPATIAL_NODES

        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(9.dp))
                .background(if (isPeq) TigerCyberCyan.copy(alpha = 0.22f) else Color.Transparent)
                .border(
                    if (isPeq) 1.dp else 0.dp,
                    if (isPeq) TigerCyberCyan else Color.Transparent,
                    RoundedCornerShape(9.dp)
                )
                .clickable {
                    if (isPeq && allowToggleOff) {
                        if (onToggleOff != null) onToggleOff()
                        else onSelectSource(EqualizerEngineSource.PARAMETRIC_BANDS)
                    } else {
                        onSelectSource(EqualizerEngineSource.PARAMETRIC_BANDS)
                    }
                }
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = if (isPeq) TigerCyberCyan else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "PEQ Studio",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isPeq) TigerCyberCyan else Color.White.copy(alpha = 0.65f),
                    fontSize = 11.sp
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(9.dp))
                .background(if (isSpatial) TigerNeonOrange.copy(alpha = 0.22f) else Color.Transparent)
                .border(
                    if (isSpatial) 1.dp else 0.dp,
                    if (isSpatial) TigerNeonOrange else Color.Transparent,
                    RoundedCornerShape(9.dp)
                )
                .clickable {
                    if (isSpatial && allowToggleOff) {
                        if (onToggleOff != null) onToggleOff()
                        else onSelectSource(EqualizerEngineSource.PARAMETRIC_BANDS)
                    } else {
                        onSelectSource(EqualizerEngineSource.SPATIAL_NODES)
                    }
                }
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoFixHigh,
                    contentDescription = null,
                    tint = if (isSpatial) TigerNeonOrange else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Spatial Nexus",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSpatial) TigerNeonOrange else Color.White.copy(alpha = 0.65f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

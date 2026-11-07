package com.tigerplayer.ui.coverscreen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tigerplayer.ui.equalizer.EqualizerFrequencyCurve
import com.tigerplayer.ui.equalizer.EqualizerHubViewModel
import com.tigerplayer.ui.equalizer.EqualizerPresets
import com.tigerplayer.ui.theme.TigerCyberCyan
import com.tigerplayer.ui.theme.TigerNeonOrange
import com.tigerplayer.ui.theme.bounceClick
import com.tigerplayer.ui.theme.glassEffect

private val EmeraldNeon = Color(0xFF67F7B1)
private val CardBackground = Color(0xFF13141A)

/**
 * Cover screen home card for the Equalizer Hub.
 * Provides quick telemetry, master bypass switch, frequency curve preview, and launch trigger.
 */
@Composable
fun CoverScreenEqHubCard(
    onOpenEqHub: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EqualizerHubViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CoverScreenEqHubCardContent(
        activePreset = uiState.activePresetName,
        isEqEnabled = uiState.isEqEnabled,
        deviceName = uiState.connectedDeviceName,
        autoEqModel = uiState.autoEqModel,
        compositePeakDb = uiState.compositePeakDb,
        curvePoints = uiState.frequencyCurvePoints,
        bands = uiState.bands,
        onToggleMasterEq = viewModel::toggleMasterEq,
        onQuickPreset = viewModel::applyPreset,
        onClick = onOpenEqHub,
        modifier = modifier
    )
}

/**
 * Stateless rendering component for [CoverScreenEqHubCard].
 */
@Composable
fun CoverScreenEqHubCardContent(
    activePreset: String,
    isEqEnabled: Boolean,
    deviceName: String,
    autoEqModel: String?,
    compositePeakDb: Float,
    curvePoints: List<androidx.compose.ui.geometry.Offset>,
    bands: List<com.tigerplayer.ui.equalizer.ParametricBand>,
    onToggleMasterEq: (Boolean) -> Unit,
    onQuickPreset: (String) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .glassEffect(RoundedCornerShape(18.dp))
            .border(
                BorderStroke(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(
                            TigerNeonOrange.copy(alpha = 0.5f),
                            TigerCyberCyan.copy(alpha = 0.3f),
                            Color.White.copy(alpha = 0.08f)
                        )
                    )
                ),
                RoundedCornerShape(18.dp)
            )
            .bounceClick(onClick = onClick)
            .testTag(CoverScreenTestTags.HOME_EQ_HUB_CARD)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Header: Icon + Title + Device + Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .background(TigerNeonOrange.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = TigerNeonOrange,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "EQUALIZER",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 0.8.sp,
                                fontSize = 11.sp
                            )
                        }

                        Text(
                            text = deviceName.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = TigerCyberCyan,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = isEqEnabled,
                        onCheckedChange = onToggleMasterEq,
                        modifier = Modifier
                            .scale(0.7f)
                            .testTag(CoverScreenTestTags.HOME_EQ_MASTER_SWITCH),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = TigerNeonOrange,
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.DarkGray
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Mini Frequency Curve Visualizer Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .glassEffect(RoundedCornerShape(10.dp))
                    .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
            ) {


                // Headroom Badge
                Text(
                    text = if (isEqEnabled) """PK ${String.format("%.1f", compositePeakDb)} dB""" else "BYPASS",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isEqEnabled) EmeraldNeon else Color.Gray,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Active Preset & Tweak Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (autoEqModel != null) {
                        Icon(
                            imageVector = Icons.Rounded.Headphones,
                            contentDescription = null,
                            tint = TigerCyberCyan,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Text(
                        text = activePreset,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = TigerNeonOrange,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "TWEAK EQ →",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        color = TigerNeonOrange,
                        fontSize = 10.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

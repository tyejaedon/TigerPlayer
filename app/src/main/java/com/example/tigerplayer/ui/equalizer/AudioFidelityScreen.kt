package com.example.tigerplayer.ui.equalizer

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tigerplayer.engine.EqUiState
import com.example.tigerplayer.service.PeqProfile
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.aardBlue
import com.example.tigerplayer.ui.theme.bounceClick
import kotlin.math.log10

// Audiophile Colors
private val BitPerfectGold = Color(0xFFFFD700)

@Composable
fun AudioFidelityScreen(
    eqState: EqUiState,
    onToggleBitPerfect: () -> Unit,
    onSelectProfile: (PeqProfile) -> Unit,
    onClose: () -> Unit
) {
    val isBp = eqState.isBitPerfect
     val dspBlue = MaterialTheme.aardBlue


    // Smooth color transitions based on mode
    val primaryColor by animateColorAsState(
        targetValue = if (isBp) BitPerfectGold else dspBlue,
        animationSpec = tween(500), label = "ModeColor"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .padding(24.dp)
            .statusBarsPadding()
    ) {
        // --- HEADER ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "AUDIO FIDELITY",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
            IconButton(onClick = onClose, modifier = Modifier.background(Color.White.copy(alpha = 0.1f), CircleShape)) {
                Icon(WitcherIcons.Close, "Close", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // --- THE ENGINE TOGGLE CARD ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = if (isBp) 32.dp else 0.dp,
                    shape = RoundedCornerShape(24.dp),
                    spotColor = BitPerfectGold.copy(alpha = 0.5f)
                )
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(primaryColor.copy(alpha = 0.15f), Color.Transparent)))
                .border(1.dp, primaryColor.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                .bounceClick { onToggleBitPerfect() }
                .padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isBp) "BIT-PERFECT OUTPUT" else "DYNAMICS PROCESSING",
                        style = MaterialTheme.typography.titleMedium,
                        color = primaryColor,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isBp) "Bypassing Android Mixer. Sending raw FLAC frames directly to DAC."
                        else "Applying AutoEq Parametric DSP. Audio offload disabled.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Switch(
                    checked = isBp,
                    onCheckedChange = { onToggleBitPerfect() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = BitPerfectGold,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color.White.copy(alpha = 0.2f)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // --- DSP CONTROLS (Only visible if Bit-Perfect is OFF) ---
        AnimatedVisibility(
            visible = !isBp,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Text(
                    text = "PARAMETRIC EQ VISUALIZER",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.4f),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // The Premium PEQ Visualizer
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(16.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    PeqVisualizerCanvas(
                        profile = eqState.selectedProfile,
                        color = dspBlue
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "ACOUSTIC PROFILES",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.4f),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(eqState.availableProfiles) { profile ->
                        val isSelected = eqState.selectedProfile?.name == profile.name
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) dspBlue.copy(alpha = 0.2f) else Color.Transparent)
                                .border(1.dp, if (isSelected) dspBlue.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                .clickable { onSelectProfile(profile) }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = profile.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isSelected) dspBlue else Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Preamp: ${profile.preamp} dB • ${profile.bands.size} Bands",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 🎨 Plots the PEQ bands logarithmically just like high-end studio software.
 */
@Composable
fun PeqVisualizerCanvas(profile: PeqProfile?, color: Color) {
    if (profile == null || profile.bands.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No Profile Loaded", color = Color.White.copy(alpha = 0.3f))
        }
        return
    }

    // Animation for smooth graph loading
    val animProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(1000), label = "GraphAnim"
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerY = height / 2

        // Draw 0dB Reference Line
        drawLine(
            color = Color.White.copy(alpha = 0.2f),
            start = Offset(0f, centerY),
            end = Offset(width, centerY),
            strokeWidth = 1.dp.toPx()
        )

        // Math constraints
        val minFreq = 20f
        val maxFreq = 20000f
        val minLog = log10(minFreq)
        val maxLog = log10(maxFreq)
        val logRange = maxLog - minLog

        // UI assumes max boost/cut of +/- 15dB
        val dbScale = 15f

        val path = Path()
        var isFirst = true

        // Sort bands chronologically by frequency
        val sortedBands = profile.bands.sortedBy { it.frequency }

        sortedBands.forEach { band ->
            // Map Frequency to Logarithmic X Axis
            val freqLog = log10(band.frequency.coerceIn(minFreq, maxFreq))
            val x = ((freqLog - minLog) / logRange) * width

            // Map Gain to Y Axis (Inverted because Y grows downwards)
            val gainOffset = (band.gain / dbScale) * centerY
            val y = centerY - (gainOffset * animProgress)

            if (isFirst) {
                path.moveTo(0f, centerY) // Start at 0dB
                path.lineTo(x.toFloat(), y)
                isFirst = false
            } else {
                path.lineTo(x.toFloat(), y)
            }

            // Draw anchor points for the filters
            drawCircle(
                color = color,
                radius = 4.dp.toPx(),
                center = Offset(x.toFloat(), y)
            )
        }

        // Finish path to end of screen
        path.lineTo(width, centerY)

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}
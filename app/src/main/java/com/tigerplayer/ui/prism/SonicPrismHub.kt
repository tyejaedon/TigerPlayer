package com.tigerplayer.ui.prism

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tigerplayer.data.local.PrismSpectralAnalysis
import com.tigerplayer.ui.theme.TigerCyberCyan
import com.tigerplayer.ui.theme.TigerNeonOrange
import com.tigerplayer.ui.theme.TigerSurfaceFloating
import com.tigerplayer.ui.theme.TigerToxicLime
import java.util.Locale

private val PrismText = Color(0xFFE9EEF8)

@Composable
fun PrismInlineMixer(
    state: PrismUiState,
    onVocalsChange: (Float) -> Unit,
    onBeatsChange: (Float) -> Unit,
    onInstrumentsChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onEnabledChange: ((Boolean) -> Unit)? = null,
    onPresetSelected: ((PrismPreset) -> Unit)? = null,
    onResetRequested: (() -> Unit)? = null,
    onSpectralAnalysisChange: ((PrismSpectralAnalysis) -> Unit)? = null,
    faderHeight: Dp = 330.dp
) {
    val dominantBandIndex = state.spectralBands.indices.maxByOrNull { state.spectralBands[it] } ?: 0
    val dominantLabel = when (dominantBandIndex) {
        0 -> "60 Hz"
        1 -> "250 Hz"
        2 -> "1 kHz"
        3 -> "2.5 kHz"
        4 -> "6 kHz"
        else -> "12 kHz"
    }
    val analysisProfileLabel = remember(state.observedAnalysisMode, state.analysisCostMicros) {
        val modeLabel = if (state.observedAnalysisMode == PrismSpectralAnalysis.FFT) "FFT" else "Bandpass"
        val costMs = state.analysisCostMicros.coerceAtLeast(0f) / 1_000f
        "Profiler: $modeLabel ${String.format(Locale.US, "%.2f", costMs)} ms/window"
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (onEnabledChange != null || onResetRequested != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (state.isPrismEnabled) "Prism Enabled" else "Prism Bypassed",
                    color = PrismText.copy(alpha = 0.84f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    onResetRequested?.let {
                        IconButton(
                            onClick = it,
                            modifier = Modifier.testTag(PrismTestTags.RESET_BUTTON)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset Prism Mix",
                                tint = PrismText.copy(alpha = 0.86f)
                            )
                        }
                    }
                    onEnabledChange?.let {
                        Switch(
                            checked = state.isPrismEnabled,
                            onCheckedChange = it,
                            modifier = Modifier.testTag(PrismTestTags.ENABLE_SWITCH),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TigerCyberCyan,
                                checkedTrackColor = TigerCyberCyan.copy(alpha = 0.35f)
                            )
                        )
                    }
                }
            }
        }

        if (onPresetSelected != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    PrismPreset.BALANCED,
                    PrismPreset.VOCAL_FOCUS,
                    PrismPreset.BEAT_PUNCH,
                    PrismPreset.INSTRUMENTAL
                ).forEach { preset ->
                    val selected = state.preset == preset
                    AssistChip(
                        onClick = { onPresetSelected(preset) },
                        modifier = Modifier.testTag(PrismTestTags.presetChip(preset)),
                        label = { Text(preset.displayName) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (selected) TigerCyberCyan.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.06f),
                            labelColor = if (selected) TigerCyberCyan else PrismText.copy(alpha = 0.84f)
                        )
                    )
                }
            }
        }

        if (onSpectralAnalysisChange != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Analysis",
                    color = PrismText.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                AssistChip(
                    onClick = { onSpectralAnalysisChange(PrismSpectralAnalysis.FFT) },
                    modifier = Modifier.testTag(PrismTestTags.ANALYSIS_FFT_CHIP),
                    label = { Text("FFT") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (state.spectralAnalysis == PrismSpectralAnalysis.FFT) {
                            TigerCyberCyan.copy(alpha = 0.22f)
                        } else {
                            Color.White.copy(alpha = 0.06f)
                        },
                        labelColor = if (state.spectralAnalysis == PrismSpectralAnalysis.FFT) {
                            TigerCyberCyan
                        } else {
                            PrismText.copy(alpha = 0.84f)
                        }
                    )
                )
                AssistChip(
                    onClick = { onSpectralAnalysisChange(PrismSpectralAnalysis.BANDPASS) },
                    modifier = Modifier.testTag(PrismTestTags.ANALYSIS_BANDPASS_CHIP),
                    label = { Text("Bandpass") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (state.spectralAnalysis == PrismSpectralAnalysis.BANDPASS) {
                            TigerCyberCyan.copy(alpha = 0.22f)
                        } else {
                            Color.White.copy(alpha = 0.06f)
                        },
                        labelColor = if (state.spectralAnalysis == PrismSpectralAnalysis.BANDPASS) {
                            TigerCyberCyan
                        } else {
                            PrismText.copy(alpha = 0.84f)
                        }
                    )
                )
            }
            Text(
                text = analysisProfileLabel,
                modifier = Modifier.testTag(PrismTestTags.ANALYSIS_PROFILE_LABEL),
                color = PrismText.copy(alpha = 0.68f),
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 0.3.sp
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(PrismTestTags.SPECTRAL_SECTION),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Dominant: $dominantLabel",
                modifier = Modifier.testTag(PrismTestTags.DOMINANT_BAND_LABEL),
                color = PrismText.copy(alpha = 0.82f),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                state.spectralBands.forEachIndexed { index, value ->
                    val barHeight = (value.coerceIn(0f, 1f) * 34f + 6f).dp
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(barHeight)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (index == dominantBandIndex) TigerCyberCyan.copy(alpha = 0.9f)
                                else PrismText.copy(alpha = 0.28f)
                            )
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PrismFader(
                label = "VOCALS",
                value = state.vocals,
                activeColor = TigerNeonOrange,
                onValueChange = onVocalsChange,
                faderHeight = faderHeight
            )
            PrismFader(
                label = "BEATS",
                value = state.beats,
                activeColor = TigerCyberCyan,
                onValueChange = onBeatsChange,
                faderHeight = faderHeight
            )
            PrismFader(
                label = "MELODY",
                value = state.instruments,
                activeColor = TigerToxicLime,
                onValueChange = onInstrumentsChange,
                faderHeight = faderHeight
            )
        }
    }
}

@Composable
private fun PrismFader(
    label: String,
    value: Float,
    activeColor: Color,
    onValueChange: (Float) -> Unit,
    faderHeight: Dp = 330.dp
) {
    val haptic = LocalHapticFeedback.current
    val isMuted = value <= 0.001f
    val tubeColor = if (isMuted) TigerSurfaceFloating else activeColor

    LaunchedEffect(isMuted) {
        if (isMuted) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "${(value * 100f).toInt()}%",
            color = if (isMuted) TigerSurfaceFloating else PrismText,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )

        NeonVerticalFader(
            value = value,
            color = tubeColor,
            onValueChange = onValueChange,
            height = faderHeight
        )

        Text(
            text = label,
            color = if (isMuted) TigerSurfaceFloating else PrismText,
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun NeonVerticalFader(
    value: Float,
    color: Color,
    onValueChange: (Float) -> Unit,
    height: Dp = 330.dp
) {
    val density = LocalDensity.current
    val widthPx = with(density) { 76.dp.toPx() }
    val heightPx = with(density) { height.toPx() }
    val knobRadius = with(density) { 16.dp.toPx() }
    val glowPaint = remember {
        android.graphics.Paint().apply {
            isAntiAlias = true
            style = android.graphics.Paint.Style.STROKE
            strokeCap = android.graphics.Paint.Cap.ROUND
        }
    }

    Canvas(
        modifier = Modifier
            .size(width = 76.dp, height = height)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, _ ->
                        val dragHeight = size.height.toFloat()
                        val clampedY = change.position.y.coerceIn(0f, dragHeight)
                        val newValue = 1f - (clampedY / dragHeight)
                        onValueChange(newValue.coerceIn(0f, 1f))
                    },
                    onDragEnd = {
                        if (value < 0.015f) onValueChange(0f)
                    }
                )
            }
    ) {
        val centerX = widthPx * 0.5f
        val filledY = heightPx * (1f - value.coerceIn(0f, 1f))

        drawRoundRect(
            color = Color.White.copy(alpha = 0.06f),
            topLeft = Offset(centerX - 7.dp.toPx(), 0f),
            size = Size(14.dp.toPx(), heightPx),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx(), 10.dp.toPx())
        )

        glowPaint.color = color.copy(alpha = 0.9f).toArgbCompat()
        glowPaint.strokeWidth = 12.dp.toPx()
        glowPaint.maskFilter = BlurMaskFilter(18f, BlurMaskFilter.Blur.NORMAL)

        drawContext.canvas.nativeCanvas.drawLine(
            centerX,
            heightPx,
            centerX,
            filledY,
            glowPaint
        )

        drawLine(
            color = color,
            start = Offset(centerX, heightPx),
            end = Offset(centerX, filledY),
            strokeWidth = 4.dp.toPx()
        )

        drawCircle(
            color = color.copy(alpha = 0.22f),
            radius = knobRadius * 1.65f,
            center = Offset(centerX, filledY)
        )
        drawCircle(
            color = color,
            radius = knobRadius,
            center = Offset(centerX, filledY)
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.55f),
            radius = knobRadius,
            center = Offset(centerX, filledY),
            style = Stroke(width = 1.2.dp.toPx())
        )
    }
}

private fun Color.toArgbCompat(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).toInt().coerceIn(0, 255),
        (red * 255).toInt().coerceIn(0, 255),
        (green * 255).toInt().coerceIn(0, 255),
        (blue * 255).toInt().coerceIn(0, 255)
    )
}



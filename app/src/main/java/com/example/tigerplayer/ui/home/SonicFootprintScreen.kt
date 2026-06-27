package com.example.tigerplayer.ui.home

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tigerplayer.ui.theme.TigerCyberCyan
import com.example.tigerplayer.ui.theme.TigerNeonOrange
import com.example.tigerplayer.ui.theme.WitcherIcons

@Composable
fun SonicFootprintScreen(
    onBackClick: () -> Unit,
    viewModel: SonicFootprintViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val horizontalFilterScroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackClick) {
                Icon(imageVector = WitcherIcons.Back, contentDescription = "Back")
            }
            Column {
                Text(
                    text = "SONIC FOOTPRINT",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "Your local listening DNA",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(horizontalFilterScroll),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            viewModel.availableFilters.forEach { filter ->
                val selected = filter == uiState.selectedFilter
                Surface(
                    modifier = Modifier,
                    shape = RoundedCornerShape(999.dp),
                    color = if (selected) {
                        TigerNeonOrange.copy(alpha = 0.2f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    },
                    tonalElevation = if (selected) 3.dp else 0.dp,
                    onClick = { viewModel.setFilter(filter) }
                ) {
                    Text(
                        text = filter.label,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = if (selected) TigerNeonOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            TigerCyberCyan.copy(alpha = 0.08f),
                            TigerNeonOrange.copy(alpha = 0.07f),
                            Color.Transparent
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (uiState.isEmpty) {
                Text(
                    text = "No listening history yet.\nPlay a few tracks to forge your footprint.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                SonicRadarChart(
                    axes = uiState.axes,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Total plays analyzed: ${uiState.totalPlays}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = String.format(
                "%s listening: %d min (%.1f%% of lifetime %d min)",
                uiState.selectedFilter.label,
                uiState.selectedMinutes,
                uiState.globalListeningSharePercent,
                uiState.lifetimeMinutes
            ),
            style = MaterialTheme.typography.bodySmall,
            color = TigerCyberCyan
        )

        Spacer(modifier = Modifier.height(10.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            uiState.axes.forEach { axis ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                if (axis.label == "Acoustic" || axis.label == "Vocal") TigerNeonOrange else TigerCyberCyan,
                                CircleShape
                            )
                    )
                    Text(
                        text = "  ${axis.label}: ${(axis.value * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SonicRadarChart(
    axes: List<SonicFootprintAxis>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (axes.isEmpty()) return@Canvas

        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = (size.minDimension * 0.34f)
        val levels = 5
        val angleStep = (Math.PI * 2f / axes.size).toFloat()

        fun pointAt(axisIndex: Int, factor: Float): Offset {
            val angle = -Math.PI.toFloat() / 2f + axisIndex * angleStep
            return Offset(
                x = center.x + kotlin.math.cos(angle) * radius * factor,
                y = center.y + kotlin.math.sin(angle) * radius * factor
            )
        }

        repeat(levels) { level ->
            val levelFactor = (level + 1) / levels.toFloat()
            val web = Path().apply {
                axes.indices.forEach { i ->
                    val p = pointAt(i, levelFactor)
                    if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
                close()
            }
            drawPath(
                path = web,
                color = TigerCyberCyan.copy(alpha = 0.16f),
                style = Stroke(width = 1.2f)
            )
        }

        axes.indices.forEach { i ->
            drawLine(
                color = TigerCyberCyan.copy(alpha = 0.24f),
                start = center,
                end = pointAt(i, 1f),
                strokeWidth = 1.2f
            )
        }

        val dataPath = Path().apply {
            axes.forEachIndexed { i, axis ->
                val p = pointAt(i, axis.value.coerceIn(0f, 1f))
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }

        drawPath(
            path = dataPath,
            brush = Brush.radialGradient(
                colors = listOf(
                    TigerNeonOrange.copy(alpha = 0.44f),
                    TigerCyberCyan.copy(alpha = 0.28f),
                    Color.Transparent
                ),
                center = center,
                radius = radius
            )
        )

        drawPath(
            path = dataPath,
            color = TigerNeonOrange,
            style = Stroke(width = 4f, cap = StrokeCap.Round)
        )

        drawContext.canvas.nativeCanvas.apply {
            val glowPaint = android.graphics.Paint().apply {
                color = TigerCyberCyan.toArgb()
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 7f
                isAntiAlias = true
                maskFilter = BlurMaskFilter(16f, BlurMaskFilter.Blur.NORMAL)
            }
            drawPath(dataPath.asAndroidPath(), glowPaint)
        }

        axes.forEachIndexed { i, axis ->
            val labelPoint = pointAt(i, 1.12f)
            drawContext.canvas.nativeCanvas.apply {
                val labelPaint = android.graphics.Paint().apply {
                    color = if (i % 2 == 0) TigerNeonOrange.toArgb() else TigerCyberCyan.toArgb()
                    textSize = 30f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isFakeBoldText = true
                    isAntiAlias = true
                }
                drawText(axis.label.uppercase(), labelPoint.x, labelPoint.y, labelPaint)
            }
        }
    }
}


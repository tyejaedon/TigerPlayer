package com.example.tigerplayer.ui.player

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tigerplayer.ui.prism.PrismUiState

@Composable
fun PrismInlineMixer(
    state: PrismUiState,
    onVocalsChange: (Float) -> Unit,
    onBeatsChange: (Float) -> Unit,
    onInstrumentsChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        Text(
            text = "SONIC PRISM",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.4.sp
        )

        PrismInlineSlider("VOCALS", state.vocals, Color(0xFFFF6A00), onVocalsChange)
        PrismInlineSlider("BEATS", state.beats, Color(0xFF00E5FF), onBeatsChange)
        PrismInlineSlider("INSTRUMENTS", state.instruments, Color(0xFF39FF14), onInstrumentsChange)
    }
}

@Composable
private fun PrismInlineSlider(
    label: String,
    value: Float,
    accent: Color,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, color = accent, fontWeight = FontWeight.Bold)
            Text(text = "${(value * 100).toInt()}%", color = Color.White.copy(alpha = 0.8f))
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            )
        )
    }
}

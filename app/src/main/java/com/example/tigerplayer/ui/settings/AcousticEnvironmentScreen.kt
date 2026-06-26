package com.example.tigerplayer.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tigerplayer.engine.AcousticEnvironmentMode
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect

private data class EnvironmentOption(
    val mode: AcousticEnvironmentMode,
    val title: String,
    val subtitle: String,
    val accent: Color
)

@Composable
fun AcousticEnvironmentScreen(
    selectedMode: AcousticEnvironmentMode,
    onModeSelected: (AcousticEnvironmentMode) -> Unit,
    onBack: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val options = listOf(
        EnvironmentOption(
            mode = AcousticEnvironmentMode.OFF,
            title = "Neutral",
            subtitle = "Bit-clean path. No environmental coloration.",
            accent = Color(0xFF9AA0A6)
        ),
        EnvironmentOption(
            mode = AcousticEnvironmentMode.VINYL_WARMTH,
            title = "Vinyl Warmth",
            subtitle = "Subtle even harmonics + analog noise floor.",
            accent = Color(0xFFFFA726)
        ),
        EnvironmentOption(
            mode = AcousticEnvironmentMode.CONCERT_HALL,
            title = "Concert Hall",
            subtitle = "Light Schroeder room bloom with spatial depth.",
            accent = Color(0xFF00E5FF)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = WitcherIcons.Back,
                        contentDescription = "Back"
                    )
                }
                Column {
                    Text(
                        text = "ACOUSTIC ENVIRONMENTS",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Choose the room your music breathes in.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            options.forEach { option ->
                val selected = option.mode == selectedMode
                val borderColor = if (selected) option.accent else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassEffect(RoundedCornerShape(20.dp))
                        .background(
                            brush = Brush.linearGradient(
                                listOf(
                                    option.accent.copy(alpha = if (selected) 0.14f else 0.04f),
                                    Color.Transparent
                                )
                            )
                        )
                        .border(1.dp, borderColor, RoundedCornerShape(20.dp))
                        .bounceClick {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onModeSelected(option.mode)
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onModeSelected(option.mode)
                        }
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = option.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) option.accent else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = option.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(option.accent, CircleShape)
                    )
                }
            }
        }
    }
}


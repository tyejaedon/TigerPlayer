package com.tigerplayer.ui.home.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Castle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tigerplayer.engine.AcousticEnvironmentMode
import com.tigerplayer.ui.home.SectionTitle
import com.tigerplayer.ui.theme.TigerCyberCyan

data class MoodItem(val mode: AcousticEnvironmentMode, val title: String, val icon: ImageVector)

@Composable
fun AcousticMoodCarousel(
    currentMode: AcousticEnvironmentMode,
    onSelectMode: (AcousticEnvironmentMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val modes = listOf(
        MoodItem(AcousticEnvironmentMode.STUDIO, "Studio Flat", Icons.Default.GraphicEq),
        MoodItem(AcousticEnvironmentMode.VINYL_WARMTH, "Vinyl Warmth", Icons.Default.Album),
        MoodItem(AcousticEnvironmentMode.CONCERT_HALL, "Concert Hall", Icons.Default.Castle)
    )

    Column(modifier = modifier.fillMaxWidth()) {
        SectionTitle("ACOUSTIC MOODS")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(modes) { item ->
                val isSelected = currentMode == item.mode
                Card(
                    modifier = Modifier
                        .width(140.dp)
                        .height(110.dp)
                        .clickable { onSelectMode(item.mode) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) TigerCyberCyan.copy(alpha = 0.1f) else Color(0xFF1B1E28)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        if (isSelected) 1.5.dp else 0.5.dp,
                        if (isSelected) TigerCyberCyan else Color.White.copy(alpha = 0.08f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            tint = if (isSelected) TigerCyberCyan else Color.Gray,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            if (isSelected) {
                                Text(
                                    text = "ACTIVE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Black,
                                    color = TigerCyberCyan,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.example.tigerplayer.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.tigerplayer.data.repository.ArtistDetails
import com.example.tigerplayer.ui.theme.aardBlue
import com.example.tigerplayer.ui.theme.glassEffect


fun formatMinutesListened(totalMinutes: Int): String {
    return when {
        totalMinutes == 0 -> "0 MINS"
        totalMinutes < 60 -> "$totalMinutes MINS"
        else -> {
            val hours = totalMinutes / 60
            val remainingMins = totalMinutes % 60
            if (remainingMins == 0) "${hours}H" else "${hours}H ${remainingMins}M"
        }
    }
}
@Composable
fun ArtistHeroImage(model: Any?, artistName: String) {
    AsyncImage(
        model = model,
        contentDescription = "Image of $artistName",
        // Fallback to the Tiger logo if Last.fm fails
        fallback = painterResource(com.example.tigerplayer.R.drawable.ic_tiger_logo),
        error = painterResource(com.example.tigerplayer.R.drawable.ic_tiger_logo),
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
            .padding(16.dp)
            // Use glassEffect instead of hard shadows to avoid boxy artifacts
            .glassEffect(MaterialTheme.shapes.extraLarge)
            .clip(MaterialTheme.shapes.extraLarge),
        contentScale = ContentScale.Crop
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun ArtistVanguardStats(profile: ArtistDetails?, accentColor: Color) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
            .glassEffect(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.2f), MaterialTheme.shapes.large)
            .border(1.dp, accentColor.copy(alpha = 0.2f), MaterialTheme.shapes.large)
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    text = "VANGUARD DOSSIER",
                    style = MaterialTheme.typography.labelLarge,
                    color = accentColor,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Global Resonance: ${profile?.popularity ?: 0}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val totalMinutes = profile?.minutesListened ?: 0
            ArtistMetadataBadge(
                text = formatMinutesListened(totalMinutes),
                textColor = if (totalMinutes >= 60) MaterialTheme.aardBlue else Color.Gray,
                isHighlight = totalMinutes >= 600
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (profile?.bio != null) {
            Text(
                text = profile.bio,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
            )

            if (profile.genres.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    profile.genres.take(5).forEach { genre ->
                        GenrePill(genre, accentColor)
                    }
                }
            }
        } else {
            ConsultingArchivesState()
        }
    }
}

@Composable
fun ConsultingArchivesState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- THE PULSE: A low-profile indicator ---
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth(0.6f) // Centered and not full-width for a sleeker look
                .height(2.dp)
                .clip(CircleShape),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- THE CHANT: Themed loading text ---
        Text(
            text = "CONSULTING THE ARCHIVES...",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )

        Text(
            text = "Summoning artist lore and global resonance data.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun ArtistMetadataBadge(
    text: String,
    textColor: Color,
    isHighlight: Boolean
) {
    Surface(
        color = if (isHighlight) textColor.copy(alpha = 0.2f) else Color.Transparent,
        shape = CircleShape,
        border = BorderStroke(1.dp, if (isHighlight) textColor else textColor.copy(alpha = 0.3f))
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun GenrePill(name: String, accentColor: Color) {
    Surface(
        shape = CircleShape,
        color = accentColor.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.2f))
    ) {
        Text(
            text = name.uppercase(),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = accentColor,
            letterSpacing = 1.sp
        )
    }
}



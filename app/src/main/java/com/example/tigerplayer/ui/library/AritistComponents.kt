package com.example.tigerplayer.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.tigerplayer.data.repository.ArtistDetails
import com.example.tigerplayer.ui.player.LibraryArtist
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ArtistSearchRow(artist: LibraryArtist, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick { onClick() }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = WitcherIcons.Artist,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = artist.name,
            modifier = Modifier.padding(start = 16.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
@Composable
fun ArtistRow(artist: LibraryArtist, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(AardBlue.copy(0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(WitcherIcons.Artist, null, tint = AardBlue)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "${artist.trackCount} Tracks • ${artist.albumCount} Albums",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ArtistGenreCloud(genres: List<String>) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        genres.take(4).forEach { genre ->
            ArtistMetadataBadge(text = genre, isHighlight = true)
        }
    }
}

@Composable
fun ArtistVanguardStats(profile: ArtistDetails?) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
            .glassEffect(MaterialTheme.shapes.large)
            .background(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                MaterialTheme.shapes.large
            )
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "VANGUARD STATS",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )

            val formattedListeners = NumberFormat.getNumberInstance(Locale.US).format(profile?.popularity ?: 0)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ArtistMetadataBadge(
                    text = "LISTENERS: $formattedListeners",
                    textColor = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val bio = profile?.bio
        if (!bio.isNullOrBlank()) {
            Text(
                text = bio,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 26.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Consulting the Archives...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    trackColor = Color.Transparent
                )
            }
        }
    }
}
@Composable
fun ArtistMetadataBadge(
    text: String,
    isHighlight: Boolean = false,
    textColor: Color = MaterialTheme.colorScheme.primary
) {
    Surface(
        color = if (isHighlight) textColor.copy(alpha = 0.2f) else textColor.copy(alpha = 0.1f),
        shape = CircleShape,
        border = BorderStroke(
            width = 1.dp,
            color = if (isHighlight) textColor else textColor.copy(alpha = 0.3f)
        )
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            letterSpacing = 1.sp
        )
    }
}
@Composable
fun ArtistsTab(viewModel: PlayerViewModel, onNavigateToArtist: (String) -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(uiState.artists) { artist ->
            ArtistRow(artist) { onNavigateToArtist(artist.name) }
        }
    }
}


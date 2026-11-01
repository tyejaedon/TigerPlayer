package com.tigerplayer.ui.coverscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tigerplayer.R
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.ui.theme.aardBlue
import com.tigerplayer.ui.theme.bounceClick
import com.tigerplayer.ui.theme.glassEffect
import com.tigerplayer.utils.ArtistUtils

@Composable
internal fun AlbumPicker(
    albums: List<CoverScreenAlbum>,
    onAlbumSelected: (CoverScreenAlbum) -> Unit
) {
    if (albums.isEmpty()) {
        EmptyLibraryMessage(stringResource(R.string.coverscreen_empty_albums))
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 136.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(albums, key = CoverScreenAlbum::id) { album ->
                CoverScreenAlbumCard(
                    album = album,
                    modifier = Modifier.testTag(CoverScreenTestTags.libraryAlbum(album.id)),
                    onClick = { onAlbumSelected(album) }
                )
            }
        }
    }
}

@Composable
internal fun CoverScreenAlbumCard(
    album: CoverScreenAlbum,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val description = "Album ${album.name}, ${album.trackCount} tracks"
    Column(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .minimumInteractiveComponentSize()
            .bounceClick(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = description
                onClick(label = description) {
                    onClick()
                    true
                }
            },
        horizontalAlignment = Alignment.Start
    ) {
        // Square artwork container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .shadow(
                    elevation = 10.dp,
                    shape = RoundedCornerShape(18.dp),
                    spotColor = MaterialTheme.aardBlue.copy(alpha = 0.25f),
                    ambientColor = Color.Black
                )
                .clip(RoundedCornerShape(18.dp))
                .glassEffect(RoundedCornerShape(18.dp))
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.35f),
                            Color.White.copy(alpha = 0.08f)
                        )
                    ),
                    shape = RoundedCornerShape(18.dp)
                )
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = album.artworkUrl,
                contentDescription = album.name,
                contentScale = ContentScale.Crop,
                fallback = painterResource(R.drawable.ic_tiger_logo),
                error = painterResource(R.drawable.ic_tiger_logo),
                modifier = Modifier.fillMaxSize()
            )

            // Bottom dark gradient overlay for badge readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                            startY = 180f
                        )
                    )
            )

            // Track count pill badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .glassEffect(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.60f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${album.trackCount} CHANTS",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    fontSize = 8.5.sp,
                    letterSpacing = 0.8.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Title
        Text(
            text = album.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Artist ID / name in uppercase
        Text(
            text = album.artistId.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            letterSpacing = 0.5.sp
        )
    }
}

/**
 * Builds unique aggregated cover screen albums from all library tracks, borrowing the aggregation
 * mechanism from [com.tigerplayer.engine.LibraryEngine] and ui/library to ensure exact track counts
 * and preventing duplicate or fragmented album entries across tracks with featured artists.
 */
internal fun buildCoverScreenAlbums(tracks: List<AudioTrack>): List<CoverScreenAlbum> {
    return tracks
        .distinctBy { it.id }
        .filter { it.album.isNotBlank() }
        .groupBy { it.album.trim().lowercase() }
        .map { (key, albumTracks) ->
            val first = albumTracks.first()
            val dominantArtist = albumTracks
                .map { ArtistUtils.getBaseArtist(it.artist).trim() }
                .filter { it.isNotBlank() }
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }?.key
                ?.takeIf { it.isNotBlank() }
                ?: first.artist.trim().ifBlank { "Unknown Artist" }

            CoverScreenAlbum(
                id = "album_${key.hashCode()}",
                artistId = dominantArtist,
                name = first.album.trim(),
                trackCount = albumTracks.size,
                artworkUrl = albumTracks.firstNotNullOfOrNull { it.artworkUri }?.toString()?.takeUnless { it.isBlank() }
            )
        }
        .sortedBy { it.name.lowercase() }
}

/**
 * Filters albums by search query borrowing from [com.tigerplayer.engine.LibraryEngine], matching
 * album name, album artist, or any track title/artist on the album.
 */
internal fun filterCoverScreenAlbums(
    albums: List<CoverScreenAlbum>,
    tracks: List<AudioTrack>,
    query: String
): List<CoverScreenAlbum> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return albums

    return albums.filter { album ->
        album.name.contains(trimmed, ignoreCase = true) ||
            album.artistId.contains(trimmed, ignoreCase = true) ||
            tracks.any { track ->
                track.album.trim().equals(album.name.trim(), ignoreCase = true) &&
                    (track.title.contains(trimmed, ignoreCase = true) ||
                        track.artist.contains(trimmed, ignoreCase = true))
            }
    }
}

/**
 * Retrieves all tracks belonging to an album sorted by disc, track number, and title, borrowing
 * the picking mechanism from [com.tigerplayer.ui.library.AlbumDetailsScreen].
 * Importantly, this never filters out songs by active search queries or featured artists so all
 * songs belonging to the album are reliably presented.
 */
internal fun getCoverScreenAlbumTracks(
    album: CoverScreenAlbum?,
    tracks: List<AudioTrack>
): List<AudioTrack> {
    if (album == null) return emptyList()
    val targetAlbum = album.name.trim()
    return tracks
        .distinctBy { it.id }
        .filter { it.album.trim().equals(targetAlbum, ignoreCase = true) }
        .sortedWith(
            compareBy<AudioTrack> { it.trackNumber.takeIf { t -> t > 0 } ?: Int.MAX_VALUE }
                .thenBy { it.title.lowercase() }
        )
}

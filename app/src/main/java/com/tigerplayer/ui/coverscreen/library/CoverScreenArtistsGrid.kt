package com.tigerplayer.ui.coverscreen

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import com.tigerplayer.ui.coverscreen.core.WetPaintingFadeImage
import com.tigerplayer.ui.coverscreen.core.rememberArtworkAccent
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
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
import com.tigerplayer.data.model.Artist
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.ui.coverscreen.library.formatTrackDuration
import com.tigerplayer.ui.theme.TigerNeonOrange
import com.tigerplayer.ui.theme.TigerTextHigh
import com.tigerplayer.ui.theme.aardBlue
import com.tigerplayer.ui.theme.bounceClick
import com.tigerplayer.ui.theme.glassEffect
import com.tigerplayer.ui.theme.rememberTigerAmbientGradient
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.LocalContext
import com.tigerplayer.data.model.Playlist
import com.tigerplayer.ui.library.SongOptionsSheet
import com.tigerplayer.utils.ArtistUtils

data class CoverScreenAlbum(
    val id: String,
    val artistId: String,
    val name: String,
    val trackCount: Int,
    val artworkUrl: String? = null
)

@Composable
fun CoverScreenArtistsGrid(
    artists: List<Artist>,
    albums: List<CoverScreenAlbum>,
    modifier: Modifier = Modifier,
    tracks: List<AudioTrack> = emptyList(),
    playlists: List<Playlist> = emptyList(),
    onArtistSelected: (Artist) -> Unit = {},
    onAlbumSelected: (CoverScreenAlbum) -> Unit = {},
    onPlayTrack: (AudioTrack) -> Unit = {},
    onPlayNext: (AudioTrack) -> Unit = {},
    onAddToQueue: (AudioTrack) -> Unit = {},
    onAddToPlaylist: (Long, AudioTrack) -> Unit = { _, _ -> },
    onGoToAlbum: (String) -> Unit = {}
) {
    val view = LocalView.current

    if (artists.isEmpty()) {
        val emptyMsg = stringResource(R.string.coverscreen_empty_artists)
        Box(
            modifier = modifier
                .fillMaxSize()
                .testTag(CoverScreenTestTags.ARTISTS_EMPTY_STATE)
                .semantics { contentDescription = emptyMsg },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emptyMsg,
                modifier = Modifier.semantics {
                    contentDescription = emptyMsg
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val tabTitle = stringResource(R.string.coverscreen_tab_artists)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 144.dp),
        modifier = modifier
            .fillMaxSize()
            .testTag(CoverScreenTestTags.ARTISTS_GRID)
            .semantics { contentDescription = tabTitle },
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(artists, key = { it.source.name + ":" + it.id }) { artist ->
            val baseName = remember(artist.name) {
                ArtistUtils.getBaseArtist(artist.name).trim()
            }
            val displayImage = remember(artist, albums, tracks, baseName) {
                artist.imageUrl?.takeIf { it.isNotBlank() }
                    ?: albums.firstOrNull { album ->
                        album.artistId.equals(artist.id, ignoreCase = true) ||
                            album.artistId.equals(artist.name, ignoreCase = true) ||
                            ArtistUtils.getBaseArtist(album.artistId).equals(baseName, ignoreCase = true)
                    }?.artworkUrl
                    ?: tracks.firstOrNull { track ->
                        ArtistUtils.getBaseArtist(track.artist).equals(baseName, ignoreCase = true)
                    }?.artworkUri?.toString()
            }
            val computedAlbumCount = remember(artist, albums, baseName) {
                val matchingCount = albums.count { album ->
                    album.artistId.equals(artist.id, ignoreCase = true) ||
                        album.artistId.equals(artist.name, ignoreCase = true) ||
                        ArtistUtils.getBaseArtist(album.artistId).equals(baseName, ignoreCase = true)
                }
                if (matchingCount > 0) matchingCount else artist.albumCount
            }

            ArtistCard(
                artist = artist,
                displayImage = displayImage,
                albumCount = computedAlbumCount,
                modifier = Modifier.semantics {
                    contentDescription = "${artist.name}, $computedAlbumCount albums"
                },
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onArtistSelected(artist)
                }
            )
        }
    }
}

@Composable
private fun ArtistCard(
    artist: Artist,
    displayImage: String?,
    albumCount: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .minimumInteractiveComponentSize()
            .testTag(CoverScreenTestTags.artist(artist.id))
            .clip(RoundedCornerShape(16.dp))
            .glassEffect(RoundedCornerShape(16.dp))
            .clickable(
                role = Role.Button,
                onClickLabel = artist.name,
                onClick = onClick
            ),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            if (!displayImage.isNullOrBlank()) {
                WetPaintingFadeImage(
                    imageModel = displayImage,
                    contentDescription = "${artist.name} artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.45f to Color.Black.copy(alpha = 0.35f),
                                1.0f to Color.Black.copy(alpha = 0.82f)
                            )
                        )
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(44.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White
                )
                Text(
                    text = "$albumCount albums",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Text(
                    text = artist.source.name.lowercase().replaceFirstChar(Char::uppercase),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.aardBlue,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

@Composable
private fun ArtistThumbnail(name: String, imageUrl: String?) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .border(1.dp, MaterialTheme.aardBlue.copy(alpha = 0.2f), CircleShape)
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl.isNullOrBlank()) {
            Icon(
                imageVector = Icons.Filled.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(44.dp)
            )
        } else {
            WetPaintingFadeImage(
                imageModel = imageUrl,
                contentDescription = "$name artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

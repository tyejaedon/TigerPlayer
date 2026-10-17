package com.tigerplayer.ui.coverscreen

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tigerplayer.R
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tigerplayer.data.model.Artist

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
    onAlbumSelected: (CoverScreenAlbum) -> Unit = {}
) {
    var selectedArtist by remember { mutableStateOf<Artist?>(null) }
    val view = LocalView.current
    val currentArtist = selectedArtist

    if (currentArtist == null) {
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
        } else {
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
                    ArtistCard(
                        artist = artist,
                        modifier = Modifier.semantics {
                            contentDescription = "${artist.name}, ${artist.albumCount} albums"
                        },
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            selectedArtist = artist
                        }
                    )
                }
            }
        }
    } else {
        val artistAlbums = albums.filter { it.artistId == currentArtist.id }
        Column(
            modifier = modifier
                .fillMaxSize()
                .pointerInput(currentArtist.id) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                        onDragEnd = {
                            if (totalDrag > 80.dp.toPx()) selectedArtist = null
                            totalDrag = 0f
                        },
                        onDragCancel = { totalDrag = 0f }
                    )
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val backDesc = stringResource(R.string.coverscreen_tab_artists)
                IconButton(
                    onClick = { selectedArtist = null },
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .minimumInteractiveComponentSize()
                        .semantics {
                            role = Role.Button
                            onClick(label = backDesc) {
                                selectedArtist = null
                                true
                            }
                            contentDescription = backDesc
                        }.testTag(CoverScreenTestTags.ARTIST_BACK)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = backDesc
                    )
                }
                Text(
                    text = currentArtist.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
            }
            if (artistAlbums.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.coverscreen_empty_albums),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 144.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(artistAlbums, key = { it.id }) { album ->
                        AlbumCard(album = album) {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onAlbumSelected(album)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtistCard(
    artist: Artist,
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
            .clickable(
                role = Role.Button,
                onClickLabel = artist.name,
                onClick = onClick
            ),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ArtistThumbnail(artist.name, artist.imageUrl)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = artist.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1
            )
            Text(
                text = "${artist.albumCount} albums",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = artist.source.name.lowercase().replaceFirstChar(Char::uppercase),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun ArtistThumbnail(name: String, imageUrl: String?) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
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
            AsyncImage(
                model = imageUrl,
                contentDescription = "$name artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun AlbumCard(album: CoverScreenAlbum, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .minimumInteractiveComponentSize()
            .testTag(CoverScreenTestTags.album(album.id))
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                role = Role.Button,
                onClickLabel = album.name,
                onClick = onClick
            )
            .semantics {
                contentDescription = "${album.name}, ${album.trackCount} tracks"
            },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            AsyncImage(
                model = album.artworkUrl,
                contentDescription = "${album.name} artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = album.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(
                text = "${album.trackCount} tracks",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

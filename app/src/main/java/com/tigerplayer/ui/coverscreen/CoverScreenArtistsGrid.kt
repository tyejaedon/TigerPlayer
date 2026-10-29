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
import com.tigerplayer.ui.theme.TigerNeonOrange
import com.tigerplayer.ui.theme.TigerTextHigh
import com.tigerplayer.ui.theme.aardBlue
import com.tigerplayer.ui.theme.bounceClick
import com.tigerplayer.ui.theme.glassEffect
import com.tigerplayer.ui.theme.rememberTigerAmbientGradient
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
    onAlbumSelected: (CoverScreenAlbum) -> Unit = {},
    onPlayTrack: (AudioTrack) -> Unit = {}
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
        val baseName = remember(currentArtist.name) {
            ArtistUtils.getBaseArtist(currentArtist.name).trim()
        }
        val artistTracks = remember(tracks, baseName) {
            tracks.filter { track ->
                ArtistUtils.getBaseArtist(track.artist).equals(baseName, ignoreCase = true)
            }
        }
        val artistAlbums = remember(albums, currentArtist.id, baseName) {
            albums.filter { album ->
                album.artistId.equals(currentArtist.id, ignoreCase = true) ||
                    album.artistId.equals(baseName.lowercase(), ignoreCase = true)
            }
        }

        val heroImage = remember(currentArtist.imageUrl, artistAlbums, artistTracks) {
            currentArtist.imageUrl?.takeIf { it.isNotBlank() }
                ?: artistAlbums.firstOrNull()?.artworkUrl
                ?: artistTracks.firstOrNull()?.artworkUri?.toString()
        }

        val ambientBrush = rememberTigerAmbientGradient(
            accent = MaterialTheme.aardBlue,
            baseTopAlpha = 0.16f
        )

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(ambientBrush)
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Top Bar Navigation
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassEffect(RectangleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))
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
                                contentDescription = backDesc,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = currentArtist.name.uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }

                // 2. Hero Image
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .padding(horizontal = 12.dp)
                            .shadow(12.dp, RoundedCornerShape(20.dp), ambientColor = Color.Transparent, spotColor = MaterialTheme.aardBlue.copy(alpha = 0.2f))
                            .clip(RoundedCornerShape(20.dp))
                            .glassEffect(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        AsyncImage(
                            model = heroImage,
                            contentDescription = "Image of ${currentArtist.name}",
                            fallback = painterResource(R.drawable.ic_tiger_logo),
                            error = painterResource(R.drawable.ic_tiger_logo),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0.0f to Color.Transparent,
                                        0.5f to Color.Black.copy(alpha = 0.3f),
                                        1.0f to Color.Black.copy(alpha = 0.88f)
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(14.dp)
                        ) {
                            Text(
                                text = currentArtist.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${artistAlbums.size} ALBUMS • ${if (artistTracks.isNotEmpty()) "${artistTracks.size} TRACKS" else "${currentArtist.albumCount} RELEASES"}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.aardBlue,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                // 3. Vanguard Dossier Card
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .glassEffect(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .border(1.dp, MaterialTheme.aardBlue.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "VANGUARD DOSSIER",
                            style = MaterialTheme.typography.labelMedium,
                            color = TigerTextHigh,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CoverArtistMetadataBadge(
                                text = "${artistAlbums.size} ALBUMS",
                                textColor = MaterialTheme.aardBlue,
                                isHighlight = true
                            )
                            if (artistTracks.isNotEmpty()) {
                                CoverArtistMetadataBadge(
                                    text = "${artistTracks.size} TRACKS",
                                    textColor = TigerNeonOrange,
                                    isHighlight = false
                                )
                            }
                        }
                        if (currentArtist.genres.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                currentArtist.genres.take(3).forEach { genre ->
                                    CoverGenrePill(name = genre, accentColor = MaterialTheme.aardBlue)
                                }
                            }
                        }
                    }
                }

                // 4. Discography Section
                item {
                    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                        Text(
                            text = "DISCOGRAPHY",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (artistAlbums.isEmpty()) {
                            Text(
                                text = stringResource(R.string.coverscreen_empty_albums),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(artistAlbums, key = { it.id }) { album ->
                                    CoverArtistAlbumCard(
                                        album = album,
                                        onClick = {
                                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                            onAlbumSelected(album)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // 5. All Manifestations / Tracks Section
                if (artistTracks.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "ALL MANIFESTATIONS",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    items(artistTracks, key = { it.id }) { track ->
                        CoverArtistTrackRow(
                            track = track,
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                onPlayTrack(track)
                            }
                        )
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
            .glassEffect(RoundedCornerShape(16.dp))
            .clickable(
                role = Role.Button,
                onClickLabel = artist.name,
                onClick = onClick
            ),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
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
                fontWeight = FontWeight.Bold,
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
                color = MaterialTheme.aardBlue,
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
private fun CoverArtistAlbumCard(
    album: CoverScreenAlbum,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(128.dp)
            .bounceClick(onClick = onClick)
            .testTag(CoverScreenTestTags.album(album.id))
    ) {
        Box(
            modifier = Modifier
                .size(128.dp)
                .shadow(8.dp, RoundedCornerShape(14.dp), spotColor = MaterialTheme.aardBlue.copy(0.2f))
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(14.dp)
                )
        ) {
            AsyncImage(
                model = album.artworkUrl,
                contentDescription = "Cover for ${album.name}",
                contentScale = ContentScale.Crop,
                fallback = painterResource(R.drawable.ic_tiger_logo),
                error = painterResource(R.drawable.ic_tiger_logo),
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${album.trackCount}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    fontSize = 10.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = album.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "RECORDED",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.aardBlue,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun CoverArtistTrackRow(
    track: AudioTrack,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .glassEffect(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = track.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                fallback = painterResource(R.drawable.ic_tiger_logo),
                error = painterResource(R.drawable.ic_tiger_logo),
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.album} • ${formatTrackDuration(track.durationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play ${track.title}",
                tint = MaterialTheme.aardBlue,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun CoverArtistMetadataBadge(
    text: String,
    textColor: Color,
    isHighlight: Boolean
) {
    Surface(
        color = if (isHighlight) textColor.copy(alpha = 0.18f) else Color.Transparent,
        shape = CircleShape,
        border = BorderStroke(1.dp, if (isHighlight) textColor else textColor.copy(alpha = 0.35f))
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
private fun CoverGenrePill(name: String, accentColor: Color) {
    Surface(
        shape = CircleShape,
        color = accentColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f))
    ) {
        Text(
            text = name.uppercase(),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            letterSpacing = 0.8.sp
        )
    }
}

package com.tigerplayer.ui.coverscreen

import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tigerplayer.R
import com.tigerplayer.data.model.Artist
import com.tigerplayer.data.model.ArtistSource
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.model.Playlist
import com.tigerplayer.ui.coverscreen.core.WetPaintingFadeImage
import com.tigerplayer.ui.coverscreen.library.formatTrackDuration
import com.tigerplayer.ui.library.SongOptionsSheet
import com.tigerplayer.ui.theme.TigerNeonOrange
import com.tigerplayer.ui.theme.TigerPlayerTheme
import com.tigerplayer.ui.theme.TigerTextHigh
import com.tigerplayer.ui.theme.aardBlue
import com.tigerplayer.ui.theme.glassEffect
import com.tigerplayer.ui.theme.rememberTigerAmbientGradient
import com.tigerplayer.utils.ArtistUtils

import androidx.compose.material.icons.rounded.Shuffle

@Composable
internal fun CoverScreenArtistView(
    artist: Artist,
    albums: List<CoverScreenAlbum>,
    tracks: List<AudioTrack>,
    modifier: Modifier = Modifier,
    playlists: List<Playlist> = emptyList(),
    onBack: () -> Unit = {},
    onAlbumSelected: (CoverScreenAlbum) -> Unit = {},
    onPlayTrack: (AudioTrack) -> Unit = {},
    onPlayNext: (AudioTrack) -> Unit = {},
    onAddToQueue: (AudioTrack) -> Unit = {},
    onAddToPlaylist: (Long, AudioTrack) -> Unit = { _, _ -> },
    onGoToAlbum: (String) -> Unit = {},
    onPlayAll: (List<AudioTrack>) -> Unit = {},
    onShufflePlay: (List<AudioTrack>) -> Unit = {}
) {
    var optionsTrack by remember { mutableStateOf<AudioTrack?>(null) }
    val view = LocalView.current
    val context = LocalContext.current

    val baseName = remember(artist.name) {
        ArtistUtils.getBaseArtist(artist.name).trim()
    }
    val artistTracks = remember(tracks, baseName) {
        tracks.filter { track ->
            ArtistUtils.getBaseArtist(track.artist).equals(baseName, ignoreCase = true)
        }
    }
    val artistAlbums = remember(albums, artist.id, baseName, tracks) {
        albums.filter { album ->
            album.artistId.equals(artist.id, ignoreCase = true) ||
                album.artistId.equals(baseName, ignoreCase = true) ||
                ArtistUtils.getBaseArtist(album.artistId).equals(baseName, ignoreCase = true) ||
                tracks.any { track ->
                    track.album.trim().equals(album.name.trim(), ignoreCase = true) &&
                        ArtistUtils.getBaseArtist(track.artist).equals(baseName, ignoreCase = true)
                }
        }
    }

    val heroImage = remember(artist.imageUrl, artistAlbums, artistTracks) {
        artist.imageUrl?.takeIf { it.isNotBlank() }
            ?: artistAlbums.firstOrNull()?.artworkUrl
            ?: artistTracks.firstOrNull()?.artworkUri?.toString()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(artist.id) {
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                    onDragEnd = {
                        if (totalDrag > 80.dp.toPx()) onBack()
                        totalDrag = 0f
                    },
                    onDragCancel = { totalDrag = 0f }
                )
            }
    ) {
        WetPaintingFadeImage(
            imageModel = heroImage,
            contentDescription = "Image of ${artist.name}",
            fadeBottomPercent = 0.12f,
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

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                CoverScreenArtistHero(
                    artist = artist,
                    albumCount = artistAlbums.size,
                    trackCount = artistTracks.size,
                    artistTracks = artistTracks,
                    onPlayAll = onPlayAll,
                    onShufflePlay = onShufflePlay
                )
            }

            item {
                CoverScreenArtistDiscography(
                    albums = artistAlbums,
                    onAlbumSelected = { album ->
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onAlbumSelected(album)
                    }
                )
            }

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
                        },
                        onLongClick = {
                            optionsTrack = track
                        }
                    )
                }
            }
        }
    }

    optionsTrack?.let { selectedTrack ->
        SongOptionsSheet(
            track = selectedTrack,
            playlists = playlists,
            onDismiss = { optionsTrack = null },
            onPlayNext = { onPlayNext(selectedTrack) },
            onAddToQueue = { onAddToQueue(selectedTrack) },
            onAddToPlaylist = { playlistId -> onAddToPlaylist(playlistId, selectedTrack) },
            onGoToAlbum = { albumName -> onGoToAlbum(albumName) },
            onQueueAdded = {
                Toast.makeText(
                    context,
                    "${it.title} added to queue",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onPlaylistAdded = { song, playlist ->
                Toast.makeText(
                    context,
                    "${song.title} added to ${playlist.name}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }
}

@Composable
private fun CoverScreenArtistHero(
    artist: Artist,
    albumCount: Int,
    trackCount: Int,
    artistTracks: List<AudioTrack> = emptyList(),
    modifier: Modifier = Modifier,
    onPlayAll: (List<AudioTrack>) -> Unit = {},
    onShufflePlay: (List<AudioTrack>) -> Unit = {}
) {
    val heroShape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .shadow(
                elevation = 8.dp,
                shape = heroShape,
                ambientColor = Color.Transparent,
                spotColor = MaterialTheme.aardBlue.copy(alpha = 0.25f)
            )
            .clip(heroShape)
            .glassEffect(heroShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .border(
                BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                heroShape
            )
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = artist.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "$albumCount ALBUMS • ${if (trackCount > 0) "$trackCount TRACKS" else "${artist.albumCount} RELEASES"}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.aardBlue,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center
        )
        if (artistTracks.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = { onPlayAll(artistTracks) },
                    shape = CircleShape,
                    color = MaterialTheme.aardBlue,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .semantics {
                            role = Role.Button
                            onClick(label = "Play all tracks by ${artist.name}") {
                                onPlayAll(artistTracks)
                                true
                            }
                        }
                        .testTag(CoverScreenTestTags.PLAY_ALL)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "PLAY ALL",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = Color.Black
                        )
                    }
                }

                Surface(
                    onClick = { onShufflePlay(artistTracks) },
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .semantics {
                            role = Role.Button
                            onClick(label = "Shuffle play tracks by ${artist.name}") {
                                onShufflePlay(artistTracks)
                                true
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "SHUFFLE",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
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
                    text = "$albumCount ALBUMS",
                    textColor = MaterialTheme.aardBlue,
                    isHighlight = true
                )
                if (trackCount > 0) {
                    CoverArtistMetadataBadge(
                        text = "$trackCount TRACKS",
                        textColor = TigerNeonOrange,
                        isHighlight = false
                    )
                }
            }
            if (artist.genres.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    artist.genres.take(3).forEach { genre ->
                        CoverGenrePill(name = genre, accentColor = MaterialTheme.aardBlue)
                    }
                }
            }
            if (!artist.bio.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "BIOGRAPHY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.aardBlue,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = artist.bio,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun CoverScreenArtistDiscography(
    albums: List<CoverScreenAlbum>,
    onAlbumSelected: (CoverScreenAlbum) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        Text(
            text = "DISCOGRAPHY",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (albums.isEmpty()) {
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
                items(albums, key = { it.id }) { album ->
                    CoverArtistAlbumCard(
                        album = album,
                        onClick = {
                            onAlbumSelected(album)
                        }
                    )
                }
            }
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
            .clickable { onClick() }
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
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .glassEffect(RoundedCornerShape(12.dp))
            .testTag(CoverScreenTestTags.track(track.id))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
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

@Composable
fun CoverScreenArtistDetail(
    artist: Artist,
    albums: List<CoverScreenAlbum>,
    tracks: List<AudioTrack>,
    modifier: Modifier = Modifier,
    playlists: List<Playlist> = emptyList(),
    onBack: () -> Unit = {},
    onAlbumSelected: (CoverScreenAlbum) -> Unit = {},
    onPlayTrack: (AudioTrack) -> Unit = {},
    onPlayNext: (AudioTrack) -> Unit = {},
    onAddToQueue: (AudioTrack) -> Unit = {},
    onAddToPlaylist: (Long, AudioTrack) -> Unit = { _, _ -> },
    onGoToAlbum: (String) -> Unit = {},
    onPlayAll: (List<AudioTrack>) -> Unit = {},
    onShufflePlay: (List<AudioTrack>) -> Unit = {}
) {
    CoverScreenArtistView(
        artist = artist,
        albums = albums,
        tracks = tracks,
        modifier = modifier,
        playlists = playlists,
        onBack = onBack,
        onAlbumSelected = onAlbumSelected,
        onPlayTrack = onPlayTrack,
        onPlayNext = onPlayNext,
        onAddToQueue = onAddToQueue,
        onAddToPlaylist = onAddToPlaylist,
        onGoToAlbum = onGoToAlbum,
        onPlayAll = onPlayAll,
        onShufflePlay = onShufflePlay
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun CoverScreenArtistDetailPreview() {
    TigerPlayerTheme {
        CoverScreenArtistDetail(
            artist = Artist(
                id = "artist-preview",
                name = "Tiger Artist",
                source = ArtistSource.LOCAL,
                albumCount = 3
            ),
            albums = emptyList(),
            tracks = emptyList()
        )
    }
}

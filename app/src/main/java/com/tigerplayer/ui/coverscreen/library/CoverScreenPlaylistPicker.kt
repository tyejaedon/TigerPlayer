package com.tigerplayer.ui.coverscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tigerplayer.R
import com.tigerplayer.data.local.entity.PlaylistEntity
import com.tigerplayer.data.model.Playlist
import com.tigerplayer.data.remote.model.SpotifyPlaylist
import com.tigerplayer.ui.theme.aardBlue
import com.tigerplayer.ui.theme.bounceClick
import com.tigerplayer.ui.theme.glassEffect
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

@Composable
internal fun LocalPlaylistPicker(
    playlists: List<Playlist>,
    onPlaylistSelected: (Playlist) -> Unit,
    onEditPhoto: (Playlist) -> Unit,
    onImportPlaylist: () -> Unit = {}
) {
    if (playlists.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.coverscreen_empty_playlists),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, MaterialTheme.aardBlue.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .clickable(role = Role.Button, onClick = onImportPlaylist)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag(CoverScreenTestTags.IMPORT_PLAYLIST),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FileDownload,
                        contentDescription = stringResource(R.string.coverscreen_action_import_playlist),
                        tint = MaterialTheme.aardBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.coverscreen_action_import_playlist),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
        return
    }

    // Sort alphabetically, with Liked Songs ALWAYS first
    val sortedPlaylists = remember(playlists) {
        sortCoverScreenPlaylists(playlists)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .testTag(CoverScreenTestTags.PLAYLIST_CAROUSEL)
    ) {
        val totalWidth = maxWidth
        val cardWidth = 154.dp
        val horizontalPadding = ((totalWidth - cardWidth) / 2).coerceAtLeast(20.dp)

        val pagerState = rememberPagerState(
            initialPage = 0,
            pageCount = { sortedPlaylists.size }
        )
        val coroutineScope = rememberCoroutineScope()

        // 3D Horizontal Wheel Carousel
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val playlist = sortedPlaylists[page]
            val isLiked = playlist.id == PlaylistEntity.LIKED_SONGS_PLAYLIST_ID ||
                    playlist.name.equals(PlaylistEntity.LIKED_SONGS_PLAYLIST_NAME, ignoreCase = true)

            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val absOffset = pageOffset.absoluteValue.coerceIn(0f, 2f)

            Box(
                modifier = Modifier
                    .width(cardWidth)
                    .graphicsLayer {
                        cameraDistance = 12f * density
                        // Cylindrical wheel rotation around Y axis
                        rotationY = (pageOffset * -36f).coerceIn(-65f, 65f)
                        // Scale contraction along cylindrical wheel
                        val scale = (1f - (absOffset * 0.16f)).coerceIn(0.68f, 1f)
                        scaleX = scale
                        scaleY = scale
                        // Depth alpha fade
                        alpha = (1f - (absOffset * 0.40f)).coerceIn(0.25f, 1f)
                        // Perspective translation
                        translationX = pageOffset * 6f
                    },
                contentAlignment = Alignment.Center
            ) {
                CoverScreenPlaylistCard(
                    playlist = playlist,
                    isLiked = isLiked,
                    onSelect = { onPlaylistSelected(playlist) },
                    onEditPhoto = { onEditPhoto(playlist) },
                    modifier = Modifier.testTag(CoverScreenTestTags.localPlaylist(playlist.id))
                )
            }
        }

        // Fast-scroll Slider on the Side (Right Edge)
        if (sortedPlaylists.size > 1) {
            PlaylistSideScrubber(
                totalCount = sortedPlaylists.size,
                currentPage = pagerState.currentPage,
                pageOffsetFraction = pagerState.currentPageOffsetFraction,
                onScrubTo = { targetIndex ->
                    coroutineScope.launch {
                        pagerState.scrollToPage(targetIndex)
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp)
            )
        }

        // Import M3U/M3U8 button positioned at bottom center
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1B1F2A).copy(alpha = 0.90f))
                    .border(1.dp, MaterialTheme.aardBlue.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button, onClick = onImportPlaylist)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .testTag(CoverScreenTestTags.IMPORT_PLAYLIST),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.FileDownload,
                    contentDescription = stringResource(R.string.coverscreen_action_import_playlist),
                    tint = MaterialTheme.aardBlue,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = stringResource(R.string.coverscreen_action_import_playlist),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
internal fun CoverScreenPlaylistCard(
    playlist: Playlist,
    isLiked: Boolean,
    onSelect: () -> Unit,
    onEditPhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    val editPhotoLabel = stringResource(R.string.coverscreen_action_edit_playlist_photo)
    val likedHeartColor = Color(0xFFE91E63)

    Column(
        modifier = modifier
            .width(154.dp)
            .bounceClick { onSelect() }
    ) {
        Box(
            modifier = Modifier
                .size(154.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(22.dp),
                    spotColor = if (isLiked) likedHeartColor.copy(alpha = 0.35f) else MaterialTheme.aardBlue.copy(alpha = 0.25f),
                    ambientColor = Color.Black
                )
                .clip(RoundedCornerShape(22.dp))
                .glassEffect(RoundedCornerShape(22.dp))
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.30f),
                            Color.White.copy(alpha = 0.08f)
                        )
                    ),
                    shape = RoundedCornerShape(22.dp)
                )
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // Artwork or stylized fallback
            if (!playlist.artworkUri.isNullOrBlank()) {
                AsyncImage(
                    model = playlist.artworkUri,
                    contentDescription = playlist.name,
                    contentScale = ContentScale.Crop,
                    fallback = painterResource(R.drawable.ic_tiger_logo),
                    error = painterResource(R.drawable.ic_tiger_logo),
                    modifier = Modifier.fillMaxSize()
                )
            } else if (isLiked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    likedHeartColor.copy(alpha = 0.45f),
                                    Color.Black.copy(alpha = 0.85f)
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Favorite,
                        contentDescription = null,
                        tint = likedHeartColor,
                        modifier = Modifier.size(54.dp)
                    )
                }
            } else {
                AsyncImage(
                    model = null,
                    contentDescription = playlist.name,
                    contentScale = ContentScale.Crop,
                    fallback = painterResource(R.drawable.ic_tiger_logo),
                    error = painterResource(R.drawable.ic_tiger_logo),
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Bottom gradient overlay for badge readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)),
                            startY = 140f
                        )
                    )
            )

            // Track count pill badge (bottom start)
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
                    text = "${playlist.trackCount} CHANTS",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    fontSize = 8.5.sp,
                    letterSpacing = 0.8.sp
                )
            }

            // Liked badge (bottom end) if liked
            if (isLiked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(likedHeartColor.copy(alpha = 0.85f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(9.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = "LIKED",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            fontSize = 8.sp,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
            }

            // Edit photo action button (top end)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .glassEffect(CircleShape)
                    .background(Color.Black.copy(alpha = 0.60f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = editPhotoLabel
                    ) {
                        onEditPhoto()
                    }
                    .testTag(CoverScreenTestTags.editPlaylistPhoto(playlist.id)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = editPhotoLabel,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Title
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Subtitle
        Text(
            text = if (isLiked) "FAVORITES" else "${playlist.trackCount} TRACKS",
            style = MaterialTheme.typography.labelSmall,
            color = if (isLiked) likedHeartColor else MaterialTheme.aardBlue,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
internal fun PlaylistSideScrubber(
    totalCount: Int,
    currentPage: Int,
    pageOffsetFraction: Float,
    onScrubTo: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var scrubberHeightPx by remember { mutableFloatStateOf(1f) }

    Box(
        modifier = modifier
            .height(130.dp)
            .width(28.dp)
            .onSizeChanged { scrubberHeightPx = it.height.toFloat() }
            .pointerInput(totalCount) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        val fraction = (offset.y / scrubberHeightPx).coerceIn(0f, 1f)
                        val targetIndex = (fraction * (totalCount - 1)).roundToInt()
                        onScrubTo(targetIndex)
                    },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onDrag = { change, _ ->
                        change.consume()
                        val fraction = (change.position.y / scrubberHeightPx).coerceIn(0f, 1f)
                        val targetIndex = (fraction * (totalCount - 1)).roundToInt()
                        onScrubTo(targetIndex)
                    }
                )
            }
            .pointerInput(totalCount) {
                detectTapGestures { offset ->
                    val fraction = (offset.y / scrubberHeightPx).coerceIn(0f, 1f)
                    val targetIndex = (fraction * (totalCount - 1)).roundToInt()
                    onScrubTo(targetIndex)
                }
            }
            .testTag(CoverScreenTestTags.PLAYLIST_SLIDER),
        contentAlignment = Alignment.Center
    ) {
        // Track
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.16f))
        )

        // Thumb indicator
        val progressFraction = ((currentPage + pageOffsetFraction) / (totalCount - 1).coerceAtLeast(1))
            .coerceIn(0f, 1f)

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(28.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        val thumbHeightPx = 22.dp.toPx()
                        val travelRangePx = (scrubberHeightPx - thumbHeightPx).coerceAtLeast(0f)
                        translationY = progressFraction * travelRangePx
                        if (isDragging) {
                            scaleX = 1.25f
                            scaleY = 1.15f
                        }
                    }
                    .width(if (isDragging) 9.dp else 6.dp)
                    .height(22.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.aardBlue)
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.5f),
                        RoundedCornerShape(3.dp)
                    )
            )
        }
    }
}

@Composable
internal fun SpotifyPlaylistPicker(
    authenticated: Boolean,
    playlists: List<SpotifyPlaylist>,
    onPlaylistSelected: (SpotifyPlaylist) -> Unit
) {
    if (!authenticated) {
        EmptyLibraryMessage(
            message = stringResource(R.string.coverscreen_auth_required),
            testTag = CoverScreenTestTags.LIBRARY_SPOTIFY_AUTH_REQUIRED
        )
    } else if (playlists.isEmpty()) {
        EmptyLibraryMessage(stringResource(R.string.coverscreen_empty_spotify_playlists))
    } else {
        LazyColumn(contentPadding = PaddingValues(12.dp)) {
            items(playlists.size, key = { playlists[it].id }) { index ->
                val playlist = playlists[index]
                LibraryTile(
                    title = playlist.name,
                    subtitle = playlist.owner.displayName,
                    description = "Spotify playlist ${playlist.name}",
                    imageUrl = playlist.images?.firstOrNull()?.url,
                    icon = Icons.AutoMirrored.Filled.PlaylistPlay,
                    modifier = Modifier.testTag(CoverScreenTestTags.spotifyPlaylist(playlist.id)),
                    onClick = { onPlaylistSelected(playlist) }
                )
            }
        }
    }
}

/**
 * Sorts playlists alphabetically by name (case-insensitive), ensuring the
 * singleton Liked Songs playlist is always placed first.
 */
internal fun sortCoverScreenPlaylists(playlists: List<Playlist>): List<Playlist> {
    return playlists.sortedWith { a, b ->
        val aIsLiked = a.id == PlaylistEntity.LIKED_SONGS_PLAYLIST_ID ||
                a.name.equals(PlaylistEntity.LIKED_SONGS_PLAYLIST_NAME, ignoreCase = true)
        val bIsLiked = b.id == PlaylistEntity.LIKED_SONGS_PLAYLIST_ID ||
                b.name.equals(PlaylistEntity.LIKED_SONGS_PLAYLIST_NAME, ignoreCase = true)
        when {
            aIsLiked && bIsLiked -> 0
            aIsLiked -> -1
            bIsLiked -> 1
            else -> a.name.compareTo(b.name, ignoreCase = true)
        }
    }
}

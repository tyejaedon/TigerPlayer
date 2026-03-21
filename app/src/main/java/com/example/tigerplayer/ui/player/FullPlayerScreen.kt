package com.example.tigerplayer.ui.player

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Subject
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.ui.library.SongOptionsSheet
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.glassEffect
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.media3.common.Player
import coil.size.Precision
import coil.size.Size
import kotlin.math.sin

// --- Thematic Witcher Colors ---
// --- Thematic Witcher Colors ---
private val IgniRed = Color(0xFFF11F1A)
private val AardBlue = Color(0xFF4FC3F7)

@Composable
fun FullPlayerScreen(
    viewModel: PlayerViewModel,
    onCollapse: () -> Unit,
    onNavigateToAlbum: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val track = uiState.currentTrack ?: return
    val context = LocalContext.current

    var showOptions by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }

    // Gesture State
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    // Dynamic Color States
    val onBackground = MaterialTheme.colorScheme.onBackground
    var dynamicTextColor by remember(onBackground) { mutableStateOf(onBackground) }
    var dynamicSecondaryTextColor by remember(onBackground) { mutableStateOf(onBackground.copy(alpha = 0.7f)) }

    val infiniteTransition = rememberInfiniteTransition(label = "ArtBreathing")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ScaleAnimation"
    )

    val imageUrl = if (showLyrics) {
        uiState.artistImageUrl ?: track.artworkUri
    } else {
        track.artworkUri ?: uiState.artistImageUrl
    }

    val imageRequest = remember(imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(true)
            .size(Size.ORIGINAL)
            .precision(Precision.EXACT)
            .allowHardware(false)
            .listener(
                onSuccess = { _, result ->
                    val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                    bitmap?.let { b ->
                        Palette.from(b).generate { palette ->
                            // Prioritize vibrant colors to avoid muddy grays
                            val dominantColorInt = palette?.vibrantSwatch?.rgb
                                ?: palette?.dominantSwatch?.rgb
                                ?: palette?.mutedSwatch?.rgb
                                ?: android.graphics.Color.DKGRAY

                            val luminance = ColorUtils.calculateLuminance(dominantColorInt)

                            if (luminance > 0.6) {
                                dynamicTextColor = Color.Black
                                dynamicSecondaryTextColor = Color.Black.copy(alpha = 0.7f)
                            } else if (luminance < 0.1) {
                                dynamicTextColor = Color.White
                                dynamicSecondaryTextColor = Color.White.copy(alpha = 0.7f)
                            } else {
                                dynamicTextColor = Color.White
                                dynamicSecondaryTextColor = Color.White.copy(alpha = 0.7f)
                            }
                        }
                    }
                }
            )
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    },
                    onDragEnd = {
                        if (abs(offsetX) > abs(offsetY)) {
                            if (offsetX > 150) viewModel.skipToPrevious()
                            else if (offsetX < -150) viewModel.skipToNext()
                        } else {
                            if (offsetY > 150) onCollapse()
                        }
                        offsetX = 0f
                        offsetY = 0f
                    },
                    onDragCancel = {
                        offsetX = 0f
                        offsetY = 0f
                    }
                )
            }
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        )

        // Adaptive Scrim: Lightens or darkens based on the extracted text color
        val scrimColor = if (dynamicTextColor == Color.Black) Color.White else Color.Black
        val scrimAlphaCenter = if (showLyrics) 0.7f else 0.4f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            scrimColor.copy(alpha = 0.2f),
                            scrimColor.copy(alpha = scrimAlphaCenter),
                            scrimColor.copy(alpha = 0.85f)
                        )
                    )
                )
        )
        var isLikedLocally by remember(track.id) { mutableStateOf(false) }

        val igniTransition = rememberInfiniteTransition(label = "IgniFlicker")
        val igniAlpha by igniTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(250, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "FlickerAnimation"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(WitcherIcons.Collapse, contentDescription = "Collapse", tint = dynamicTextColor)
                }

                Text(
                    text = if (showLyrics) "LYRICS" else "NOW PLAYING",
                    style = MaterialTheme.typography.labelLarge,
                    color = dynamicTextColor.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )

                Row {
                    IconButton(onClick = { showLyrics = !showLyrics }) {
                        Icon(
                            imageVector = Icons.Rounded.Subject,
                            contentDescription = "Lyrics",
                            tint = if (showLyrics) MaterialTheme.colorScheme.primary else dynamicTextColor
                        )
                    }
                    IconButton(onClick = { showOptions = true }) {
                        Icon(WitcherIcons.Options, contentDescription = "Options", tint = dynamicTextColor)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.BottomStart
            ) {
                AnimatedContent(
                    targetState = showLyrics,
                    transitionSpec = {
                        (scaleIn(initialScale = 0.8f, animationSpec = tween(400, easing = EaseOutBack)) + fadeIn(tween(400))) togetherWith
                                (scaleOut(targetScale = 0.8f, animationSpec = tween(400)) + fadeOut(tween(400)))
                    },
                    label = "LyricsBubbleAnimation"
                ) { isLyricsShowing ->
                    if (isLyricsShowing) {
                        LyricsDisplay(
                            lyrics = uiState.currentLyrics,
                            currentPositionMs = uiState.currentPosition,
                            textColor = dynamicTextColor
                        )
                    } else {
                        TrackInfoCard(
                            track = track,
                            textColor = dynamicTextColor,
                            secondaryTextColor = dynamicSecondaryTextColor,
                            isLiked = isLikedLocally,
                            onLikeClick = {
                                isLikedLocally = !isLikedLocally
                                if (isLikedLocally) {
                                    // Summon the ViewModel to permanently save it
                                    viewModel.addTrackToLikedSongs(track)
                                } else {
                                    // Optional: viewModel.removeTrackFromLikedSongs(track)
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            FieryWavySeeker(
                uiState = uiState,
                viewModel = viewModel,
                textColor = dynamicTextColor
            )

            Spacer(modifier = Modifier.height(40.dp))

            PlaybackControls(
                uiState = uiState,
                viewModel = viewModel,
                textColor = dynamicTextColor,
                igniFlickerAlpha = igniAlpha
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showOptions) {
        val playlists by viewModel.customPlaylists.collectAsState()

        SongOptionsSheet(
            track = track,
            playlists = playlists,
            onDismiss = { showOptions = false },
            onPlayNext = { viewModel.addToQueue(track) },
            onAddToPlaylist = { playlistId ->
                viewModel.addTrackToPlaylist(playlistId, track)
            },
            onGoToAlbum = { albumName ->
                onNavigateToAlbum(albumName)
                showOptions = false
            }
        )
    }
}

// --- NEW: Synced Lyrics Display Engine ---

data class LyricLine(val timeMs: Long, val text: String)

private fun parseSyncedLyrics(raw: String?): List<LyricLine> {
    if (raw.isNullOrBlank()) return emptyList()
    val regex = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)""")

    return raw.lines().mapNotNull { line ->
        val match = regex.find(line)
        if (match != null) {
            val (min, sec, msStr, text) = match.destructured
            val ms = if (msStr.length == 2) msStr.toLong() * 10 else msStr.toLong()
            val timeMs = min.toLong() * 60000 + sec.toLong() * 1000 + ms
            LyricLine(timeMs, text.trim())
        } else null
    }
}

@Composable
private fun LyricsDisplay(lyrics: String?, currentPositionMs: Long, textColor: Color) {
    if (lyrics.isNullOrBlank()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Instrumental\n(Or no lyrics found)",
                color = textColor.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineSmall
            )
        }
        return
    }

    val parsedLyrics = remember(lyrics) { parseSyncedLyrics(lyrics) }

    if (parsedLyrics.isEmpty()) {
        val scrollState = rememberScrollState()
        Text(
            text = lyrics,
            color = textColor,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 32.dp),
            style = MaterialTheme.typography.titleLarge.copy(lineHeight = 36.sp),
            textAlign = TextAlign.Center
        )
    } else {
        val listState = rememberLazyListState()
        val activeIndex = parsedLyrics.indexOfLast { it.timeMs <= currentPositionMs }.coerceAtLeast(0)

        val configuration = LocalConfiguration.current
        val screenHeight = configuration.screenHeightDp.dp
        val centerOffset = with(LocalDensity.current) { (screenHeight / 3).toPx().toInt() }

        LaunchedEffect(activeIndex) {
            if (activeIndex > 0) {
                listState.animateScrollToItem(activeIndex, scrollOffset = -centerOffset)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .glassEffect(MaterialTheme.shapes.large)
                .padding(24.dp),
            contentPadding = PaddingValues(vertical = 100.dp)
        ) {
            itemsIndexed(parsedLyrics) { index, line ->
                val isActive = index == activeIndex
                val isPassed = index < activeIndex

                Text(
                    text = if (line.text.isBlank()) "🎵" else line.text,
                    color = if (isActive) MaterialTheme.colorScheme.primary else if (isPassed) textColor.copy(alpha = 0.7f) else textColor.copy(alpha = 0.3f),
                    style = if (isActive) MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black) else MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        }
    }
}

// --- THE FIERY WAVY SEEKER ---
@Composable
private fun FieryWavySeeker(
    uiState: PlayerUiState,
    viewModel: PlayerViewModel,
    textColor: Color
) {
    val track = uiState.currentTrack ?: return
    val accentColor = if (uiState.isPlaying) IgniRed else AardBlue


    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableStateOf(0f) }
// THE FIX: Ensure duration is at least 1ms and handle NaN explicitly
    val duration = track.durationMs.coerceAtLeast(1L)
    val progressValue = (uiState.currentPosition.toFloat() / duration)
    val actualProgress = if (progressValue.isNaN()) 0f else progressValue.coerceIn(0f, 1f)

    val targetProgress = if (isDragging) dragProgress else actualProgress

// Also, ensure the animation doesn't try to interpolate a NaN value
    val animatedProgress by animateFloatAsState(
        targetValue = if (targetProgress.isNaN()) 0f else targetProgress,
        animationSpec = if (uiState.isPlaying && !isDragging)
            tween(1000, easing = LinearEasing)
        else spring(),
        label = "SmoothProgress"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "WaveMotion")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "Phase"
    )

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = {
                            isDragging = false
                            viewModel.seekTo((dragProgress * track.durationMs).toLong())
                        },
                        onDragCancel = { isDragging = false }
                    ) { change, _ ->
                        change.consume()
                        dragProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val centerY = height / 2

                val progressX = width * animatedProgress

                drawLine(
                    color = textColor.copy(alpha = 0.1f),
                    start = Offset(0f, centerY),
                    end = androidx.compose.ui.geometry.Offset(width, centerY),
                    strokeWidth = 4.dp.toPx()
                )

                val path = Path()
                val waveAmplitude = if (uiState.isPlaying) 15f else 0f
                val waveFrequency = 0.05f

                path.moveTo(0f, centerY)
                for (x in 0..progressX.toInt() step 5) {
                    val y = centerY + (waveAmplitude * sin(x * waveFrequency + phase))
                    path.lineTo(x.toFloat(), y)
                }

                drawPath(
                    path = path,
                    color = accentColor,
                    style = Stroke(
                        width = 6.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )

                drawCircle(
                    color = accentColor,
                    radius = 8.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(
                        progressX,
                        centerY + (waveAmplitude * sin(progressX * waveFrequency + phase))
                    )
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val displayPosition = if (isDragging) (dragProgress * track.durationMs).toLong() else uiState.currentPosition
            Text(formatDuration(displayPosition), color = textColor.copy(alpha = 0.6f), fontSize = 12.sp)
            Text(formatDuration(track.durationMs), color = textColor.copy(alpha = 0.6f), fontSize = 12.sp)
        }
    }
}

// --- AUTO-SCALING TRACK INFO CARD ---
@Composable
private fun TrackInfoCard(
    track: AudioTrack,
    textColor: Color,
    secondaryTextColor: Color,
    isLiked: Boolean,
    onLikeClick: () -> Unit
) {
    var titleFontSize by remember { mutableStateOf(44.sp) }

    // Heart Animation Mechanics
    val heartScale by animateFloatAsState(
        targetValue = if (isLiked) 1.2f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessLow),
        label = "HeartScale"
    )
    val heartColor by animateColorAsState(
        targetValue = if (isLiked) IgniRed else textColor.copy(alpha = 0.6f),
        tween(300),
        label = "HeartColor"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassEffect(MaterialTheme.shapes.large)
            .padding(24.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Track & Artist Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontSize = titleFontSize,
                        lineHeight = titleFontSize * 1.1f
                    ),
                    color = textColor,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { textLayoutResult ->
                        if (textLayoutResult.hasVisualOverflow && titleFontSize > 24.sp) {
                            titleFontSize *= 0.9f
                        }
                    }
                )

                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.headlineMedium,
                    color = secondaryTextColor,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // --- THE LIKED SONGS BUTTON ---
            IconButton(
                onClick = onLikeClick,
                modifier = Modifier
                    .padding(start = 16.dp)
                    .graphicsLayer {
                        scaleX = heartScale
                        scaleY = heartScale
                    }
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "Like Song",
                    tint = heartColor,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetadataBadge(text = track.mimeType.split("/").last().uppercase(), textColor = textColor)
            MetadataBadge(text = "LOSSLESS", isHighlight = true, textColor = textColor)
        }
    }
}
@Composable
private fun MetadataBadge(text: String, isHighlight: Boolean = false, textColor: Color) {
    Surface(
        color = if (isHighlight) IgniRed.copy(alpha = 0.3f) else textColor.copy(alpha = 0.15f),
        shape = CircleShape,
        modifier = Modifier.padding(vertical = 4.dp),
        border = if (isHighlight) BorderStroke(1.dp, IgniRed) else null
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (isHighlight) IgniRed else textColor,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

// --- FLICKERING & PULSING PLAYBACK CONTROLS ---
@Composable
private fun PlaybackControls(
    uiState: PlayerUiState,
    viewModel: PlayerViewModel,
    textColor: Color,
    igniFlickerAlpha: Float
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.toggleShuffle() }) {
                Icon(
                    imageVector = WitcherIcons.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (uiState.isShuffleEnabled) IgniRed else textColor.copy(alpha = 0.6f)
                )
            }

            IconButton(onClick = { viewModel.skipToPrevious() }, modifier = Modifier.size(60.dp)) {
                Icon(WitcherIcons.Previous, "Previous", modifier = Modifier.size(36.dp), tint = textColor)
            }
        }

        val dynamicPulseTransition = rememberInfiniteTransition(label = "DynamicPulse")
        val targetPulseScale = if (uiState.isPlaying) 1.15f else 1.05f

        val dynamicPulseScale by dynamicPulseTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = targetPulseScale,
            animationSpec = infiniteRepeatable(
                animation = tween(if (uiState.isPlaying) 700 else 2000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "PulsingAnimation"
        )

        val color1 by animateColorAsState(if (uiState.isPlaying) AardBlue else IgniRed, tween(500), label = "C1")
        val color2 by animateColorAsState(if (uiState.isPlaying) AardBlue.copy(alpha = 0.5f) else Color(0xFFFF5722), tween(500), label = "C2")

        val gradientBrush = Brush.linearGradient(
            colors = listOf(color1, color2)
        )

        val actionIcon = if (uiState.isPlaying) Icons.Rounded.AcUnit else Icons.Rounded.LocalFireDepartment

        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(brush = gradientBrush)
                .graphicsLayer {
                    scaleX = dynamicPulseScale
                    scaleY = dynamicPulseScale
                    alpha = if (!uiState.isPlaying) 1.0f else igniFlickerAlpha
                }
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = { viewModel.togglePlayPause() },
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = actionIcon,
                    contentDescription = "Play/Pause",
                    modifier = Modifier.size(56.dp),
                    tint = Color.White
                )
            }
        }

        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.skipToNext() }, modifier = Modifier.size(60.dp)) {
                Icon(WitcherIcons.Next, "Next", modifier = Modifier.size(36.dp), tint = textColor)
            }

            IconButton(onClick = { viewModel.toggleRepeat() }) {
                Icon(
                    imageVector = WitcherIcons.Repeat,
                    contentDescription = "Repeat",
                    tint = if (uiState.repeatMode != Player.REPEAT_MODE_OFF) IgniRed else textColor.copy(alpha = 0.6f)
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return String.format("%02d:%02d", minutes, seconds)
}
package com.example.tigerplayer.ui.player

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Subject
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.ui.library.SongOptionsSheet
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.aardBlue
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.igniRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

// --- Thematic Witcher Colors ---
private val IgniRed = Color(0xFFF11F1A)
private val AardBlue = Color(0xFF4FC3F7)
private val SpotifyGreen = Color(0xFF1DB954)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerScreen(
    viewModel: PlayerViewModel,
    onCollapse: () -> Unit,
    onNavigateToAlbum: (String) -> Unit
) {
    // --- 1. SLICED STATE COLLECTION ---
    val currentTrack by remember(viewModel) {
        viewModel.uiState.map { it.currentTrack }.distinctUntilChanged()
    }.collectAsState(initial = null)

    val track = currentTrack ?: return
    val context = LocalContext.current

    val currentLyrics by remember(viewModel) {
        viewModel.uiState.map { it.currentLyrics }.distinctUntilChanged()
    }.collectAsState(initial = null)

    val queue by remember(viewModel) {
        viewModel.uiState.map { it.queue }.distinctUntilChanged()
    }.collectAsState(initial = emptyList())

    val artistImageUrl by remember(viewModel) {
        viewModel.uiState.map { it.artistImageUrl }.distinctUntilChanged()
    }.collectAsState(initial = null)

    val isPlaying by remember(viewModel) {
        viewModel.uiState.map { it.isPlaying }.distinctUntilChanged()
    }.collectAsState(initial = false)
    val playlists by remember(viewModel) {
        viewModel.customPlaylists
    }.collectAsState(initial = emptyList())

    var showOptionsSheet by remember { mutableStateOf(false) }

    // The trigger for the portal

    // --- 2. THEME-AWARE STATE HOISTING ---
    var showTechnicalInfo by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var isLikedLocally by remember(track.id) { mutableStateOf(false) }

    val themeSurface = MaterialTheme.colorScheme.surface
    val themeOnSurface = MaterialTheme.colorScheme.onSurface

    var dominantBgColor by remember(themeSurface) { mutableStateOf(themeSurface) }
    var dynamicTextColor by remember(themeOnSurface) { mutableStateOf(themeOnSurface) }
    var dynamicSecondaryTextColor by remember(themeOnSurface) { mutableStateOf(themeOnSurface.copy(alpha = 0.6f)) }

    // Gesture Animation State
    val scope = rememberCoroutineScope()
    val offsetXAnimate = remember { Animatable(0f) }
    val dragThreshold = 150f

    // --- 3. THE DYNAMIC COLOR RITUAL ---
    val imageUrl = if (showLyrics) artistImageUrl ?: track.artworkUri else track.artworkUri

    val imageRequest = remember(imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(true)
            .allowHardware(false)
            .listener(onSuccess = { _, result ->
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                bitmap?.let { b ->
                    Palette.from(b).generate { palette ->
                        val dominantSwatch = palette?.dominantSwatch ?: palette?.mutedSwatch
                        dominantBgColor = dominantSwatch?.rgb?.let { Color(it) } ?: Color(0xFF121212)

                        val luminance = ColorUtils.calculateLuminance(dominantBgColor.value.toLong().toInt())
                        if (luminance > 0.4) {
                            dynamicTextColor = Color(0xFF1A1A1A)
                            dynamicSecondaryTextColor = Color(0xFF1A1A1A).copy(alpha = 0.7f)
                        } else {
                            dynamicTextColor = Color(0xFFF5F5F5)
                            dynamicSecondaryTextColor = Color(0xFFF5F5F5).copy(alpha = 0.7f)
                        }
                    }
                }
            })
            .build()
    }

    // --- 4. THE UI STRUCTURE ---
    // THE MASTER BOX handles the vertical swipe to close
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(dominantBgColor)
            .pointerInput(Unit) {
                // Vertical drag to close the player
                detectDragGestures(
                    onDragEnd = { /* Cleanup if needed */ }
                ) { change, dragAmount ->
                    change.consume()
                    // If they swipe down fast enough
                    if (dragAmount.y > 20) {
                        onCollapse()
                    }
                }
            }
    ) {
        // --- LAYER 1: BACKDROP SCRIM ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to dominantBgColor.copy(alpha = 0.1f),
                        0.4f to dominantBgColor.copy(alpha = 0.6f),
                        0.8f to dominantBgColor.copy(alpha = 0.98f),
                        1.0f to dominantBgColor
                    )
                )
        )

        // --- LAYER 2: FOREGROUND UI ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- HEADER RITUAL ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Icon(WitcherIcons.Collapse, "Collapse", tint = dynamicTextColor)
                }

                var showAlbum by remember { mutableStateOf(false) }

                LaunchedEffect(track.id) {
                    showAlbum = false
                    while (isActive) {
                        delay(5000)
                        if (track.album.isNotBlank() && !track.album.contains("Unknown", ignoreCase = true)) {
                            showAlbum = !showAlbum
                        }
                    }
                }

                val currentDisplayText = if (showAlbum) track.album.uppercase() else "NOW PLAYING"

                AnimatedContent(
                    targetState = currentDisplayText,
                    transitionSpec = {
                        val isResettingToNowPlaying = targetState == "NOW PLAYING"
                        if (isResettingToNowPlaying) {
                            (slideInVertically(animationSpec = tween(400, easing = FastOutSlowInEasing)) { -it } +
                                    fadeIn(animationSpec = tween(300)) +
                                    scaleIn(initialScale = 0.9f, animationSpec = tween(400, easing = FastOutSlowInEasing))) togetherWith
                                    slideOutVertically(animationSpec = tween(400, easing = FastOutSlowInEasing)) { it } +
                                    fadeOut(animationSpec = tween(300)) +
                                    scaleOut(targetScale = 0.9f, animationSpec = tween(400, easing = FastOutSlowInEasing))
                        } else {
                            (slideInVertically(animationSpec = tween(400, easing = FastOutSlowInEasing)) { it } +
                                    fadeIn(animationSpec = tween(300)) +
                                    scaleIn(initialScale = 0.9f, animationSpec = tween(400, easing = FastOutSlowInEasing))) togetherWith
                                    slideOutVertically(animationSpec = tween(400, easing = FastOutSlowInEasing)) { -it } +
                                    fadeOut(animationSpec = tween(300)) +
                                    scaleOut(targetScale = 0.9f, animationSpec = tween(400, easing = FastOutSlowInEasing))
                        }.using(SizeTransform(clip = false))
                    },
                    label = "HeaderTicker",
                    modifier = Modifier.padding(horizontal = 80.dp)
                ) { displayedText ->
                    Text(
                        text = displayedText,
                        style = MaterialTheme.typography.labelMedium,
                        color = dynamicTextColor.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    IconButton(onClick = { showLyrics = !showLyrics; showQueue = false }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Subject,
                            "Lyrics",
                            tint = if (showLyrics) MaterialTheme.aardBlue else dynamicTextColor.copy(alpha = 0.5f)
                        )
                    }

                    IconButton(onClick = { showQueue = !showQueue; showLyrics = false }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.QueueMusic,
                            "Queue",
                            tint = if (showQueue) MaterialTheme.aardBlue else dynamicTextColor.copy(alpha = 0.5f)
                        )
                    }

                    // THE NEW GUARD: The Options Portal
                    IconButton(onClick = { showOptionsSheet = true }) {
                        Icon(
                            imageVector = WitcherIcons.Options,
                            contentDescription = "Options",
                            tint = dynamicTextColor.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            // --- VIEWPORT (Artwork / Queue / Lyrics) ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = if (showQueue) "queue" else if (showLyrics) "lyrics" else "artwork",
                    label = "ContentAnim"
                ) { type ->
                    when (type) {
                        "queue" -> QueueDisplay(
                            queue = queue,
                            currentTrackId = track.id,
                            isPlaying = isPlaying,
                            dynamicTextColor = dynamicTextColor,
                            onTrackClick = { viewModel.playTrack(it) },
                            onRemoveFromQueue = { viewModel.removeFromQueue(it) }
                        )

                        "lyrics" -> LyricsDisplay(
                            lyrics = currentLyrics,
                            viewModel = viewModel,
                            textColor = dynamicTextColor
                        )

                        "artwork" -> {
                            // --- 1. THE CINEMATIC DRIFT ENGINE ---
                            val infiniteTransition =
                                rememberInfiniteTransition(label = "ArtworkDrift")

                            // We use mismatched, prime-number durations (25s, 18s, 22s) so the animations
                            // desync from each other. This creates an organic, unpredictable floating
                            // effect rather than a cheap, repeating ping-pong loop.
                            val driftScale by infiniteTransition.animateFloat(
                                initialValue = 1.0f,
                                targetValue = 1.15f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(25000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "DriftScale"
                            )

                            val driftPanX by infiniteTransition.animateFloat(
                                initialValue = -15f,
                                targetValue = 15f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(18000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "DriftPanX"
                            )

                            val driftPanY by infiniteTransition.animateFloat(
                                initialValue = -10f,
                                targetValue = 10f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(22000, easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "DriftPanY"
                            )

                            // --- 2. THE VIEWPORT CONTAINER ---
                            // The Box handles the physical realm: Swipe physics, constraints, and shadows.
                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(offsetXAnimate.value.roundToInt(), 0) }
                                    .pointerInput(Unit) {
                                        detectHorizontalDragGestures(
                                            onHorizontalDrag = { change, dragAmount ->
                                                change.consume()
                                                scope.launch {
                                                    offsetXAnimate.snapTo(offsetXAnimate.value + (dragAmount * 0.5f))
                                                }
                                            },
                                            onDragEnd = {
                                                scope.launch {
                                                    if (offsetXAnimate.value > dragThreshold) {
                                                        viewModel.skipToPrevious()
                                                    } else if (offsetXAnimate.value < -dragThreshold) {
                                                        viewModel.skipToNext()
                                                    }
                                                    offsetXAnimate.animateTo(
                                                        targetValue = 0f,
                                                        animationSpec = tween(
                                                            300,
                                                            easing = FastOutSlowInEasing
                                                        )
                                                    )
                                                }
                                            }
                                        )
                                    }
                                    .size(320.dp)
                                    // The neon shadow bleed cast by the extracted Palette color
                                    .shadow(
                                        40.dp,
                                        MaterialTheme.shapes.extraLarge,
                                        spotColor = dominantBgColor
                                    )
                                    .clip(MaterialTheme.shapes.extraLarge)
                            ) {
                                // --- 3. THE LIVING CANVAS ---
                                // The Image handles the visual realm: Drifting slowly within the clipped bounds.
                                AsyncImage(
                                    model = imageRequest,
                                    contentDescription = "Album Art",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize() // Fills the container safely
                                        .graphicsLayer {
                                            // Apply the sophisticated parallax only when the music is actively playing
                                            if (isPlaying) {
                                                scaleX = driftScale
                                                scaleY = driftScale
                                                translationX = driftPanX
                                                translationY = driftPanY
                                            }
                                        }
                                )
                            }
                        }
                    }
                }
            }

            // --- TRACK INFO ---
            AnimatedVisibility(
                visible = !showLyrics && !showQueue,
                enter = fadeIn() + slideInVertically { it / 4 },
                exit = fadeOut() + slideOutVertically { it / 4 }
            ) {
                Column {
                    TrackInfoCard(
                        track = track,
                        viewModel = viewModel,
                        textColor = dynamicTextColor,
                        secondaryTextColor = dynamicSecondaryTextColor,
                        isLiked = isLikedLocally,
                        onLikeClick = { isLikedLocally = it },
                        showTechnicalInfo = showTechnicalInfo,
                        onToggleTechInfo = { showTechnicalInfo = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            FieryWavySeeker(viewModel = viewModel, textColor = dynamicTextColor)

            Spacer(modifier = Modifier.height(16.dp))
            PlaybackControls(viewModel = viewModel, textColor = dynamicTextColor, igniFlickerAlpha = 1f)
            Spacer(modifier = Modifier.height(24.dp))
        }
// --- LAYER 4: THE OPTIONS PORTAL ---
        if (showOptionsSheet) {
            SongOptionsSheet(
                track = track,
                playlists = playlists,
                onDismiss = { showOptionsSheet = false },
                onPlayNext = {
                    viewModel.addToQueue(track)
                },
                onAddToPlaylist = { playlistId ->
                    viewModel.addTrackToPlaylist(playlistId, track)
                },
                onGoToAlbum = { albumName ->
                    // 1. Close the sheet
                    showOptionsSheet = false
                    // 2. Collapse the player
                    onCollapse()
                    // 3. Navigate to the album
                    onNavigateToAlbum(albumName)
                }
            )
        }
    } // <-- This is the final closing brace of your Master Box
}

// ==========================================
// --- TRACK INFO CARD ---
// ==========================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackInfoCard(
    track: AudioTrack,
    viewModel: PlayerViewModel,
    textColor: Color,
    secondaryTextColor: Color,
    isLiked: Boolean,
    onLikeClick: (Boolean) -> Unit,
    showTechnicalInfo: Boolean,
    onToggleTechInfo: (Boolean) -> Unit
) {
    if (showTechnicalInfo) {
        AlertDialog(
            onDismissRequest = { onToggleTechInfo(false) },
            confirmButton = {
                TextButton(onClick = { onToggleTechInfo(false) }) {
                    Text("CLOSE", color = MaterialTheme.igniRed, fontWeight = FontWeight.Bold)
                }
            },
            title = { Text("TECHNICAL SPECIFICATIONS", style = MaterialTheme.typography.labelLarge, color = textColor) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TechRow("Path", track.path ?: "Unknown", textColor)
                    TechRow("Sample Rate", "${track.sampleRate ?: "Unknown"} Hz", textColor)
                    TechRow("Bitrate", "${track.bitrate / 1000} kbps", textColor)
                    TechRow("Format", track.mimeType, textColor)
                }
            },
            containerColor = Color(0xFF121212),
            shape = RoundedCornerShape(16.dp)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = textColor,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE, velocity = 40.dp)
                )

                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = secondaryTextColor,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                )
            }

            IconButton(
                onClick = {
                    val newLikedState = !isLiked
                    onLikeClick(newLikedState)
                    // Ensure your ViewModel logic exists
                    // viewModel.addTrackToLikedSongs(track)
                },
                modifier = Modifier
                    .padding(start = 16.dp)
                    .bounceClick { }
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "Like Song",
                    // THE FIX: Adaptive IgniRed
                    tint = if (isLiked) MaterialTheme.igniRed else textColor,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- THE MISSING METADATA BADGES RESTORED ---
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val format = track.mimeType.split("/").lastOrNull()?.uppercase() ?: "AUDIO"

            MetadataBadge(
                text = if (track.mimeType.contains("flac")) "HI-RES" else format,
                isHighlight = track.mimeType.contains("flac"),
                textColor = textColor,
                onLongClick = { onToggleTechInfo(true) }
            )

            if (track.bitrate > 0) {
                MetadataBadge(
                    text = "${track.bitrate / 1000} KBPS",
                    textColor = textColor,
                    onLongClick = { onToggleTechInfo(true) }
                )
            }

            track.year?.let {
                MetadataBadge(
                    text = it,
                    textColor = textColor,
                    onLongClick = { onToggleTechInfo(true) }
                )
            }
        }
    }
}

@Composable
private fun TechRow(label: String, value: String, textColor: Color) {
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.5f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = textColor, fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MetadataBadge(
    text: String,
    isHighlight: Boolean = false,
    textColor: Color,
    onLongClick: () -> Unit = {}
) {
    Surface(
        color = if (isHighlight) IgniRed.copy(alpha = 0.2f) else textColor.copy(alpha = 0.1f),
        shape = CircleShape,
        modifier = Modifier.combinedClickable(onClick = { }, onLongClick = onLongClick),
        border = if (isHighlight) BorderStroke(1.dp, IgniRed.copy(alpha = 0.5f)) else BorderStroke(1.dp, textColor.copy(alpha = 0.2f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (isHighlight) IgniRed else textColor,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

// ==========================================
// --- CONTROLS & SEEKER ---
// ==========================================

@Composable
fun FieryWavySeeker(
    viewModel: PlayerViewModel,
    textColor: Color
) {
    val uiState by viewModel.uiState.collectAsState()
    val track = uiState.currentTrack ?: return

    // THE FIX: Adaptive Signs
    val accentColor = if (uiState.isPlaying) MaterialTheme.igniRed else MaterialTheme.aardBlue

    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableStateOf(0f) }

    val duration = track.durationMs.coerceAtLeast(1L)
    val progressValue = (uiState.currentPosition.toFloat() / duration)
    val actualProgress = if (progressValue.isNaN()) 0f else progressValue.coerceIn(0f, 1f)

    val targetProgress = if (isDragging) dragProgress else actualProgress

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

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
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
                    color = textColor.copy(alpha = 0.2f),
                    start = Offset(0f, centerY),
                    end = Offset(width, centerY),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )

                val path = androidx.compose.ui.graphics.Path()
                val waveAmplitude = if (uiState.isPlaying) 12f else 0f
                val waveFrequency = 0.05f

                path.moveTo(0f, centerY)
                for (x in 0..progressX.toInt() step 5) {
                    val y = centerY + (waveAmplitude * kotlin.math.sin(x * waveFrequency + phase))
                    path.lineTo(x.toFloat(), y)
                }

                drawPath(path = path, color = accentColor, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
                drawCircle(color = accentColor, radius = 6.dp.toPx(), center = Offset(progressX, centerY + (waveAmplitude * kotlin.math.sin(progressX * waveFrequency + phase))))
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val displayPosition = if (isDragging) (dragProgress * track.durationMs).toLong() else uiState.currentPosition
            Text(text = formatDuration(displayPosition), color = textColor.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
            Text(text = formatDuration(track.durationMs), color = textColor.copy(alpha = 0.6f), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun PlaybackControls(
    viewModel: PlayerViewModel,
    textColor: Color,
    igniFlickerAlpha: Float
) {
    val uiState by viewModel.uiState.collectAsState()

    Row(
        modifier = Modifier.fillMaxWidth(),
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
                    // THE FIX: Adaptive AardBlue
                    tint = if (uiState.isShuffleEnabled) MaterialTheme.aardBlue else textColor.copy(alpha = 0.6f)
                )
            }
            IconButton(
                onClick = { viewModel.skipToPrevious() },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(WitcherIcons.Previous, "Previous", modifier = Modifier.size(32.dp), tint = textColor)
            }
        }

        val dynamicPulseTransition = rememberInfiniteTransition(label = "DynamicPulse")
        val targetPulseScale = if (uiState.isPlaying) 1.15f else 1.0f
        val dynamicPulseScale by dynamicPulseTransition.animateFloat(
            initialValue = 1.0f, targetValue = targetPulseScale,
            animationSpec = infiniteRepeatable(
                animation = tween(if (uiState.isPlaying) 700 else 2000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "PulsingAnimation"
        )

        val isPlaying = uiState.isPlaying
        val elementTransition = rememberInfiniteTransition(label = "ElementPulse")
        // These elemental colors remain raw to preserve the exact Fire/Ice visual effect
        val heatingColor by elementTransition.animateColor(initialValue = Color(0xFFF11F1A), targetValue = Color(0xFFFF9100), animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "Heating")
        val coolingColor by elementTransition.animateColor(initialValue = Color(0xFF0D47A1), targetValue = Color(0xFF81D4FA), animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "Cooling")

        val color1 by animateColorAsState(if (isPlaying) heatingColor else coolingColor, tween(500), label = "C1")
        val color2 by animateColorAsState(if (isPlaying) Color(0xFFFFD700) else Color(0xFFE1F5FE), tween(500), label = "C2")

        val gradientBrush = Brush.linearGradient(colors = listOf(color1, color2))
        val actionIcon = if (isPlaying) Icons.Rounded.LocalFireDepartment else Icons.Rounded.AcUnit

        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(brush = gradientBrush)
                .graphicsLayer {
                    scaleX = dynamicPulseScale;
                    scaleY = dynamicPulseScale;
                    alpha = if (!isPlaying) 1.0f else igniFlickerAlpha
                }
                .bounceClick { viewModel.togglePlayPause() }
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = actionIcon,
                contentDescription = "Play/Pause",
                modifier = Modifier.size(48.dp),
                tint = if (ColorUtils.calculateLuminance(color1.toArgb()) > 0.5) Color.Black else Color.White
            )
        }

        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.skipToNext() },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(WitcherIcons.Next, "Next", modifier = Modifier.size(32.dp), tint = textColor)
            }
            IconButton(onClick = { viewModel.toggleRepeat() }) {
                Icon(
                    imageVector = WitcherIcons.Repeat,
                    contentDescription = "Repeat",
                    // THE FIX: Assuming you have access to androidx.media3.common.Player
                    tint = if (uiState.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF) MaterialTheme.aardBlue else textColor.copy(alpha = 0.6f)
                )
            }
        }
    }
}

// ==========================================
// --- QUEUE & LYRICS ---
// ==========================================

@Composable
fun QueueDisplay(
    queue: List<AudioTrack>,
    currentTrackId: String?,
    isPlaying: Boolean,
    dynamicTextColor: Color,
    onTrackClick: (AudioTrack) -> Unit,
    onRemoveFromQueue: (AudioTrack) -> Unit
) {
    if (queue.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "The queue is empty.\nNo shadows follow.",
                color = dynamicTextColor.copy(alpha = 0.4f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge
            )
        }
    } else {
        val listState = rememberLazyListState()
        val currentIndex = remember(queue, currentTrackId) {
            queue.indexOfFirst { it.id == currentTrackId }.coerceAtLeast(0)
        }

        LaunchedEffect(currentTrackId) {
            if (currentIndex >= 0) {
                listState.animateScrollToItem(currentIndex, scrollOffset = -200)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 150.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(items = queue, key = { index, track -> "${track.id}_$index" }) { _, track ->
                val isActive = track.id == currentTrackId

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .bounceClick { onTrackClick(track) },
                    color = if (isActive) dynamicTextColor.copy(alpha = 0.1f) else Color.Transparent,
                    shape = RoundedCornerShape(16.dp),
                    border = if (isActive) BorderStroke(1.dp, dynamicTextColor.copy(alpha = 0.3f)) else null
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(48.dp)) {
                            AsyncImage(
                                model = track.artworkUri, contentDescription = null, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                            )
                            if (isActive && isPlaying) {
                                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.4f)), contentAlignment = Alignment.Center) {
                                    Icon(WitcherIcons.VolumeUp, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = track.title, style = MaterialTheme.typography.titleMedium, fontWeight = if (isActive) FontWeight.Black else FontWeight.Bold, color = dynamicTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(text = track.artist, style = MaterialTheme.typography.bodySmall, color = dynamicTextColor.copy(alpha = 0.6f), maxLines = 1)
                        }
                        if (!isActive) {
                            IconButton(onClick = { onRemoveFromQueue(track) }) {
                                Icon(WitcherIcons.Close, null, tint = dynamicTextColor.copy(0.4f), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

data class LyricLine(val timeMs: Long, val text: String)

private fun parseLrc(lrc: String?): List<LyricLine> {
    if (lrc.isNullOrBlank()) return emptyList()
    val regex = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\](.*)""")
    return lrc.lines().mapNotNull { line ->
        val match = regex.find(line)
        if (match != null) {
            val (min, sec, msStr, text) = match.destructured
            val ms = when (msStr.length) {
                0 -> 0L
                1 -> msStr.toLong() * 100
                2 -> msStr.toLong() * 10
                else -> msStr.toLong()
            }
            LyricLine(min.toLong() * 60000 + sec.toLong() * 1000 + ms, text.trim())
        } else null
    }
}

@Composable
private fun LyricsDisplay(lyrics: String?, viewModel: PlayerViewModel, textColor: Color) {
    if (lyrics.isNullOrBlank()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = "FETCHING LYRICS HANG ON!", color = textColor.copy(alpha = 0.4f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        }
    } else {
        val parsedLyrics = remember(lyrics) { parseLrc(lyrics) }

        if (parsedLyrics.isEmpty()) {
            val scrollState = rememberScrollState()
            Text(text = lyrics, color = textColor, modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(24.dp).padding(bottom = 200.dp), style = MaterialTheme.typography.titleLarge.copy(lineHeight = 38.sp, fontWeight = FontWeight.Bold), textAlign = TextAlign.Center)
        } else {
            val listState = rememberLazyListState()
            val currentPositionState = remember(viewModel) { viewModel.uiState.map { it.currentPosition } }.collectAsState(initial = 0L)

            val activeIndex by remember(parsedLyrics) {
                derivedStateOf {
                    val currentMs = currentPositionState.value
                    parsedLyrics.indexOfLast { it.timeMs <= currentMs }.coerceAtLeast(0)
                }
            }

            LaunchedEffect(activeIndex) {
                if (parsedLyrics.isNotEmpty() && !listState.isScrollInProgress) {
                    listState.animateScrollToItem(index = activeIndex, scrollOffset = 0)
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val verticalPadding = (maxHeight / 2) - 24.dp

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = verticalPadding, bottom = verticalPadding),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    itemsIndexed(parsedLyrics, key = { _, line -> line.timeMs }) { index, line ->
                        val isActive = index == activeIndex
                        val alpha by animateFloatAsState(if (isActive) 1f else 0.3f, tween(400), label = "Alpha")
                        val scale by animateFloatAsState(if (isActive) 1.15f else 1f, spring(dampingRatio = Spring.DampingRatioLowBouncy), label = "Scale")

                        Text(
                            text = if (line.text.isBlank()) "•••" else line.text,
                            color = if (isActive) IgniRed else textColor.copy(alpha = alpha),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = if (isActive) FontWeight.Black else FontWeight.Bold, lineHeight = 38.sp, fontSize = if (isActive) 24.sp else 20.sp),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 24.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                        )
                    }
                }
            }
        }
    }
}




@Composable
fun DynamicMetadataTicker(
    title: String,
    album: String,
    artist: String, // Keeping artist static underneath grounds the UI
    primaryColor: Color,
    secondaryColor: Color
) {
    // State to track what is currently being displayed
    var showAlbum by remember { mutableStateOf(false) }

    // THE CYCLE RITUAL
    // We key this to the `title` so that when the song changes,
    // it instantly resets to showing the Title first.
    LaunchedEffect(title) {
        showAlbum = false // Always start with the song title
        while (isActive) {
            delay(5000) // Wait 5 seconds

            // Only toggle if the album name actually exists and isn't "Unknown Album"
            if (album.isNotBlank() && !album.contains("Unknown", ignoreCase = true)) {
                showAlbum = !showAlbum
            }
        }
    }

    Column {
        // --- THE DYNAMIC TOP LINE (Title <-> Album) ---
        AnimatedContent(
            targetState = showAlbum,
            transitionSpec = {
                // A premium "Sly" transition: New text slides up and fades in,
                // while old text slides up and fades out.
                if (targetState) {
                    (slideInVertically { height -> height } + fadeIn(tween(500))) togetherWith
                            slideOutVertically { height -> -height } + fadeOut(tween(500))
                } else {
                    (slideInVertically { height -> -height } + fadeIn(tween(500))) togetherWith
                            slideOutVertically { height -> height } + fadeOut(tween(500))
                }.using(SizeTransform(clip = false))
            },
            label = "MetadataTicker"
        ) { isShowingAlbum ->
            if (isShowingAlbum) {
                Text(
                    text = "ALBUM • ${album.uppercase()}",
                    style = MaterialTheme.typography.titleMedium,
                    color = secondaryColor, // Slightly dimmed to indicate it's contextual
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                )
            } else {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium, // Or headlineLarge for FullPlayer
                    color = primaryColor,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                )
            }
        }

        // --- THE STATIC BOTTOM LINE (Artist) ---
        Text(
            text = artist,
            style = MaterialTheme.typography.bodyMedium,
            color = secondaryColor.copy(alpha = 0.8f),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatDuration(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return String.format("%02d:%02d", minutes, seconds)
}
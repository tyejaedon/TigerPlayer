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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.tigerplayer.R
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.sin

// --- Thematic Witcher Colors ---
private val IgniRed = Color(0xFFF11F1A)
private val AardBlue = Color(0xFF4FC3F7)
private val SpotifyGreen = Color(0xFF1DB954)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FullPlayerScreen(
    viewModel: PlayerViewModel,
    onCollapse: () -> Unit,
    onNavigateToAlbum: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val track = uiState.currentTrack ?: return
    val context = LocalContext.current

    // --- 1. THEME-AWARE STATE HOISTING ---
    var showTechnicalInfo by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var isLikedLocally by remember(track.id) { mutableStateOf(false) }

    val themeSurface = MaterialTheme.colorScheme.surface
    val themeOnSurface = MaterialTheme.colorScheme.onSurface

    // The "Dominant Resonance" colors
    var dominantBgColor by remember(themeSurface) { mutableStateOf(themeSurface) }
    var dynamicTextColor by remember(themeOnSurface) { mutableStateOf(themeOnSurface) }
    var dynamicSecondaryTextColor by remember(themeOnSurface) { mutableStateOf(themeOnSurface.copy(alpha = 0.6f)) }

    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    // --- 2. THE DYNAMIC COLOR RITUAL ---
    val imageUrl = if (showLyrics) uiState.artistImageUrl ?: track.artworkUri else track.artworkUri

    val imageRequest = remember(imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(true)
            .allowHardware(false) // Required for Palette extraction
            .listener(onSuccess = { _, result ->
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                bitmap?.let { b ->
                    Palette.from(b).generate { palette ->
                        // 1. Extract the deep base color for the background gradient
                        val dominantSwatch = palette?.dominantSwatch ?: palette?.mutedSwatch
                        dominantBgColor = dominantSwatch?.rgb?.let { Color(it) } ?: Color(0xFF121212)

                        // 2. Extract luminance to flip text color for legibility
                        val luminance = ColorUtils.calculateLuminance(dominantBgColor.value.toLong().toInt()) // Convert Compose Color to Int
                        if (luminance > 0.4) {
                            // Bright Art -> Obsidian Text
                            dynamicTextColor = Color(0xFF1A1A1A)
                            dynamicSecondaryTextColor = Color(0xFF1A1A1A).copy(alpha = 0.7f)
                        } else {
                            // Dark Art -> White Text
                            dynamicTextColor = Color(0xFFF5F5F5)
                            dynamicSecondaryTextColor = Color(0xFFF5F5F5).copy(alpha = 0.7f)
                        }
                    }
                }
            })
            .build()
    }

    // --- 3. THE UI STRUCTURE ---
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(dominantBgColor) // Base falls back to the dominant color
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
                        } else if (offsetY > 150) onCollapse()
                        offsetX = 0f; offsetY = 0f
                    }
                )
            }
    ) {
        // --- LAYER 1: THE BACKDROP RITUAL ---
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val infiniteScale = 1.05f
                    scaleX = infiniteScale; scaleY = infiniteScale
                }
        )

        // --- LAYER 2: THE DOMINANT RESONANCE SCRIM ---
        // Fades from transparent (top) down to solid dominant color (bottom)
        // This ensures the album art is visible up top, but the text is perfectly readable below.
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

        // --- LAYER 3: THE FOREGROUND UI ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp) // Tightened for S22
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // --- HEADER RITUAL ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(WitcherIcons.Collapse, "Collapse", tint = dynamicTextColor)
                }

                // Toggle Switches for Lyrics/Queue
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(onClick = { showLyrics = !showLyrics; showQueue = false }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Subject,
                            "Lyrics",
                            tint = if (showLyrics) AardBlue else dynamicTextColor.copy(alpha = 0.5f)
                        )
                    }
                    IconButton(onClick = { showQueue = !showQueue; showLyrics = false }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.QueueMusic,
                            "Queue",
                            tint = if (showQueue) AardBlue else dynamicTextColor.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            // --- VIEWPORT: LYRICS / QUEUE / EMPTY (Art shines through) ---
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
                            queue = uiState.queue,
                            currentTrackId = track.id,
                            isPlaying = uiState.isPlaying,
                            dynamicTextColor = dynamicTextColor,
                            onTrackClick = { viewModel.playTrack(it) },
                            onRemoveFromQueue = { viewModel.removeFromQueue(it) }
                        )
                        "lyrics" -> LyricsDisplay(
                            lyrics = uiState.currentLyrics,
                            currentPositionMs = uiState.currentPosition,
                            textColor = dynamicTextColor
                        )
                        else -> {
                            // THE FIX: Leave this empty!
                            // The background AsyncImage provides the artwork.
                            // Placing the TrackInfoCard here caused the "double render" bug.
                            Box(modifier = Modifier.fillMaxSize())
                        }
                    }
                }
            }

            // --- THE TRACK INFO CARD & CONTROLS ---
            // Sits anchored at the bottom of the screen inside the solid color zone
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
            FieryWavySeeker(uiState, viewModel, dynamicTextColor)

            Spacer(modifier = Modifier.height(16.dp))
            PlaybackControls(uiState, viewModel, dynamicTextColor, 1f)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ==========================================
// --- TRACK INFO CARD (Rectified) ---
// ==========================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrackInfoCard(
    track: AudioTrack,
    viewModel: PlayerViewModel,
    textColor: Color,
    secondaryTextColor: Color,
    isLiked: Boolean,
    onLikeClick: (Boolean) -> Unit,
    showTechnicalInfo: Boolean,
    onToggleTechInfo: (Boolean) -> Unit
) {
    // --- TECHNICAL INFO DIALOG ---
    if (showTechnicalInfo) {
        AlertDialog(
            onDismissRequest = { onToggleTechInfo(false) },
            confirmButton = {
                TextButton(onClick = { onToggleTechInfo(false) }) {
                    Text("CLOSE", color = IgniRed, fontWeight = FontWeight.Bold)
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
            // Dialog stays dark for readability regardless of album art
            containerColor = Color(0xFF121212),
            shape = RoundedCornerShape(16.dp)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Removed heavy glassEffect because the dominant gradient already provides contrast
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
                    modifier = Modifier.basicMarquee(
                        iterations = Int.MAX_VALUE,
                        velocity = 40.dp // Slightly faster for long titles on S22
                    )
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
                    viewModel.addTrackToLikedSongs(track)
                },
                modifier = Modifier.padding(start = 16.dp).bounceClick {  }
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "Like Song",
                    tint = if (isLiked) IgniRed else textColor,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
                MetadataBadge(text = it, textColor = textColor, onLongClick = { onToggleTechInfo(true) })
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
        // The badge tint adapts to the dynamic text color for contrast
        color = if (isHighlight) IgniRed.copy(alpha = 0.2f) else textColor.copy(alpha = 0.1f),
        shape = CircleShape,
        modifier = Modifier
            .combinedClickable(
                onClick = { },
                onLongClick = onLongClick
            ),
        border = if (isHighlight) BorderStroke(1.dp, IgniRed.copy(alpha = 0.5f))
        else BorderStroke(1.dp, textColor.copy(alpha = 0.2f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium, // 9sp from our S22 theme
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
private fun FieryWavySeeker(
    uiState: PlayerUiState,
    viewModel: PlayerViewModel,
    textColor: Color
) {
    val track = uiState.currentTrack ?: return
    val accentColor = if (uiState.isPlaying) IgniRed else AardBlue

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

                val path = Path()
                val waveAmplitude = if (uiState.isPlaying) 12f else 0f // Tighter wave for S22
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
                        width = 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                )

                drawCircle(
                    color = accentColor,
                    radius = 6.dp.toPx(),
                    center = Offset(
                        progressX,
                        centerY + (waveAmplitude * sin(progressX * waveFrequency + phase))
                    )
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val displayPosition = if (isDragging) (dragProgress * track.durationMs).toLong() else uiState.currentPosition
            Text(
                text = formatDuration(displayPosition),
                color = textColor.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = formatDuration(track.durationMs),
                color = textColor.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun PlaybackControls(
    uiState: PlayerUiState,
    viewModel: PlayerViewModel,
    textColor: Color,
    igniFlickerAlpha: Float
) {
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
                    tint = if (uiState.isShuffleEnabled) AardBlue else textColor.copy(alpha = 0.6f)
                )
            }

            IconButton(onClick = { viewModel.skipToPrevious() }, modifier = Modifier.size(56.dp)) {
                Icon(WitcherIcons.Previous, "Previous", modifier = Modifier.size(32.dp), tint = textColor)
            }
        }

        val dynamicPulseTransition = rememberInfiniteTransition(label = "DynamicPulse")
        val targetPulseScale = if (uiState.isPlaying) 1.15f else 1.0f

        val dynamicPulseScale by dynamicPulseTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = targetPulseScale,
            animationSpec = infiniteRepeatable(
                animation = tween(if (uiState.isPlaying) 700 else 2000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "PulsingAnimation"
        )

        val isPlaying = uiState.isPlaying

        val elementTransition = rememberInfiniteTransition(label = "ElementPulse")
        val heatingColor by elementTransition.animateColor(
            initialValue = IgniRed,
            targetValue = Color(0xFFFF9100),
            animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "Heating"
        )
        val coolingColor by elementTransition.animateColor(
            initialValue = Color(0xFF0D47A1),
            targetValue = Color(0xFF81D4FA),
            animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "Cooling"
        )

        val color1 by animateColorAsState(if (isPlaying) heatingColor else coolingColor, tween(500), label = "C1")
        val color2 by animateColorAsState(if (isPlaying) Color(0xFFFFD700) else Color(0xFFE1F5FE), tween(500), label = "C2")

        val gradientBrush = Brush.linearGradient(colors = listOf(color1, color2))
        val actionIcon = if (isPlaying) Icons.Rounded.LocalFireDepartment else Icons.Rounded.AcUnit

        Box(
            modifier = Modifier
                .size(80.dp) // Scaled slightly for S22 width
                .clip(CircleShape)
                .background(brush = gradientBrush)
                .graphicsLayer {
                    scaleX = dynamicPulseScale
                    scaleY = dynamicPulseScale
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
                // Play button icon adapts to the inverse of the background for contrast
                tint = if (ColorUtils.calculateLuminance(color1.toArgb()) > 0.5) Color.Black else Color.White
            )
        }

        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.skipToNext() }, modifier = Modifier.size(56.dp)) {
                Icon(WitcherIcons.Next, "Next", modifier = Modifier.size(32.dp), tint = textColor)
            }

            IconButton(onClick = { viewModel.toggleRepeat() }) {
                Icon(
                    imageVector = WitcherIcons.Repeat,
                    contentDescription = "Repeat",
                    tint = if (uiState.repeatMode != Player.REPEAT_MODE_OFF) AardBlue else textColor.copy(alpha = 0.6f)
                )
            }
        }
    }
}

// ==========================================
// --- QUEUE & LYRICS (Unchanged Logic, Adapted Colors) ---
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
        return
    }

    val listState = rememberLazyListState()
    val currentIndex = remember(queue, currentTrackId) {
        queue.indexOfFirst { it.id == currentTrackId }.coerceAtLeast(0)
    }

    LaunchedEffect(currentTrackId) {
        if (currentIndex >= 0) {
            // S22 Fix: Smoother scroll spec to prevent jarring snaps
            listState.animateScrollToItem(currentIndex, scrollOffset = -200)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 150.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(
            items = queue,
            // We still need the index in case of duplicate songs, but animateItem() handles the movement
            key = { index, track -> "${track.id}_$index" }
        ) { _, track ->
            val isActive = track.id == currentTrackId

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateItem() // THE MASTER FIX: Smoothly animates removals/insertions
                    .bounceClick { onTrackClick(track) },
                color = if (isActive) dynamicTextColor.copy(alpha = 0.1f) else Color.Transparent,
                shape = RoundedCornerShape(16.dp),
                border = if (isActive) BorderStroke(1.dp, dynamicTextColor.copy(alpha = 0.3f)) else null
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(48.dp)) {
                        AsyncImage(
                            model = track.artworkUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
                        )
                        if (isActive && isPlaying) {
                            Box(
                                modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.4f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(WitcherIcons.VolumeUp, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isActive) FontWeight.Black else FontWeight.Bold,
                            color = dynamicTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = dynamicTextColor.copy(alpha = 0.6f),
                            maxLines = 1
                        )
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

data class LyricLine(val timeMs: Long, val text: String)

private fun parseLrc(lrc: String?): List<LyricLine> {
    if (lrc.isNullOrBlank()) return emptyList()
    // THE FIX: A highly forgiving Regex that handles 1, 2, 3 digits, or no decimals at all
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
private fun LyricsDisplay(lyrics: String?, currentPositionMs: Long, textColor: Color) {
    if (lyrics.isNullOrBlank()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "INSTRUMENTAL",
                color = textColor.copy(alpha = 0.4f),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
        }
        return
    }

    val parsedLyrics = remember(lyrics) { parseLrc(lyrics) }

    // Fallback if the parser found zero valid timestamps
    if (parsedLyrics.isEmpty()) {
        val scrollState = rememberScrollState()
        Text(
            text = lyrics,
            color = textColor,
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState).padding(24.dp).padding(bottom = 200.dp),
            style = MaterialTheme.typography.titleLarge.copy(lineHeight = 38.sp, fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        return
    }

    val listState = rememberLazyListState()

    // THE FIX 1: Clean calculation without derivedStateOf traps
    val activeIndex = remember(currentPositionMs, parsedLyrics) {
        parsedLyrics.indexOfLast { it.timeMs <= currentPositionMs }.coerceAtLeast(0)
    }

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    // THE MASTER SCROLL RITUAL
    LaunchedEffect(activeIndex) {
        if (parsedLyrics.isNotEmpty()) {
            // THE FIX 2: We removed the `!isScrollInProgress` check.
            // Now, every time the lyric changes, it commands the list to move.
            // By passing scrollOffset = 0, we align the item exactly at the start
            // of the content padding (which is the center of your S22 screen).
            listState.animateScrollToItem(index = activeIndex, scrollOffset = 0)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        // THE CENTERING ANCHOR: Pushes the very first item down to the exact middle
        contentPadding = PaddingValues(top = screenHeight / 2, bottom = screenHeight / 2),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        itemsIndexed(parsedLyrics) { index, line ->
            val isActive = index == activeIndex

            val alpha by animateFloatAsState(if (isActive) 1f else 0.3f, tween(400), label = "Alpha")
            val scale by animateFloatAsState(if (isActive) 1.15f else 1f, spring(dampingRatio = Spring.DampingRatioLowBouncy), label = "Scale")

            Text(
                text = if (line.text.isBlank()) "•••" else line.text,
                color = if (isActive) IgniRed else textColor.copy(alpha = alpha),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = if (isActive) FontWeight.Black else FontWeight.Bold,
                    lineHeight = 38.sp,
                    // S22 Optimization: Kept large enough to read, small enough to fit long lines
                    fontSize = if (isActive) 24.sp else 20.sp
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 24.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
            )
        }
    }
}
private fun formatDuration(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return String.format("%02d:%02d", minutes, seconds)
}
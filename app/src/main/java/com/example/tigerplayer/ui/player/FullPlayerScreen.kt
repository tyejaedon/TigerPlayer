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
import androidx.compose.foundation.layout.padding
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
import com.example.tigerplayer.ui.theme.glassEffect
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FullPlayerScreen(
    viewModel: PlayerViewModel,
    onCollapse: () -> Unit,
    onNavigateToAlbum: (String) -> Unit
) {
    // --- 1. SINGLE SOURCE OF TRUTH ---
    val uiState by viewModel.uiState.collectAsState()
    val track = uiState.currentTrack ?: return
    val context = LocalContext.current

    // --- 2. THE MOTION THROTTLE ---
    val motionThrottle by animateFloatAsState(
        targetValue = if (uiState.isPlaying) 1f else 0f,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "MotionThrottle"
    )

    // --- 3. UI STATE ---
    var showOptionsSheet by remember { mutableStateOf(false) }
    var showTechnicalInfo by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }

    val themeSurface = MaterialTheme.colorScheme.surface
    val themeOnSurface = MaterialTheme.colorScheme.onSurface
    var dominantBgColor by remember(themeSurface) { mutableStateOf(themeSurface) }
    var dynamicTextColor by remember(themeOnSurface) { mutableStateOf(themeOnSurface) }
    var dynamicSecondaryTextColor by remember(themeOnSurface) { mutableStateOf(themeOnSurface.copy(alpha = 0.6f)) }

    val scope = rememberCoroutineScope()
    val offsetXAnimate = remember { Animatable(0f) }
    val dragThreshold = 150f

    // --- 4. COLOR EXTRACTION ---
    val imageUrl = if (showLyrics) uiState.artistImageUrl ?: track.artworkUri else track.artworkUri
    val imageRequest = remember(imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(true)
            .allowHardware(false)
            .listener(onSuccess = { _, result ->
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                bitmap?.let { b ->
                    Palette.from(b).generate { palette ->
                        dominantBgColor = palette?.dominantSwatch?.rgb?.let { Color(it) } ?: Color(0xFF121212)
                        val luminance = ColorUtils.calculateLuminance(dominantBgColor.toArgb())
                        dynamicTextColor = if (luminance > 0.4) Color(0xFF1A1A1A) else Color(0xFFF5F5F5)
                        dynamicSecondaryTextColor = dynamicTextColor.copy(alpha = 0.7f)
                    }
                }
            })
            .build()
    }

    // --- 5. DRIFT ENGINE ---
    val infiniteTransition = rememberInfiniteTransition(label = "ArtworkDrift")
    val driftScale by infiniteTransition.animateFloat(
        initialValue = 1.0f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(25000, easing = LinearEasing), RepeatMode.Reverse), label = "DriftScale"
    )
    val driftPanX by infiniteTransition.animateFloat(
        initialValue = -25f, targetValue = 25f,
        animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Reverse), label = "DriftPanX"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    if (dragAmount.y > 20) onCollapse()
                }
            }
    ) {
        // LAYER 1: BACKDROP
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scaleFactor = 1f + ((driftScale - 1f) * motionThrottle)
                    scaleX = scaleFactor
                    scaleY = scaleFactor
                    translationX = offsetXAnimate.value + (driftPanX * motionThrottle)
                    alpha = 0.45f + (0.55f * motionThrottle)
                }
        )

        // LAYER 2: SCRIM
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.3f),
                        0.4f to dominantBgColor.copy(alpha = 0.5f * (0.8f + (0.2f * motionThrottle))),
                        1.0f to dominantBgColor.copy(alpha = 0.98f)
                    )
                )
        )

        // LAYER 3: FOREGROUND
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HeaderRitual(
                dynamicTextColor = dynamicTextColor,
                onCollapse = onCollapse,
                showLyrics = showLyrics,
                onToggleLyrics = { showLyrics = it; showQueue = false },
                showQueue = showQueue,
                onToggleQueue = { showQueue = it; showLyrics = false },
                onShowOptions = { showOptionsSheet = true },
                uiState = uiState
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = if (showQueue) "queue" else if (showLyrics) "lyrics" else "artwork",
                    transitionSpec = { fadeIn(tween(500)).togetherWith(fadeOut(tween(400))) },
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
                            currentPosition = uiState.currentPosition,
                            textColor = dynamicTextColor
                        )
                        "artwork" -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectHorizontalDragGestures(
                                            onHorizontalDrag = { change, dragAmount ->
                                                change.consume()
                                                scope.launch { offsetXAnimate.snapTo(offsetXAnimate.value + (dragAmount * 0.5f)) }
                                            },
                                            onDragEnd = {
                                                scope.launch {
                                                    if (offsetXAnimate.value > dragThreshold) viewModel.skipToPrevious()
                                                    else if (offsetXAnimate.value < -dragThreshold) viewModel.skipToNext()
                                                    offsetXAnimate.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                                                }
                                            }
                                        )
                                    }
                            )
                        }
                    }
                }
            }

            // --- THE GLASS DECK ---
            AnimatedVisibility(
                visible = !showLyrics && !showQueue,
                enter = fadeIn(tween(600)) + slideInVertically(initialOffsetY = { it / 3 }, animationSpec = spring(stiffness = Spring.StiffnessLow)),
                exit = fadeOut(tween(400)) + slideOutVertically(targetOffsetY = { it / 3 })
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                        .shadow(elevation = 24.dp * motionThrottle, shape = RoundedCornerShape(32.dp), spotColor = dominantBgColor.copy(alpha = 0.5f))
                        .clip(RoundedCornerShape(32.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(32.dp))
                        .padding(vertical = 20.dp, horizontal = 12.dp)
                ) {
                    TrackInfoCard(
                        track = track,
                        textColor = dynamicTextColor,
                        secondaryTextColor = dynamicSecondaryTextColor,
                        showTechnicalInfo = showTechnicalInfo,
                        onToggleTechInfo = { showTechnicalInfo = it },
                        // Pass the ViewModel's router command via lambda
                        onToggleLike = { viewModel.toggleTrackLikeStatus(track) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    FieryWavySeeker(uiState = uiState, viewModel = viewModel, textColor = dynamicTextColor)
                    Spacer(modifier = Modifier.height(16.dp))
                    PlaybackControls(uiState = uiState, viewModel = viewModel, textColor = dynamicTextColor)
                }
            }
        }

        if (showOptionsSheet) {
            SongOptionsSheet(
                track = track,
                playlists = viewModel.customPlaylists.collectAsState(emptyList()).value,
                onDismiss = { showOptionsSheet = false },
                onPlayNext = { viewModel.addToQueue(track) },
                onAddToPlaylist = { viewModel.addTrackToPlaylist(it, track) },
                onGoToAlbum = { albumName ->
                    showOptionsSheet = false
                    onCollapse()
                    onNavigateToAlbum(albumName)
                }
            )
        }
    }
}

@Composable
fun HeaderRitual(
    dynamicTextColor: Color,
    onCollapse: () -> Unit,
    showLyrics: Boolean,
    onToggleLyrics: (Boolean) -> Unit,
    showQueue: Boolean,
    onToggleQueue: (Boolean) -> Unit,
    onShowOptions: () -> Unit,
    uiState: PlayerUiState
) {
    val track = uiState.currentTrack ?: return

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCollapse, modifier = Modifier.background(dynamicTextColor.copy(alpha = 0.05f), CircleShape)) {
                Icon(WitcherIcons.Collapse, "Collapse", tint = dynamicTextColor)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { onToggleLyrics(!showLyrics) }) {
                    Icon(Icons.AutoMirrored.Rounded.Subject, null, tint = if (showLyrics) MaterialTheme.aardBlue else dynamicTextColor.copy(alpha = 0.5f))
                }
                IconButton(onClick = { onToggleQueue(!showQueue) }) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = if (showQueue) MaterialTheme.aardBlue else dynamicTextColor.copy(alpha = 0.5f)
                    )
                }
                IconButton(onClick = onShowOptions) {
                    Icon(WitcherIcons.Options, null, tint = dynamicTextColor.copy(alpha = 0.5f))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        var showAlbumTitle by remember { mutableStateOf(false) }
        LaunchedEffect(track.id) {
            showAlbumTitle = false
            while (isActive) {
                delay(5000)
                if (track.album.isNotBlank() && !track.album.contains("Unknown", ignoreCase = true)) {
                    showAlbumTitle = !showAlbumTitle
                }
            }
        }

        AnimatedContent(
            targetState = if (showAlbumTitle) track.album.uppercase() else "NOW PLAYING",
            transitionSpec = { (slideInVertically { it } + fadeIn()).togetherWith(slideOutVertically { -it } + fadeOut()) },
            label = "Ticker"
        ) { text ->
            Text(
                text = text, style = MaterialTheme.typography.labelLarge,
                color = dynamicTextColor.copy(alpha = 0.6f), fontWeight = FontWeight.Black,
                letterSpacing = 2.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
        }
    }
}



// ==========================================
// --- TRACK INFO CARD ---
// ==========================================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackInfoCard(
    track: AudioTrack,
    textColor: Color,
    secondaryTextColor: Color,
    showTechnicalInfo: Boolean,
    onToggleTechInfo: (Boolean) -> Unit,
    onToggleLike: () -> Unit // THE FIX: Hoisted action parameter
) {
    // Optimistic Local State
    var isLikedLocally by remember(track.id) { mutableStateOf(track.isLiked) }

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
            .padding(horizontal = 8.dp),
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

            // THE FIX: Direct bounceClick on the Icon, removing the IconButton wrapper
            Icon(
                imageVector = if (isLikedLocally) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = "Like Song",
                tint = if (isLikedLocally) MaterialTheme.igniRed else textColor,
                modifier = Modifier
                    .padding(start = 16.dp)
                    .size(36.dp) // Slightly larger to compensate for removing the IconButton's padding
                    .bounceClick {
                        // 1. Instantly flip the UI
                        isLikedLocally = !isLikedLocally
                        // 2. Alert the parent screen to update the database
                        onToggleLike()
                    }
            )
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
                MetadataBadge(text = "${track.bitrate / 1000} KBPS", textColor = textColor, onLongClick = { onToggleTechInfo(true) })
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
fun FieryWavySeeker(uiState: PlayerUiState, viewModel: PlayerViewModel, textColor: Color) {
    val track = uiState.currentTrack ?: return
    val accentColor = if (uiState.isPlaying) MaterialTheme.igniRed else MaterialTheme.aardBlue

    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableStateOf(0f) }

    val duration = track.durationMs.coerceAtLeast(1L)
    val progressValue = (uiState.currentPosition.toFloat() / duration)
    val actualProgress = if (progressValue.isNaN()) 0f else progressValue.coerceIn(0f, 1f)

    val animatedProgress by animateFloatAsState(
        targetValue = if (isDragging) dragProgress else actualProgress,
        animationSpec = if (uiState.isPlaying && !isDragging) tween(1000, easing = LinearEasing) else spring(),
        label = "SeekerAnim"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "Wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 2 * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "Phase"
    )

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Box(
            modifier = Modifier.fillMaxWidth().height(40.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = { isDragging = false; viewModel.seekTo((dragProgress * duration).toLong()) },
                        onDragCancel = { isDragging = false }
                    ) { change, _ ->
                        change.consume()
                        dragProgress = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerY = size.height / 2
                val progressX = size.width * animatedProgress

                drawLine(textColor.copy(0.2f), Offset(0f, centerY), Offset(size.width, centerY), 4.dp.toPx(), StrokeCap.Round)

                val path = Path()
                val amp = if (uiState.isPlaying) 12f else 0f
                path.moveTo(0f, centerY)
                for (x in 0..progressX.toInt() step 5) {
                    path.lineTo(x.toFloat(), centerY + (amp * sin(x * 0.05f + phase)))
                }
                drawPath(path, accentColor, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                drawCircle(accentColor, 6.dp.toPx(), Offset(progressX, centerY + (amp * sin(progressX * 0.05f + phase))))
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(if (isDragging) (dragProgress * duration).toLong() else uiState.currentPosition), color = textColor.copy(0.6f), style = MaterialTheme.typography.labelMedium)
            Text(formatDuration(duration), color = textColor.copy(0.6f), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun PlaybackControls(
    uiState: PlayerUiState, // Using the monolithic state
    viewModel: PlayerViewModel,
    textColor: Color
) {
    val isPlaying = uiState.isPlaying
    val aardBlue = MaterialTheme.aardBlue
    val igniRed = MaterialTheme.igniRed

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // --- LEFT WING: UTILITY ---
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.toggleShuffle() }) {
                Icon(
                    imageVector = WitcherIcons.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (uiState.isShuffleEnabled) aardBlue else textColor.copy(alpha = 0.5f),
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(
                onClick = { viewModel.skipToPrevious() },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(WitcherIcons.Previous, "Previous", modifier = Modifier.size(36.dp), tint = textColor)
            }
        }

        // --- THE CENTRAL CORE: THE GLASS ORB ---
        val infiniteTransition = rememberInfiniteTransition(label = "PulseCore")

        // Dynamic Pulse for the shadow and scale
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = if (isPlaying) 1.12f else 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(if (isPlaying) 800 else 2000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "Pulse"
        )

        // Elemental Color Shift
        val coreColor by animateColorAsState(
            targetValue = if (isPlaying) igniRed else aardBlue,
            animationSpec = tween(600, easing = FastOutSlowInEasing),
            label = "CoreColor"
        )

        Box(
            modifier = Modifier
                .size(84.dp)
                // 1. The Neon Bleed: The shadow pulses with the music
                .shadow(
                    elevation = (12.dp * pulseScale),
                    shape = CircleShape,
                    spotColor = coreColor.copy(alpha = 0.6f),
                    ambientColor = Color.Transparent
                )
                .clip(CircleShape)
                // 2. The Glass Layering
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            coreColor.copy(alpha = 0.25f),
                            coreColor.copy(alpha = 0.05f)
                        )
                    )
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.4f),
                            coreColor.copy(alpha = 0.1f)
                        )
                    ),
                    shape = CircleShape
                )
                .bounceClick { viewModel.togglePlayPause() },
            contentAlignment = Alignment.Center
        ) {
            // 3. The Animated Icon Transition
            AnimatedContent(
                targetState = isPlaying,
                transitionSpec = {
                    (scaleIn(tween(400)) + fadeIn()).togetherWith(scaleOut(tween(400)) + fadeOut())
                },
                label = "PlayPauseAnim"
            ) { playing ->
                Icon(
                    imageVector = if (playing) WitcherIcons.Pause else WitcherIcons.Play,
                    contentDescription = "Play/Pause",
                    tint = if (playing) Color.White else coreColor,
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        // --- RIGHT WING: UTILITY ---
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.skipToNext() },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(WitcherIcons.Next, "Next", modifier = Modifier.size(36.dp), tint = textColor)
            }
            IconButton(onClick = { viewModel.toggleRepeat() }) {
                Icon(
                    imageVector = when (uiState.repeatMode) {
                        Player.REPEAT_MODE_ONE -> WitcherIcons.RepeatOne
                        else -> WitcherIcons.Repeat
                    },
                    contentDescription = "Repeat",
                    tint = if (uiState.repeatMode != Player.REPEAT_MODE_OFF) aardBlue else textColor.copy(alpha = 0.5f),
                    modifier = Modifier.size(22.dp)
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
private fun LyricsDisplay(lyrics: String?, currentPosition: Long, textColor: Color) {
    if (lyrics.isNullOrBlank()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("NO LYRICS FOUND", color = textColor.copy(0.4f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        }
    } else {
        val parsed = remember(lyrics) { parseLrc(lyrics) }
        val listState = rememberLazyListState()
        val activeIndex = remember(currentPosition, parsed) {
            parsed.indexOfLast { it.timeMs <= currentPosition }.coerceAtLeast(0)
        }

        LaunchedEffect(activeIndex) { listState.animateScrollToItem(activeIndex) }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 200.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(
                items = parsed,
                // THE FIX: Salted timestamp prevents collision in duplicate LRC lines
                key = { index, line -> "lyric_${line.timeMs}_$index" }
            ) { index, line ->
                val isActive = index == activeIndex
                Text(
                    text = line.text.ifBlank { "•••" },
                    color = if (isActive) MaterialTheme.igniRed else textColor.copy(if (isActive) 1f else 0.3f),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, fontSize = if (isActive) 24.sp else 20.sp),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 24.dp).graphicsLayer {
                        val s = if (isActive) 1.1f else 1f
                        scaleX = s; scaleY = s
                    }
                )
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
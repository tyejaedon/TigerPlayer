@file:SuppressLint("NewApi")
package com.example.tigerplayer.ui.player

import android.annotation.SuppressLint
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Subject
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.sin

private val IgniRed = Color(0xFFF11F1A)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FullPlayerScreen(
    viewModel: PlayerViewModel,
    onCollapse: () -> Unit,
    onNavigateToAlbum: (String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val currentTrack = uiState.currentTrack
    var lastKnownTrack by remember { mutableStateOf<AudioTrack?>(null) }

    LaunchedEffect(currentTrack) {
        if (currentTrack != null) {
            lastKnownTrack = currentTrack
        }
    }

    val track = currentTrack ?: lastKnownTrack ?: return
    val context = LocalContext.current

    val motionThrottle by animateFloatAsState(
        targetValue = if (uiState.isPlaying) 1f else 0f,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "MotionThrottle"
    )

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
                        val extractedColor = palette?.dominantSwatch?.rgb?.let { Color(it) } ?: Color(0xFF121212)
                        dominantBgColor = extractedColor
                        viewModel.updateTrackColor(extractedColor)

                        val luminance = ColorUtils.calculateLuminance(extractedColor.toArgb())
                        dynamicTextColor = if (luminance > 0.4) Color(0xFF1A1A1A) else Color(0xFFF5F5F5)
                        dynamicSecondaryTextColor = dynamicTextColor.copy(alpha = 0.7f)
                    }
                }
            })
            .build()
    }

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
                track = track
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
                            shuffleModeEnabled = uiState.isShuffleEnabled,
                            repeatMode = uiState.repeatMode,
                            onRemoveFromQueue = { viewModel.removeFromQueue(it) },
                            onMoveItem = { from, to -> viewModel.moveQueueItem(from, to) }
                        )
                        "lyrics" -> LyricsDisplay(
                            lyrics = uiState.currentLyrics,
                            currentPosition = uiState.currentPosition,
                            textColor = dynamicTextColor
                        )
                        "artwork" -> {
                            Crossfade(
                                targetState = uiState.visualMode,
                                animationSpec = tween(600, easing = FastOutSlowInEasing),
                                label = "VisualModeCrossfade",
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
                            ) { mode ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { translationX = offsetXAnimate.value }
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { viewModel.toggleVisualMode() }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when (mode) {
                                        PlayerVisualMode.ARTWORK -> {
                                            AsyncImage(
                                                model = track.artworkUri,
                                                contentDescription = "Album Cover",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .fillMaxWidth(0.85f)
                                                    .aspectRatio(1f)
                                                    .shadow(48.dp, RoundedCornerShape(32.dp), spotColor = dominantBgColor)
                                                    .clip(RoundedCornerShape(32.dp))
                                            )
                                        }
                                        PlayerVisualMode.WAVEFORM -> {
                                            val isWaveformLoading = uiState.currentWaveform.isEmpty()
                                            val waveform = uiState.currentWaveform.ifEmpty { List(80) { 0.15f } }

                                            val progress = if (track.durationMs > 0L) {
                                                uiState.currentPosition.toFloat() / track.durationMs.toFloat()
                                            } else 0f

                                            FullWaveformSeekBar(
                                                amplitudes = waveform,
                                                progress = progress,
                                                isPlaying = uiState.isPlaying,
                                                color = dynamicTextColor,
                                                isLoading = isWaveformLoading
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

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
                        .background(Color.Transparent)
                        .padding(vertical = 20.dp, horizontal = 12.dp)
                ) {
                    TrackInfoCard(
                        track = track,
                        textColor = dynamicTextColor,
                        secondaryTextColor = dynamicSecondaryTextColor,
                        showTechnicalInfo = showTechnicalInfo,
                        onToggleTechInfo = { showTechnicalInfo = it },
                        onToggleLike = { viewModel.toggleTrackLikeStatus(track) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    FieryWavySeeker(uiState = uiState, track = track, viewModel = viewModel, textColor = dynamicTextColor)
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

// ==========================================
// --- THE LIVE WAVEFORM ENGINE ---
// ==========================================

@Composable
fun FullWaveformSeekBar(
    amplitudes: List<Float>,
    progress: Float,
    isPlaying: Boolean,
    color: Color,
    isLoading: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "LiveWaveform")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label = "Phase"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .height(180.dp), // Massive and prominent
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        amplitudes.forEachIndexed { index, baseAmp ->
            val barProgress = index.toFloat() / amplitudes.size
            val isPlayed = barProgress <= progress

            // --- 1. DYNAMIC AMPLITUDE BOOST ---
            // If the waveform is loading, animate a cool sine-wave. If real, boost the height.
            val actualAmp = if (isLoading) {
                0.15f + 0.1f * sin(phase * 2 + index * 0.2f)
            } else {
                (baseAmp * 1.8f).coerceIn(0.05f, 1f) // 🔥 FIX 1: Multiply amplitude so it looks massive
            }

            // --- 2. THE RIPPLE PHYSICS ---
            val liveRipple = if (isPlaying && !isLoading) {
                val rippleSpeed = phase * if (isPlayed) 1f else 1.5f
                val rippleDensity = if (isPlayed) 0.5f else 0.2f
                0.85f + 0.15f * sin(rippleSpeed + index * rippleDensity)
            } else {
                1f // If paused, NO ripple.
            }

            // --- 3. THE PAUSE TRAP FIX ---
            // We multiply the amp by the ripple. If it's paused, liveRipple is 1f, meaning it
            // completely retains its physical shape instead of flattening out!
            val targetHeight = (actualAmp * liveRipple).coerceIn(0.05f, 1f)

            val animatedHeight by animateFloatAsState(
                targetValue = targetHeight,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
                label = "waveHeight"
            )

            val animatedColor by animateColorAsState(
                targetValue = if (isPlayed) color else color.copy(alpha = 0.2f),
                animationSpec = tween(200),
                label = "waveColor"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(animatedHeight)
                    .clip(RoundedCornerShape(50))
                    .background(animatedColor)
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
    track: AudioTrack
) {
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
    onToggleLike: () -> Unit
) {
    if (showTechnicalInfo) {
        AlertDialog(
            onDismissRequest = { onToggleTechInfo(false) },
            confirmButton = {
                TextButton(onClick = { onToggleTechInfo(false) }) {
                    Text("CLOSE", color = MaterialTheme.igniRed, fontWeight = FontWeight.Bold)
                }
            },
            title = {
                Text(
                    "TECHNICAL SPECIFICATIONS",
                    style = MaterialTheme.typography.labelLarge,
                    color = textColor,
                    fontWeight = FontWeight.Black
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TechRow("Path", track.path ?: "Unknown", textColor)
                    TechRow("Sample Rate", "${track.sampleRate} Hz", textColor)
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
            .padding(horizontal = 4.dp)
            .shadow(
                16.dp,
                RoundedCornerShape(24.dp),
                ambientColor = Color.Transparent,
                spotColor = Color.Black.copy(alpha = 0.5f)
            )
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.4f),
                        Color.Black.copy(alpha = 0.2f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Column(modifier = Modifier.weight(1f)) {

                Text(
                    track.title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = textColor,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(
                        iterations = Int.MAX_VALUE,
                        velocity = 40.dp
                    )
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    track.artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = secondaryTextColor,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
            }

            val scale by animateFloatAsState(
                targetValue = if (track.isLiked) 1.15f else 1f,
                animationSpec = spring(
                    dampingRatio = 0.5f,
                    stiffness = 400f
                ),
                label = "likeScale"
            )

            val tint by animateColorAsState(
                targetValue = if (track.isLiked)
                    MaterialTheme.igniRed
                else
                    textColor.copy(alpha = 0.7f),
                label = "likeColor"
            )

            IconButton(
                onClick = onToggleLike,
                modifier = Modifier
                    .padding(start = 16.dp)
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = if (track.isLiked)
                        Icons.Rounded.Favorite
                    else
                        Icons.Rounded.FavoriteBorder,

                    contentDescription = if (track.isLiked)
                        "Unlike song"
                    else
                        "Like song",

                    tint = tint,

                    modifier = Modifier.scale(scale)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

            val format = track.mimeType
                .substringAfter("/")
                .uppercase()

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

@SuppressLint("AutoboxingStateCreation")
@Composable
fun FieryWavySeeker(
    uiState: PlayerUiState,
    track: AudioTrack,
    viewModel: PlayerViewModel,
    textColor: Color
) {
    val accentColor = if (uiState.isPlaying) MaterialTheme.igniRed else MaterialTheme.aardBlue

    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    val duration = track.durationMs.coerceAtLeast(1L)
    val progressValue = uiState.currentPosition.toFloat() / duration
    val actualProgress = progressValue.coerceIn(0f, 1f).takeIf { !it.isNaN() } ?: 0f

    val animatedProgress by animateFloatAsState(
        targetValue = if (isDragging) dragProgress else actualProgress,
        animationSpec = if (uiState.isPlaying && !isDragging) tween(1000, easing = LinearEasing) else spring(),
        label = "SeekerAnim"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "Wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "Phase"
    )

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .pointerInput(Unit) {
                    var widthPx = 0f
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            widthPx = size.width.toFloat()
                            dragProgress = (offset.x / widthPx).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            isDragging = false
                            viewModel.seekTo((dragProgress * duration).toLong())
                        },
                        onDragCancel = { isDragging = false }
                    ) { change, _ ->
                        change.consume()
                        if (widthPx <= 0f) widthPx = size.width.toFloat()
                        dragProgress = (change.position.x / widthPx).coerceIn(0f, 1f)
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerY = size.height / 2
                val progressX = size.width * animatedProgress

                drawLine(
                    color = textColor.copy(alpha = 0.2f),
                    start = Offset(0f, centerY),
                    end = Offset(size.width, centerY),
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round
                )

                val path = Path()
                val amp = if (uiState.isPlaying) 12f else 0f
                path.moveTo(0f, centerY)

                val endX = progressX.toInt().coerceAtMost(size.width.toInt())
                for (x in 0..endX step 5) {
                    path.lineTo(x.toFloat(), centerY + (amp * sin(x * 0.05f + phase)))
                }

                drawPath(path = path, color = accentColor, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
                drawCircle(color = accentColor, radius = 6.dp.toPx(), center = Offset(progressX, centerY + (amp * sin(progressX * 0.05f + phase))))
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = formatDuration(if (isDragging) (dragProgress * duration).toLong() else uiState.currentPosition),
                color = textColor.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = formatDuration(duration),
                color = textColor.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
fun PlaybackControls(
    uiState: PlayerUiState,
    viewModel: PlayerViewModel,
    textColor: Color
) {
    val isPlaying = uiState.isPlaying
    val aardBlue = MaterialTheme.aardBlue
    val igniRed = MaterialTheme.igniRed

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.toggleShuffle() }) {
                Icon(
                    imageVector = WitcherIcons.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (uiState.isShuffleEnabled) aardBlue else textColor.copy(alpha = 0.5f),
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(onClick = { viewModel.skipToPrevious() }, modifier = Modifier.size(56.dp)) {
                Icon(WitcherIcons.Previous, "Previous", modifier = Modifier.size(36.dp), tint = textColor)
            }
        }

        val infiniteTransition = rememberInfiniteTransition(label = "PulseCore")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 1.0f,
            targetValue = if (isPlaying) 1.12f else 1.0f,
            animationSpec = infiniteRepeatable(tween(if (isPlaying) 800 else 2000, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
            label = "Pulse"
        )
        val coreColor by animateColorAsState(targetValue = if (isPlaying) igniRed else aardBlue, animationSpec = tween(600, easing = FastOutSlowInEasing), label = "CoreColor")

        Box(
            modifier = Modifier
                .size(84.dp)
                .shadow(elevation = (12.dp * pulseScale), shape = CircleShape, spotColor = coreColor.copy(alpha = 0.6f), ambientColor = Color.Transparent)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(coreColor.copy(alpha = 0.25f), coreColor.copy(alpha = 0.05f))))
                .bounceClick { viewModel.togglePlayPause() },
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(targetState = isPlaying, transitionSpec = { (scaleIn(tween(400)) + fadeIn()).togetherWith(scaleOut(tween(400)) + fadeOut()) }, label = "PlayPauseAnim") { playing ->
                Icon(
                    imageVector = if (playing) WitcherIcons.Pause else WitcherIcons.Play,
                    contentDescription = "Play/Pause",
                    tint = if (playing) Color.White else coreColor,
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.skipToNext() }, modifier = Modifier.size(56.dp)) {
                Icon(WitcherIcons.Next, "Next", modifier = Modifier.size(36.dp), tint = textColor)
            }
            IconButton(onClick = { viewModel.toggleRepeat() }) {
                Icon(
                    imageVector = when (uiState.repeatMode) { Player.REPEAT_MODE_ONE -> WitcherIcons.RepeatOne else -> WitcherIcons.Repeat },
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
    queue: List<AudioTrack>, currentTrackId: String?, isPlaying: Boolean, shuffleModeEnabled: Boolean,
    repeatMode: Int, dynamicTextColor: Color, onTrackClick: (AudioTrack) -> Unit, onRemoveFromQueue: (AudioTrack) -> Unit,
    onMoveItem: (fromIndex: Int, toIndex: Int) -> Unit
) {
    if (queue.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("The queue is empty.\nNo shadows follow.", color = dynamicTextColor.copy(alpha = 0.4f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge)
        }
        return
    }

    var localQueue by remember(queue) { mutableStateOf(queue) }
    val listState = rememberLazyListState()

    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var dragCurrentIndex by remember { mutableIntStateOf(-1) }
    var draggedDistance by remember { mutableFloatStateOf(0f) }

    val currentIndex = remember(localQueue, currentTrackId) { localQueue.indexOfFirst { it.id == currentTrackId }.coerceAtLeast(0) }

    LaunchedEffect(currentTrackId) {
        if (currentIndex >= 0) listState.animateScrollToItem(currentIndex, scrollOffset = -200)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp).clip(CircleShape).background(dynamicTextColor.copy(alpha = 0.05f)).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(if (shuffleModeEnabled) "CHAOS SEQUENCE" else "ARCHIVE ORDER", style = MaterialTheme.typography.labelLarge, color = dynamicTextColor.copy(alpha = 0.6f), fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (shuffleModeEnabled) { Icon(WitcherIcons.Shuffle, null, tint = dynamicTextColor, modifier = Modifier.size(16.dp)); Spacer(modifier = Modifier.width(12.dp)) }
                Icon(if (repeatMode == 1) WitcherIcons.RepeatOne else WitcherIcons.Repeat, null, tint = if (repeatMode != 0) dynamicTextColor else dynamicTextColor.copy(alpha = 0.2f), modifier = Modifier.size(16.dp))
            }
        }

        LazyColumn(state = listState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(top = 8.dp, bottom = 150.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(items = localQueue, key = { index, track -> "${track.id}_$index" }) { index, track ->
                val isActive = track.id == currentTrackId
                val isDragging = dragCurrentIndex == index && dragStartIndex != -1

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem()
                        .pointerInput(localQueue) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    dragStartIndex = index
                                    dragCurrentIndex = index
                                    draggedDistance = 0f
                                },
                                onDragEnd = {
                                    if (dragStartIndex != -1 && dragCurrentIndex != -1 && dragStartIndex != dragCurrentIndex) {
                                        onMoveItem(dragStartIndex, dragCurrentIndex)
                                    }
                                    dragStartIndex = -1
                                    dragCurrentIndex = -1
                                    draggedDistance = 0f
                                },
                                onDragCancel = {
                                    localQueue = queue
                                    dragStartIndex = -1
                                    dragCurrentIndex = -1
                                    draggedDistance = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    draggedDistance += dragAmount.y
                                    val itemHeight = 72.dp.toPx()
                                    val offsetInt = (draggedDistance / itemHeight).toInt()

                                    if (offsetInt != 0) {
                                        val newIndex = (dragCurrentIndex + offsetInt).coerceIn(0, localQueue.lastIndex)
                                        if (newIndex != dragCurrentIndex) {
                                            val mutable = localQueue.toMutableList()
                                            val item = mutable.removeAt(dragCurrentIndex)
                                            mutable.add(newIndex, item)
                                            localQueue = mutable
                                            dragCurrentIndex = newIndex
                                            draggedDistance -= (offsetInt * itemHeight)
                                        }
                                    }
                                }
                            )
                        }
                        .bounceClick { onTrackClick(track) },
                    color = if (isActive) dynamicTextColor.copy(alpha = 0.12f) else if (isDragging) dynamicTextColor.copy(alpha = 0.05f) else Color.Transparent,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(48.dp)) {
                            AsyncImage(model = track.artworkUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)))
                            if (isActive && isPlaying) {
                                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.4f)), contentAlignment = Alignment.Center) {
                                    Icon(WitcherIcons.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(track.title, style = MaterialTheme.typography.titleMedium, fontWeight = if (isActive) FontWeight.Black else FontWeight.Bold, color = dynamicTextColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(track.artist.uppercase(), style = MaterialTheme.typography.labelSmall, color = dynamicTextColor.copy(alpha = 0.6f), letterSpacing = 1.sp, maxLines = 1)
                        }
                        if (!isActive) {
                            IconButton(onClick = { onRemoveFromQueue(track) }) {
                                Icon(WitcherIcons.Close, contentDescription = null, tint = dynamicTextColor.copy(0.3f), modifier = Modifier.size(18.dp))
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
    val regex = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?](.*)""")
    return lrc.lines().mapNotNull { line ->
        val match = regex.find(line)
        if (match != null) {
            val (min, sec, msStr, text) = match.destructured
            val ms = when (msStr.length) { 0 -> 0L; 1 -> msStr.toLong() * 100; 2 -> msStr.toLong() * 10; else -> msStr.toLong() }
            LyricLine(min.toLong() * 60000 + sec.toLong() * 1000 + ms, text.trim())
        } else null
    }
}

@Composable
private fun LyricsDisplay(lyrics: String?, currentPosition: Long, textColor: Color) {
    if (lyrics.isNullOrBlank()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) { Text("NO LYRICS FOUND", color = textColor.copy(0.4f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black) }
    } else {
        val parsed = remember(lyrics) { parseLrc(lyrics) }
        val listState = rememberLazyListState()
        val activeIndex = remember(currentPosition, parsed) { parsed.indexOfLast { it.timeMs <= currentPosition }.coerceAtLeast(0) }
        LaunchedEffect(activeIndex) { listState.animateScrollToItem(activeIndex) }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 200.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            itemsIndexed(items = parsed, key = { index, line -> "lyric_${line.timeMs}_$index" }) { index, line ->
                val isActive = index == activeIndex
                Text(
                    text = line.text.ifBlank { "•••" }, color = if (isActive) MaterialTheme.igniRed else textColor.copy(
                        0.3f
                    ),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black, fontSize = if (isActive) 24.sp else 20.sp),
                    textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 12.dp, horizontal = 24.dp).graphicsLayer { val s = if (isActive) 1.1f else 1f; scaleX = s; scaleY = s }
                )
            }
        }
    }
}

@SuppressLint("DefaultLocale")
private fun formatDuration(ms: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return String.format("%02d:%02d", minutes, seconds)
}
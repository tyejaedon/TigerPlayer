@file:SuppressLint("NewApi")
package com.example.tigerplayer.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Subject
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.ui.coverscreen.rememberCoverScreenWindowState
import com.example.tigerplayer.ui.library.SongOptionsSheet
import com.example.tigerplayer.ui.prism.PrismUiState
import com.example.tigerplayer.ui.prism.PrismViewModel
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.ensureVisibleOn
import com.example.tigerplayer.ui.theme.glassEffect
import com.example.tigerplayer.ui.theme.withSafeAlpha
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerScreen(
    viewModel: PlayerViewModel,
    onCollapse: () -> Unit,
    onOpenQueueScreen: () -> Unit,
    onNavigateToAlbum: (String) -> Unit,
    prismViewModel: PrismViewModel = hiltViewModel(),
) {
    val windowState = rememberCoverScreenWindowState()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentTrack = uiState.currentTrack ?: return
    val prismState by prismViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showOptionsSheet by remember { mutableStateOf(false) }
    var showTechnicalInfo by remember { mutableStateOf(false) }
    var trackForOptions by remember { mutableStateOf<AudioTrack?>(null) }

    LaunchedEffect(uiState.visualMode, uiState.mainViewState) {
        val shouldEnablePrism = uiState.mainViewState == MainViewState.ARTWORK && 
                               uiState.visualMode == PlayerVisualMode.SONIC_PRISM
        prismViewModel.setPrismEnabled(shouldEnablePrism)
    }

    val themeSurface = MaterialTheme.colorScheme.surface
    val themePrimary = MaterialTheme.colorScheme.primary
    var dominantBgColor by remember(themeSurface) { mutableStateOf(themeSurface) }
    var dynamicTextColor by remember { mutableStateOf(Color(0xFFF5F5F5)) }
    var dynamicAccentColor by remember(themePrimary) { mutableStateOf(themePrimary) }

    val backgroundArtModel = remember(currentTrack.artworkUri, uiState.artistImageUrl) {
        uiState.artistImageUrl?.takeIf { it.isNotBlank() } ?: currentTrack.artworkUri
    }

    val imageRequest = remember(backgroundArtModel, themePrimary) {
        ImageRequest.Builder(context)
            .data(backgroundArtModel)
            .crossfade(true)
            .allowHardware(false)
            .listener(onSuccess = { _, result ->
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                bitmap?.let { b ->
                    androidx.palette.graphics.Palette.from(b).generate { palette ->
                        val extractedColor = palette?.vibrantSwatch?.rgb
                            ?: palette?.mutedSwatch?.rgb
                            ?: palette?.dominantSwatch?.rgb
                            ?: themeSurface.toArgb()

                        val resolvedBackground = Color(extractedColor)
                        val resolvedText = Color(0xFFF5F5F5).ensureVisibleOn(
                            background = resolvedBackground,
                            minContrast = 4.5
                        )
                        val resolvedAccent = themePrimary.ensureVisibleOn(
                            background = resolvedBackground,
                            minContrast = 3.2
                        )

                        dominantBgColor = resolvedBackground
                        dynamicTextColor = resolvedText
                        dynamicAccentColor = resolvedAccent
                        viewModel.updateTrackColor(resolvedAccent)
                    }
                }
            })
            .build()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // --- 1. CINEMATIC BACKGROUND (Banding Correction) ---
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(0.35f)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.5f to Color.Black.copy(0.4f),
                        0.8f to Color.Black.copy(0.85f),
                        1.0f to Color.Black
                    )
                )
        )

        // --- 2. FOREGROUND CORE CONTENT ---
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            HeaderRitual(
                dynamicTextColor = dynamicTextColor,
                backgroundColor = dominantBgColor,
                accentColor = dynamicAccentColor,
                onCollapse = onCollapse,
                mainViewState = uiState.mainViewState,
                onSetMainViewState = viewModel::setMainViewState,
                onOpenQueueScreen = onOpenQueueScreen,
                onShowOptions = {
                    trackForOptions = currentTrack
                    showOptionsSheet = true
                },
                track = currentTrack
            )

            if (windowState.hasSeparatingHinge) {
                // FLEX MODE: Visuals on TOP, Controls on BOTTOM
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    PlayerMainContent(
                        uiState = uiState,
                        currentTrack = currentTrack,
                        viewModel = viewModel,
                        prismState = prismState,
                        prismViewModel = prismViewModel,
                        dynamicTextColor = dynamicTextColor,
                        dynamicAccentColor = dynamicAccentColor,
                        dominantBgColor = dominantBgColor
                    )
                }

                // The Hinge Spacer (Prevents content from being cut)
                Spacer(modifier = Modifier.height(32.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    PlayerControlsContent(
                        currentTrack = currentTrack,
                        dynamicTextColor = dynamicTextColor,
                        uiState = uiState,
                        viewModel = viewModel,
                        showTechnicalInfo = showTechnicalInfo,
                        onShowTechnicalInfoChange = { showTechnicalInfo = it }
                    )
                }
            } else {
                // STANDARD MODE: Unified vertical flow
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    PlayerMainContent(
                        uiState = uiState,
                        currentTrack = currentTrack,
                        viewModel = viewModel,
                        prismState = prismState,
                        prismViewModel = prismViewModel,
                        dynamicTextColor = dynamicTextColor,
                        dynamicAccentColor = dynamicAccentColor,
                        dominantBgColor = dominantBgColor
                    )
                }

                // --- DOCK GLASS ---
                PlayerControlsContent(
                    currentTrack = currentTrack,
                    dynamicTextColor = dynamicTextColor,
                    uiState = uiState,
                    viewModel = viewModel,
                    showTechnicalInfo = showTechnicalInfo,
                    onShowTechnicalInfoChange = { showTechnicalInfo = it }
                )
            }
        }

        // Options Sheet
        if (showOptionsSheet) {
            trackForOptions?.let { selectedTrack ->
                SongOptionsSheet(
                    track = selectedTrack,
                    playlists = uiState.customPlaylists,
                    onDismiss = {
                        trackForOptions = null
                        showOptionsSheet = false
                    },
                    onPlayNext = {
                        viewModel.addNextToQueue(selectedTrack)
                    },
                    onAddToPlaylist = { playlistId ->
                        viewModel.addTrackToPlaylist(playlistId, selectedTrack)
                    },
                    onGoToAlbum = { albumName ->
                        onNavigateToAlbum(albumName)
                    }
                )
            }
        }
    }
}

@Composable
private fun PlayerMainContent(
    uiState: PlayerUiState,
    currentTrack: AudioTrack,
    viewModel: PlayerViewModel,
    prismState: PrismUiState,
    prismViewModel: PrismViewModel,
    dynamicTextColor: Color,
    dynamicAccentColor: Color,
    dominantBgColor: Color
) {
    AnimatedContent(
        targetState = uiState.mainViewState,
        transitionSpec = {
            (fadeIn(animationSpec = tween(400)) + scaleIn(initialScale = 0.95f)).togetherWith(
                fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 1.05f)
            )
        },
        label = "MainViewSwitch"
    ) { state ->
        when (state) {
            MainViewState.QUEUE -> {
                QueueDisplay(
                    queue = uiState.queue,
                    currentTrackId = currentTrack.id,
                    currentQueueIndex = uiState.currentQueueIndex,
                    isPlaying = uiState.isPlaying,
                    shuffleModeEnabled = uiState.isShuffleEnabled,
                    repeatMode = uiState.repeatMode,
                    dynamicTextColor = dynamicTextColor,
                    accentColor = dynamicAccentColor,
                    onTrackClick = { viewModel.playQueueItem(it) },
                    onRemoveFromQueue = { viewModel.removeFromQueue(it) },
                    onMoveItem = { from, to -> viewModel.moveQueueItem(from, to) }
                )
            }
            MainViewState.YOUTUBE_VIEWPORT -> {
                com.example.tigerplayer.ui.youtube.YouTubeSearchScreen(
                    isEmbedded = true,
                    onBackClick = { viewModel.setMainViewState(MainViewState.ARTWORK) }
                )
            }
            MainViewState.LYRICS -> {
                LyricsDisplay(
                    lyrics = uiState.currentLyrics,
                    currentPosition = uiState.currentPosition,
                    textColor = dynamicTextColor,
                    activeColor = dynamicAccentColor
                )
            }
            MainViewState.ARTWORK -> {
                // --- 3D PARALLAX ART ---
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val density = LocalDensity.current.density
                    val context = LocalContext.current

                    var sensorTiltX by remember { mutableFloatStateOf(0f) }
                    var sensorTiltY by remember { mutableFloatStateOf(0f) }
                    var touchTiltX by remember { mutableFloatStateOf(0f) }
                    var touchTiltY by remember { mutableFloatStateOf(0f) }

                    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
                    DisposableEffect(Unit) {
                        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                        val listener = object : SensorEventListener {
                            override fun onSensorChanged(event: SensorEvent?) {
                                if (event != null) {
                                    sensorTiltY = (event.values[0] * 1.5f).coerceIn(-12f, 12f)
                                    sensorTiltX = (event.values[1] * 1.5f).coerceIn(-12f, 12f)
                                }
                            }
                            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
                        }
                        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
                        onDispose { sensorManager.unregisterListener(listener) }
                    }

                    val animatedTiltX by animateFloatAsState(targetValue = sensorTiltX + touchTiltX, label = "TiltX")
                    val animatedTiltY by animateFloatAsState(targetValue = sensorTiltY + touchTiltY, label = "TiltY")

                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .aspectRatio(1f)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragEnd = { touchTiltX = 0f; touchTiltY = 0f },
                                    onDragCancel = { touchTiltX = 0f; touchTiltY = 0f }
                                ) { change, _ ->
                                    val centerX = size.width / 2f
                                    val centerY = size.height / 2f
                                    touchTiltX = ((change.position.y - centerY) / centerY) * -15f
                                    touchTiltY = ((change.position.x - centerX) / centerX) * 15f
                                }
                            }
                            .graphicsLayer {
                                rotationX = animatedTiltX
                                rotationY = animatedTiltY
                                cameraDistance = 16f * density
                            }
                            .shadow(48.dp, RoundedCornerShape(32.dp), spotColor = dominantBgColor)
                            .clip(RoundedCornerShape(32.dp))
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { viewModel.toggleVisualMode() })
                            }
                    ) {
                        // Base Art
                        AsyncImage(
                            model = currentTrack.artworkUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Overlays
                        AnimatedVisibility(visible = uiState.visualMode == PlayerVisualMode.VORTEX) {
                            FluidVortexRenderer(
                                isPlaying = uiState.isPlaying,
                                amplitudes = uiState.currentWaveform,
                                audioReactive = uiState.audioReactiveFrame,
                                trackId = currentTrack.id,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(32.dp))
                            )
                        }

                        AnimatedVisibility(visible = uiState.visualMode == PlayerVisualMode.WAVEFORM) {
                            Box(
                                modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                SmoothWaveform(
                                    amplitudes = uiState.currentWaveform,
                                    progress = (uiState.currentPosition.toFloat() / currentTrack.durationMs.coerceAtLeast(1L)),
                                    isPlaying = uiState.isPlaying,
                                    audioReactive = uiState.audioReactiveFrame,
                                    color = dynamicTextColor
                                )
                            }
                        }

                        AnimatedVisibility(visible = uiState.visualMode == PlayerVisualMode.SONIC_PRISM) {
                            PrismInlineMixer(
                                state = prismState,
                                onVocalsChange = prismViewModel::updateVocals,
                                onBeatsChange = prismViewModel::updateBeats,
                                onInstrumentsChange = prismViewModel::updateInstruments,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.78f))
                                    .padding(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerControlsContent(
    currentTrack: AudioTrack,
    dynamicTextColor: Color,
    uiState: PlayerUiState,
    viewModel: PlayerViewModel,
    showTechnicalInfo: Boolean,
    onShowTechnicalInfoChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
            .clip(RoundedCornerShape(36.dp))
            .glassEffect(RoundedCornerShape(36.dp))
            .background(Color.White.copy(0.05f))
            .padding(24.dp)
    ) {
        TrackInfoCard(
            currentTrack,
            dynamicTextColor,
            dynamicTextColor.copy(0.7f),
            showTechnicalInfo,
            onShowTechnicalInfoChange,
            { viewModel.toggleTrackLikeStatus(currentTrack) }
        )
        Spacer(modifier = Modifier.height(20.dp))
        FieryWavySeeker(uiState, currentTrack, viewModel::seekTo, dynamicTextColor)
        Spacer(modifier = Modifier.height(16.dp))
        PlaybackControls(
            uiState = uiState,
            onShuffleToggle = viewModel::toggleShuffle,
            onPrevious = viewModel::skipToPrevious,
            onPlayPauseToggle = viewModel::togglePlayPause,
            onNext = viewModel::skipToNext,
            onRepeatToggle = viewModel::toggleRepeat,
            textColor = dynamicTextColor
        )
    }
}

@Composable
fun HeaderRitual(
    dynamicTextColor: Color,
    backgroundColor: Color,
    accentColor: Color,
    onCollapse: () -> Unit,
    mainViewState: MainViewState,
    onSetMainViewState: (MainViewState) -> Unit,
    onOpenQueueScreen: () -> Unit,
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
            IconButton(
                onClick = onCollapse,
                modifier = Modifier
                    .bounceClick { onCollapse() }
                    .background(Color.White.copy(alpha = 0.1f), CircleShape)
                    .border(0.5.dp, Color.White.copy(alpha = 0.2f), CircleShape)
            ) {
                Icon(WitcherIcons.Collapse, "Collapse", tint = dynamicTextColor)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderButton(
                    icon = WitcherIcons.Cloud,
                    active = mainViewState == MainViewState.YOUTUBE_VIEWPORT,
                    onClick = { onSetMainViewState(if (mainViewState == MainViewState.YOUTUBE_VIEWPORT) MainViewState.ARTWORK else MainViewState.YOUTUBE_VIEWPORT) },
                    color = dynamicTextColor,
                    backgroundColor = backgroundColor,
                    accentColor = accentColor
                )
                HeaderButton(
                    icon = Icons.AutoMirrored.Rounded.Subject,
                    active = mainViewState == MainViewState.LYRICS,
                    onClick = { onSetMainViewState(if (mainViewState == MainViewState.LYRICS) MainViewState.ARTWORK else MainViewState.LYRICS) },
                    color = dynamicTextColor,
                    backgroundColor = backgroundColor,
                    accentColor = accentColor
                )
                HeaderButton(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    active = mainViewState == MainViewState.QUEUE,
                    onClick = { onSetMainViewState(if (mainViewState == MainViewState.QUEUE) MainViewState.ARTWORK else MainViewState.QUEUE) },
                    onLongClick = onOpenQueueScreen,
                    color = dynamicTextColor,
                    backgroundColor = backgroundColor,
                    accentColor = accentColor
                )
                IconButton(onClick = onShowOptions) {
                    Icon(
                        WitcherIcons.Options,
                        null,
                        tint = dynamicTextColor
                            .ensureVisibleOn(backgroundColor, minContrast = 4.0)
                            .withSafeAlpha(0.88f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        var showAlbumTitle by remember { mutableStateOf(false) }
        LaunchedEffect(track.id) {
            showAlbumTitle = false
            while (isActive) {
                delay(5.seconds)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeaderButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    color: Color,
    backgroundColor: Color,
    accentColor: Color
) {
    val inactiveColor = color.ensureVisibleOn(backgroundColor, minContrast = 4.0).withSafeAlpha(0.88f)
    val activeColor = accentColor.ensureVisibleOn(backgroundColor, minContrast = 3.2)

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .background(if (active) activeColor.withSafeAlpha(0.24f) else Color.Transparent, CircleShape)
    ) {
        Icon(icon, null, tint = if (active) activeColor else inactiveColor)
    }
}

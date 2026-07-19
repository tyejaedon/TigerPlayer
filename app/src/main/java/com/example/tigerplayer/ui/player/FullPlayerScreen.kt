@file:SuppressLint("NewApi")
package com.example.tigerplayer.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.ui.coverscreen.rememberCoverScreenWindowState
import com.example.tigerplayer.ui.library.SongOptionsSheet
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.ensureVisibleOn
import com.example.tigerplayer.ui.theme.glassEffect
import com.example.tigerplayer.ui.theme.withSafeAlpha
import com.example.tigerplayer.utils.AttributionTags
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.seconds
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerScreen(
    viewModel: PlayerViewModel,
    onCollapse: () -> Unit,
    onOpenQueueScreen: () -> Unit,
    onNavigateToAlbum: (String) -> Unit,
) {
    val windowState = rememberCoverScreenWindowState()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentTrack = uiState.currentTrack ?: return
    val context = LocalContext.current

    var showOptionsSheet by remember { mutableStateOf(false) }
    var showTechnicalInfo by remember { mutableStateOf(false) }
    var trackForOptions by remember { mutableStateOf<AudioTrack?>(null) }
    var useUnifiedFlexLyrics by rememberSaveable { mutableStateOf(true) }
    var albumClarityMode by rememberSaveable { mutableStateOf(false) }

    val themeSurface = MaterialTheme.colorScheme.surface
    val themePrimary = MaterialTheme.colorScheme.primary
    val themeOnSurface = MaterialTheme.colorScheme.onSurface
    val isLightTheme = MaterialTheme.colorScheme.background.luminance() > 0.5f
    var dominantBgColor by remember(themeSurface) { mutableStateOf(themeSurface) }
    var dynamicTextColor by remember(themeSurface, themeOnSurface) {
        mutableStateOf(themeOnSurface.ensureVisibleOn(background = themeSurface, minContrast = 4.5))
    }

    var dynamicAccentColor by remember(themePrimary) { mutableStateOf(themePrimary) }
    val readableTextColor = remember(dynamicTextColor, dominantBgColor) {
        dynamicTextColor.ensureVisibleOn(background = dominantBgColor, minContrast = 7.0)
    }
    val readableSecondaryTextColor = remember(readableTextColor, dominantBgColor) {
        readableTextColor
            .withSafeAlpha(0.9f)
            .ensureVisibleOn(background = dominantBgColor, minContrast = 4.8)
    }
    val readableAccentColor = remember(dynamicAccentColor, dominantBgColor) {
        dynamicAccentColor.ensureVisibleOn(background = dominantBgColor, minContrast = 4.5)
    }

    val backgroundArtModel = remember(currentTrack.artworkUri, uiState.artistImageUrl) {
        uiState.artistImageUrl?.takeIf { it.isNotBlank() } ?: currentTrack.artworkUri
    }
    val imageRequest = remember(backgroundArtModel, themePrimary, themeOnSurface) {
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
                        val resolvedText = themeOnSurface.ensureVisibleOn(
                            background = resolvedBackground,
                            minContrast = 7.0
                        )
                        val resolvedAccent = themePrimary.ensureVisibleOn(
                            background = resolvedBackground,
                            minContrast = 4.5
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
    val shouldUseUnifiedLyricsLayout =
        windowState.hasSeparatingHinge && uiState.mainViewState == MainViewState.LYRICS && useUnifiedFlexLyrics

    Box(modifier = Modifier.fillMaxSize()) {
        // --- 1. CINEMATIC BACKGROUND (Banding Correction) ---
       CinematicZoomImage(model = imageRequest)

        // --- BACKGROUND SCRIM (Visual Anchor) ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            if (albumClarityMode) {
                                if (isLightTheme) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.22f)
                            } else {
                                if (isLightTheme) Color.White.copy(alpha = 0.30f) else Color.Black.copy(alpha = 0.45f)
                            },
                            if (albumClarityMode) {
                                if (isLightTheme) Color.Black.copy(alpha = 0.22f) else Color.Black.copy(alpha = 0.42f)
                            } else {
                                if (isLightTheme) Color.Black.copy(alpha = 0.40f) else Color.Black.copy(alpha = 0.85f)
                            }
                        )
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
                dynamicTextColor = Color.White,
                dynamicSecondaryTextColor = readableSecondaryTextColor,
                backgroundColor = dominantBgColor,
                accentColor = readableAccentColor,
                onCollapse = onCollapse,
                mainViewState = uiState.mainViewState,
                onSetMainViewState = viewModel::setMainViewState,
                onOpenQueueScreen = onOpenQueueScreen,
                onShowOptions = {
                    trackForOptions = currentTrack
                    showOptionsSheet = true
                },
                isFlexMode = windowState.hasSeparatingHinge,
                isFlexLyricsUnified = useUnifiedFlexLyrics,
                onToggleFlexLyricsUnified = { useUnifiedFlexLyrics = !useUnifiedFlexLyrics },
                isAlbumClarityMode = albumClarityMode,
                onToggleAlbumClarityMode = { albumClarityMode = !albumClarityMode },
                track = currentTrack
            )

            if (windowState.hasSeparatingHinge && !shouldUseUnifiedLyricsLayout) {
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
                        dynamicTextColor = Color.White,
                        dynamicAccentColor = readableAccentColor,
                        dominantBgColor = dominantBgColor,
                        isLightTheme = isLightTheme,
                        isAlbumClarityMode = albumClarityMode
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
                        dynamicTextColor = Color.White,
                        secondaryTextColor = readableSecondaryTextColor,
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
                        dynamicTextColor = Color.White,
                        dynamicAccentColor = readableAccentColor,
                        dominantBgColor = dominantBgColor,
                        isLightTheme = isLightTheme,
                        isAlbumClarityMode = albumClarityMode
                    )
                }

                // --- DOCK GLASS ---
                PlayerControlsContent(
                    currentTrack = currentTrack,
                    dynamicTextColor = Color.White,
                    secondaryTextColor = readableSecondaryTextColor,
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
                    iconTintOverride = Color.White,
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
fun CinematicZoomImage(model: Any) {
    val transition = rememberInfiniteTransition(label = "CinematicZoom")

    // Slowly loop between 1.0 and 1.1 every 10 seconds
    val scale by transition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Scale"
    )

    AsyncImage(
        model = model,
        contentDescription = null,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(scaleX = scale, scaleY = scale),
        contentScale = ContentScale.Crop
    )
}
@Composable
private fun PlayerMainContent(
    uiState: PlayerUiState,
    currentTrack: AudioTrack,
    viewModel: PlayerViewModel,
    dynamicTextColor: Color,
    dynamicAccentColor: Color,
    dominantBgColor: Color,
    isLightTheme: Boolean,
    isAlbumClarityMode: Boolean
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
                    val sensorContext = remember(context) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            context.createAttributionContext(AttributionTags.PLAYER_MOTION_SENSOR)
                        } else {
                            context
                        }
                    }


                    var sensorTiltX by remember { mutableFloatStateOf(0f) }
                    var sensorTiltY by remember { mutableFloatStateOf(0f) }
                    var touchTiltX by remember { mutableFloatStateOf(0f) }
                    var touchTiltY by remember { mutableFloatStateOf(0f) }


                    val sensorManager = sensorContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner) {
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

                        val observer = LifecycleEventObserver { _, event ->
                            when (event) {
                                Lifecycle.Event.ON_RESUME -> {
                                    sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
                                }
                                Lifecycle.Event.ON_PAUSE -> {
                                    sensorManager.unregisterListener(listener)
                                    // Reset tilt to flat when backgrounded so it looks right upon return
                                    sensorTiltX = 0f
                                    sensorTiltY = 0f
                                }
                                else -> {}
                            }
                        }

                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                            sensorManager.unregisterListener(listener)
                        }
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
                            .shadow(
                                elevation = if (isLightTheme) 24.dp else 48.dp,
                                shape = RoundedCornerShape(32.dp),
                                spotColor = if (isLightTheme) Color.Black.copy(alpha = 0.22f) else dominantBgColor
                            )
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
                        AnimatedVisibility(visible = !isAlbumClarityMode && uiState.visualMode == PlayerVisualMode.VORTEX) {
                            FluidVortexRenderer(
                                isPlaying = uiState.isPlaying,
                                amplitudes = uiState.currentWaveform,
                                audioReactive = uiState.audioReactiveFrame,
                                trackId = currentTrack.id,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(32.dp))
                            )
                        }

                        AnimatedVisibility(visible = !isAlbumClarityMode && uiState.visualMode == PlayerVisualMode.WAVEFORM) {
                            Box(
                                modifier = Modifier.fillMaxSize().background(if (isLightTheme) Color.Black.copy(0.42f) else Color.Black.copy(0.6f)),
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

                      /*  AnimatedVisibility(visible = !isAlbumClarityMode && uiState.visualMode == PlayerVisualMode.SONIC_PRISM) {
                            com.example.tigerplayer.ui.prism.PrismInlineMixer(
                                state = prismState,
                                onVocalsChange = prismViewModel::updateVocals,
                                onBeatsChange = prismViewModel::updateBeats,
                                onInstrumentsChange = prismViewModel::updateInstruments,
                                onEnabledChange = prismViewModel::setPrismEnabled,
                                onPresetSelected = prismViewModel::applyPreset,
                                onResetRequested = prismViewModel::resetMixToBalanced,
                                onSpectralAnalysisChange = prismViewModel::setSpectralAnalysis,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(if (isLightTheme) Color.Black.copy(alpha = 0.68f) else Color.Black.copy(alpha = 0.78f))
                                    .padding(18.dp)
                            )
                        }

                       */
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
    secondaryTextColor: Color,
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
            .padding(vertical = 24.dp)
    ) {
        Box(modifier = Modifier.padding(horizontal = 24.dp)) {
            TrackInfoCard(
                track = currentTrack,
                textColor = dynamicTextColor,
                secondaryTextColor = secondaryTextColor,
                showTechnicalInfo = showTechnicalInfo,
                bluetoothDevice = uiState.connectedBluetoothDevice,
                onToggleTechInfo = onShowTechnicalInfoChange,
                onToggleLike = { viewModel.toggleTrackLikeStatus(currentTrack) }
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Box(modifier = Modifier.padding(horizontal = 24.dp)) {
            FieryWavySeeker(uiState, currentTrack, viewModel::seekTo, dynamicTextColor)
        }
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
    dynamicSecondaryTextColor: Color,
    backgroundColor: Color,
    accentColor: Color,
    onCollapse: () -> Unit,
    mainViewState: MainViewState,
    onSetMainViewState: (MainViewState) -> Unit,
    onOpenQueueScreen: () -> Unit,
    onShowOptions: () -> Unit,
    isFlexMode: Boolean,
    isFlexLyricsUnified: Boolean,
    onToggleFlexLyricsUnified: () -> Unit,
    isAlbumClarityMode: Boolean,
    onToggleAlbumClarityMode: () -> Unit,
    track: AudioTrack
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val buttonBg = if (backgroundColor.luminance() > 0.5f) {
            Color.Black.copy(alpha = 0.18f)
        } else {
            Color.White.copy(alpha = 0.14f)
        }
        val buttonBorder = if (backgroundColor.luminance() > 0.5f) {
            Color.Black.copy(alpha = 0.26f)
        } else {
            Color.White.copy(alpha = 0.24f)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onCollapse,
                modifier = Modifier
                    .bounceClick { onCollapse() }
                    .background(buttonBg, CircleShape)
                    .border(0.75.dp, buttonBorder, CircleShape)
            ) {
                Icon(WitcherIcons.Collapse, "Collapse", tint = Color.White)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderButton(
                    icon = WitcherIcons.Cloud,
                    active = mainViewState == MainViewState.YOUTUBE_VIEWPORT,
                    onClick = { onSetMainViewState(if (mainViewState == MainViewState.YOUTUBE_VIEWPORT) MainViewState.ARTWORK else MainViewState.YOUTUBE_VIEWPORT) },
                    contentDescription = "Toggle cloud view",
                    testTag = "header_cloud_button"
                )
                HeaderButton(
                    icon = Icons.AutoMirrored.Rounded.Subject,
                    active = mainViewState == MainViewState.LYRICS,
                    onClick = { onSetMainViewState(if (mainViewState == MainViewState.LYRICS) MainViewState.ARTWORK else MainViewState.LYRICS) },
                    contentDescription = "Toggle lyrics view",
                    testTag = "header_lyrics_button"
                )
                HeaderButton(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    active = mainViewState == MainViewState.QUEUE,
                    onClick = { onSetMainViewState(if (mainViewState == MainViewState.QUEUE) MainViewState.ARTWORK else MainViewState.QUEUE) },
                    onLongClick = onOpenQueueScreen,
                    contentDescription = "Open queue view",
                    testTag = "header_queue_button"
                )
                IconButton(onClick = onShowOptions) {
                    Icon(WitcherIcons.Options, null, tint = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        val lifecycleOwner = LocalLifecycleOwner.current
        var showAlbumTitle by remember { mutableStateOf(false) }
        LaunchedEffect(track.id, lifecycleOwner) {
            showAlbumTitle = false

            if (track.album.isNotBlank() && !track.album.contains("Unknown", ignoreCase = true)) {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    while (isActive) {
                        delay(5000.milliseconds) // 5.seconds
                        showAlbumTitle = !showAlbumTitle
                    }
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
                color = Color.White, fontWeight = FontWeight.Black,
                letterSpacing = 2.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isFlexMode) {
                FilterChip(
                    selected = isFlexLyricsUnified,
                    onClick = onToggleFlexLyricsUnified,
                    label = { Text("Flex Lyrics Full") }
                )
            }
            FilterChip(
                selected = isAlbumClarityMode,
                onClick = onToggleAlbumClarityMode,
                label = { Text("Clear Album") }
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
    contentDescription: String,
    testTag: String? = null
) {
    val iconTint = Color.White

    // FIX: Replaced IconButton with Box to prevent conflicting clickable modifiers
    Box(
        modifier = Modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .clip(CircleShape)
            .background(if (active) Color.White.withSafeAlpha(0.24f) else Color.Transparent)
            .semantics { this.contentDescription = contentDescription }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = iconTint)
    }
}

package com.tigerplayer.ui.coverscreen.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.tigerplayer.constellation.NodeType
import com.tigerplayer.constellation.PositionedNode
import com.tigerplayer.ui.constellation.ConstellationArtistReading
import com.tigerplayer.ui.theme.TigerCyberCyan
import com.tigerplayer.ui.theme.TigerNeonOrange
import com.tigerplayer.ui.theme.bounceClick
import com.tigerplayer.ui.theme.glassEffect
import kotlin.math.cos
import kotlin.math.sin

/**
 * Cover Screen Atomic Orbit Artist Explorer.
 * Renders the selected artist as an atomic nucleus at the origin, with albums
 * revolving on circular electron paths and tracks surrounding the core like
 * an outer asteroid belt.
 */
@Composable
fun CoverScreenAtomicArtistView(
    artistReading: ConstellationArtistReading,
    artistNodes: List<PositionedNode>,
    onTrackSelected: (PositionedNode) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedAlbumId by remember { mutableStateOf<String?>(null) }
    var selectedTrackNode by remember { mutableStateOf<PositionedNode?>(null) }

    // Separate node types for nucleus, electron shells (albums), and asteroid belt (tracks)
    val artistCore = remember(artistNodes) { artistNodes.find { it.type == NodeType.ARTIST } }
    val albumNodes = remember(artistNodes) { artistNodes.filter { it.type == NodeType.ALBUM } }
    val trackNodes = remember(artistNodes) { artistNodes.filter { it.type == NodeType.TRACK } }

    // Continuous 60 FPS atomic orbit motion clock
    val infiniteTransition = rememberInfiniteTransition(label = "AtomicOrbitClock")
    val timeClock by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(100000, easing = LinearEasing)),
        label = "AtomicOrbitalTime"
    )

    val corePulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
        label = "CorePulseScale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF04060A))
    ) {
        // --- 1. ATOMIC ORBIT CANVAS LAYER ---
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            val center = Offset(constraints.maxWidth / 2f, constraints.maxHeight / 2f)
            val minDim = minOf(constraints.maxWidth, constraints.maxHeight)
            val nucleusRadiusPx = minDim * 0.16f
            val electronRadiusPx = minDim * 0.28f
            val asteroidRadiusPx = minDim * 0.42f

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(albumNodes, trackNodes, timeClock) {
                        detectTapGestures(
                            onTap = { tapOffset ->
                                // Hit test album electron nodes
                                val tappedAlbum = albumNodes.find { album ->
                                    val idx = albumNodes.indexOf(album)
                                    val angleStep = (2 * Math.PI) / albumNodes.size.coerceAtLeast(1)
                                    val angle = (idx * angleStep + album.phaseOffset + timeClock * 0.012f).toFloat()
                                    val albumPos = Offset(
                                        center.x + cos(angle) * electronRadiusPx,
                                        center.y + sin(angle) * electronRadiusPx
                                    )
                                    (tapOffset - albumPos).getDistance() <= 32f
                                }

                                if (tappedAlbum != null) {
                                    selectedAlbumId = if (selectedAlbumId == tappedAlbum.id) null else tappedAlbum.id
                                    selectedTrackNode = null
                                    return@detectTapGestures
                                }

                                // Hit test track asteroid belt particles
                                val tappedTrack = trackNodes.find { track ->
                                    val idx = trackNodes.indexOf(track)
                                    val angleStep = (2 * Math.PI) / trackNodes.size.coerceAtLeast(1)
                                    val angle = (idx * angleStep + track.phaseOffset + timeClock * 0.02f).toFloat()
                                    val trackPos = Offset(
                                        center.x + cos(angle) * asteroidRadiusPx,
                                        center.y + sin(angle) * asteroidRadiusPx
                                    )
                                    (tapOffset - trackPos).getDistance() <= 24f
                                }

                                if (tappedTrack != null) {
                                    selectedTrackNode = tappedTrack
                                    onTrackSelected(tappedTrack)
                                } else {
                                    selectedAlbumId = null
                                    selectedTrackNode = null
                                }
                            }
                        )
                    }
            ) {
                // Orbital Path Lines & Particles
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Electron Shell Orbit Path (Albums)
                    drawCircle(
                        color = TigerCyberCyan.copy(alpha = 0.15f),
                        radius = electronRadiusPx,
                        center = center,
                        style = Stroke(
                            width = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 16f))
                        )
                    )

                    // Asteroid Belt Orbit Path (Tracks)
                    drawCircle(
                        color = TigerNeonOrange.copy(alpha = 0.12f),
                        radius = asteroidRadiusPx,
                        center = center,
                        style = Stroke(
                            width = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 12f))
                        )
                    )

                    // Connecting Tethers from Nucleus to Electron Albums
                    albumNodes.forEachIndexed { idx, album ->
                        val angleStep = (2 * Math.PI) / albumNodes.size.coerceAtLeast(1)
                        val angle = (idx * angleStep + album.phaseOffset + timeClock * 0.012f).toFloat()
                        val albumPos = Offset(
                            center.x + cos(angle) * electronRadiusPx,
                            center.y + sin(angle) * electronRadiusPx
                        )

                        val isHighlighted = selectedAlbumId == null || selectedAlbumId == album.id
                        val lineAlpha = if (isHighlighted) 0.35f else 0.08f

                        drawLine(
                            brush = Brush.linearGradient(
                                listOf(TigerCyberCyan.copy(alpha = lineAlpha), album.color.copy(alpha = lineAlpha))
                            ),
                            start = center,
                            end = albumPos,
                            strokeWidth = if (isHighlighted) 2f else 1f
                        )
                    }

                    // Render Asteroid Belt Particles (Tracks)
                    trackNodes.forEachIndexed { idx, track ->
                        val angleStep = (2 * Math.PI) / trackNodes.size.coerceAtLeast(1)
                        val angle = (idx * angleStep + track.phaseOffset + timeClock * 0.02f).toFloat()
                        val trackPos = Offset(
                            center.x + cos(angle) * asteroidRadiusPx,
                            center.y + sin(angle) * asteroidRadiusPx
                        )

                        val belongsToSelectedAlbum = selectedAlbumId == null || track.orbitCenterId == selectedAlbumId
                        val particleAlpha = if (belongsToSelectedAlbum) 0.85f else 0.2f
                        val particleRadius = if (selectedTrackNode?.id == track.id) 8f else 5f

                        // Outer particle glow
                        drawCircle(
                            color = TigerNeonOrange.copy(alpha = particleAlpha * 0.4f),
                            radius = particleRadius * 1.8f,
                            center = trackPos
                        )

                        // Core particle
                        drawCircle(
                            color = if (selectedTrackNode?.id == track.id) Color.White else TigerNeonOrange.copy(alpha = particleAlpha),
                            radius = particleRadius,
                            center = trackPos
                        )
                    }

                    // Render Electron Nodes (Albums)
                    albumNodes.forEachIndexed { idx, album ->
                        val angleStep = (2 * Math.PI) / albumNodes.size.coerceAtLeast(1)
                        val angle = (idx * angleStep + album.phaseOffset + timeClock * 0.012f).toFloat()
                        val albumPos = Offset(
                            center.x + cos(angle) * electronRadiusPx,
                            center.y + sin(angle) * electronRadiusPx
                        )

                        val isSelected = selectedAlbumId == album.id
                        val electronRadius = if (isSelected) 18f else 14f

                        // Electron aura
                        drawCircle(
                            color = album.color.copy(alpha = if (isSelected) 0.5f else 0.2f),
                            radius = electronRadius * 1.6f,
                            center = albumPos
                        )

                        // Electron solid body
                        drawCircle(
                            color = if (isSelected) Color.White else album.color,
                            radius = electronRadius,
                            center = albumPos
                        )

                        // Electron rim
                        drawCircle(
                            color = Color.White.copy(alpha = 0.8f),
                            radius = electronRadius,
                            center = albumPos,
                            style = Stroke(width = if (isSelected) 2.5f else 1.2f)
                        )
                    }
                }

                // --- 2. NUCLEUS (Artist Core Avatar) ---
                Box(
                    modifier = Modifier
                        .size((nucleusRadiusPx * 2).dp)
                        .graphicsLayer {
                            translationX = center.x - nucleusRadiusPx
                            translationY = center.y - nucleusRadiusPx
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Outer Corona Glow
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(1.15f)
                            .graphicsLayer { scaleX = corePulse; scaleY = corePulse }
                            .background(
                                Brush.radialGradient(
                                    listOf(TigerCyberCyan.copy(alpha = 0.35f), Color.Transparent)
                                ),
                                CircleShape
                            )
                    )

                    // Artist Core Avatar
                    val artistImage = artistReading.imageUrl ?: artistCore?.imageUrl
                    if (!artistImage.isNullOrBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(artistImage)
                                .crossfade(true)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .build(),
                            contentDescription = artistReading.artistName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .shadow(24.dp, CircleShape, spotColor = TigerCyberCyan)
                                .clip(CircleShape)
                                .border(2.dp, TigerCyberCyan, CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .shadow(24.dp, CircleShape, spotColor = TigerCyberCyan)
                                .clip(CircleShape)
                                .background(Brush.radialGradient(listOf(TigerCyberCyan.copy(alpha = 0.8f), Color(0xFF0D1117))))
                                .border(2.dp, TigerCyberCyan, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        }

        // --- 3. TOP ARTIST HEADER BAR ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artistReading.artistName.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 1.2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "ATOMIC ORBIT • ${artistReading.playCount} PLAYS • ${albumNodes.size} ALBUMS",
                    style = MaterialTheme.typography.labelSmall,
                    color = TigerCyberCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }

        // --- 4. BOTTOM ACTION BAR ("View Constellation" Button) ---
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp, start = 20.dp, end = 20.dp)
        ) {
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(
                    containerColor = TigerNeonOrange,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(20.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .height(44.dp)
                    .bounceClick { onClose() }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "View Constellation",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

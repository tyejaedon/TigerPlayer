package com.example.tigerplayer.ui.library

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.model.Playlist
import com.example.tigerplayer.ui.player.PlayerViewModel
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect
import java.util.concurrent.TimeUnit

import androidx.compose.foundation.border
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

// --- THE VANGUARD PALETTE (High Visibility) ---
val AardBlue = Color(0xFF4FC3F7) // Brighter blue for better dark-mode contrast
val IgniRed = Color(0xFFFF5252)  // Brighter red for visibility


@Composable
fun SearchSectionTitle(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = AardBlue,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        letterSpacing = 2.sp
    )
}

@Composable
fun ScanningOverlay(progress: Int, total: Int) {
    val GlassWhite = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    val SecondaryText = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f)) // Deepened for focus
            .pointerInput(Unit) {
                // CRUCIAL: Consume all touches so the user can't click things underneath
                detectTapGestures {  }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp) // S22: Reasonable outer bounds
                .glassEffect(MaterialTheme.shapes.extraLarge)
                .background(GlassWhite, MaterialTheme.shapes.extraLarge)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), MaterialTheme.shapes.extraLarge)
                .padding(horizontal = 24.dp, vertical = 32.dp), // S22: Tightened inner padding
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Pulsing Icon Animation
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
                label = "iconAlpha"
            )

            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = AardBlue,
                modifier = Modifier
                    .size(64.dp) // Scaled down from 80.dp
                    .graphicsLayer { this.alpha = alpha }
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "INDEXING ARCHIVES",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            LinearProgressIndicator(
                progress = { if (total > 0) progress.toFloat() / total.toFloat() else 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp) // Sleeker progress bar
                    .clip(CircleShape),
                color = IgniRed,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )

            Text(
                text = "Manifesting track $progress of $total...",
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.labelMedium, // Kept small to prevent wrapping
                color = SecondaryText
            )
        }
    }
}
@Composable
fun LikedSongsCard(trackCount: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
            .padding(vertical = 8.dp)
            .clip(MaterialTheme.shapes.large)
            .background(
                Brush.linearGradient(
                    listOf(IgniRed.copy(0.7f), IgniRed.copy(0.2f), Color.Transparent)
                )
            )
            .glassEffect(MaterialTheme.shapes.large)
            .bounceClick { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "LIKED SONGS",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "$trackCount saved chants",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.8f)
                )
            }
            Icon(
                imageVector = Icons.Rounded.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}
@Composable
fun PlaylistRow(
    playlist: Playlist,
    modifier: Modifier = Modifier, // THE FIX: Essential for animateItem() to work
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(MaterialTheme.shapes.large)
            .bounceClick { onClick() }
            // 1. THE GLASS ANCHOR: A subtle inner glow for the row
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.02f))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // --- THE MEDALLION (Glass-Cut Edition) ---
        Box(
            modifier = Modifier
                .size(48.dp)
                .shadow(4.dp, MaterialTheme.shapes.medium, spotColor = AardBlue.copy(alpha = 0.2f))
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                // 2. DIAMOND-CUT BORDER: Specular highlight on the icon box
                .border(
                    width = 0.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    ),
                    shape = MaterialTheme.shapes.medium
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = WitcherIcons.Playlist,
                contentDescription = null,
                tint = AardBlue,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // --- THE METADATA ---
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black, // Bumped to Black for the Vanguard look
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text =  "${playlist.trackCount} CHANTS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        // --- NAVIGATION HINT ---
        Icon(
            imageVector = WitcherIcons.Next,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
            modifier = Modifier.size(16.dp)
        )
    }
}


@Composable
fun LibraryHeader(
    title: String,
    searchQuery: String,
    isSearchActive: Boolean,
    onSearchToggle: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
) {
    val isDark = isSystemInDarkTheme()

    // THE FIX: High-contrast placeholder that won't get lost in the glass
    val placeholderColor = if (isDark) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    } else {
        Color.Black.copy(alpha = 0.5f)
    }

    // THE AUTO-FOCUS RITUAL
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearchActive) {
        if (isSearchActive) {
            focusRequester.requestFocus()
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically // Ensures the Row children stay aligned
    ) {
        Box(modifier = Modifier.weight(1f)) {
            // --- TITLE DISPLAY ---
            this@Row.AnimatedVisibility(
                visible = !isSearchActive,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
            }

            // --- SEARCH INPUT (The Fix) ---
            this@Row.AnimatedVisibility(
                visible = isSearchActive,
                enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
                exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End)
            ) {
                // BasicTextField strips away the rigid Material padding
                androidx.compose.foundation.text.BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp) // Now perfectly respects the 48.dp height
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), CircleShape)
                        .glassEffect(CircleShape),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(AardBlue),
                    decorationBox = { innerTextField ->
                        // This Row acts as your custom search bar layout
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp), // Perfect horizontal padding
                            verticalAlignment = Alignment.CenterVertically // Centers text dead-center
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Search archives...",
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                                // This is where the actual typed text gets rendered
                                innerTextField()
                            }

                            // Trailing Icon
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { onSearchQueryChange("") },
                                    modifier = Modifier.size(24.dp) // Tighter tap target to fit 48dp
                                ) {
                                    Icon(
                                        imageVector = WitcherIcons.Close,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Search Toggle Button
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(AardBlue)
                .bounceClick { onSearchToggle() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isSearchActive) WitcherIcons.Collapse else WitcherIcons.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }
        }
    }



@Composable
fun SearchEmptyState(query: String) {
    val GlassWhite = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    val SecondaryText = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = WitcherIcons.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
            modifier = Modifier.size(100.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "NO ECHOES FOUND",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp
        )
        Text(
            text = "The archives have no record of \"$query\"",
            style = MaterialTheme.typography.bodyMedium,
            color = SecondaryText,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}


@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    // The Focus Anchor
    val focusRequester = remember { FocusRequester() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false // Allows us to control exact S22 margins
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f) // Takes up exactly 90% of the screen width
                .glassEffect(MaterialTheme.shapes.extraLarge)
                // THE FIX: Theme-aware surface color instead of hardcoded Black
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), MaterialTheme.shapes.extraLarge)
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), MaterialTheme.shapes.extraLarge)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "FORGE NEW PLAYLIST",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )

            // S22 Optimization: Dropped from 32dp to 24dp to leave room for the keyboard
            Spacer(modifier = Modifier.height(24.dp))

            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester) // Attach the focus anchor
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), CircleShape),
                placeholder = {
                    Text(
                        text = "Name your collection...",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = AardBlue
                ),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                singleLine = true,
                shape = CircleShape
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "CANCEL",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = { if (text.isNotBlank()) onCreate(text) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AardBlue,
                        disabledContainerColor = AardBlue.copy(alpha = 0.2f)
                    ),
                    enabled = text.isNotBlank(),
                    shape = CircleShape
                ) {
                    Text(
                        text = "FORGE",
                        fontWeight = FontWeight.Black,
                        // Force black text on the bright AardBlue button for maximum contrast
                        color = if (text.isNotBlank()) Color.Black else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }

    // THE AUTO-FOCUS RITUAL
    // Triggers a fraction of a second after the dialog manifests to summon the keyboard
    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
    }
}

@Composable
fun ChapterSongRow(
    index: Int,
    track: AudioTrack,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onOptionsClick: () -> Unit
) {
    val pulseAlpha = if (isCurrentTrack && isPlaying) rememberAardPulse() else 1f
    val displayIndex = (index + 1).toString().padStart(2, '0')
    val GlassWhite = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    val SecondaryText = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(40.dp), contentAlignment = Alignment.CenterStart) {
            if (isCurrentTrack && isPlaying) {
                Icon(
                    imageVector = WitcherIcons.Headphones,
                    contentDescription = null,
                    tint = AardBlue.copy(alpha = pulseAlpha),
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = displayIndex,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isCurrentTrack) AardBlue else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    fontWeight = FontWeight.Black
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrentTrack) FontWeight.Black else FontWeight.Bold,
                color = if (isCurrentTrack) AardBlue.copy(alpha = pulseAlpha) else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = SecondaryText,
                letterSpacing = 1.sp
            )
        }

        Text(
            text = formatDuration(track.durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        IconButton(onClick = onOptionsClick, modifier = Modifier.size(24.dp)) {
            Icon(WitcherIcons.Options, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(durationMs)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(durationMs) - TimeUnit.MINUTES.toSeconds(minutes)
    return String.format("%02d:%02d", minutes, seconds)
}

@Composable
 fun rememberAardPulse(): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "AardPulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "Alpha"
    )
    return alpha
}
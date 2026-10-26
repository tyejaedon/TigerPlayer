package com.tigerplayer.ui.coverscreen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.ui.player.PlayerViewModel
import com.tigerplayer.ui.player.StatItem
import com.tigerplayer.ui.theme.bounceClick
import com.tigerplayer.ui.theme.glassEffect
import com.tigerplayer.utils.ArtistUtils
import java.util.Locale

private val AardBlue = Color(0xFF4FC3F7)
private val IgniRed = Color(0xFFFF5252)

@Composable
fun CoverScreenStatsView(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    onPlayTrack: (AudioTrack) -> Unit = {},
    onArtistSelected: (String) -> Unit = {}
) {
    val statsState by viewModel.detailedStatsState.collectAsState()
    val playerUiState by viewModel.uiState.collectAsState()
    val allTracks = playerUiState.allTracks

    val filters = remember {
        listOf(
            "Today",
            "This Week",
            "Last 7 Days",
            "This Month",
            "Last 30 Days",
            "Last 90 Days",
            "This Year",
            "Lifetime"
        )
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var searchQuery by remember { mutableStateOf("") }
    var isAlphabeticalSort by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "CoverMeshGradient")
    val xOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(22000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CoverMeshX"
    )

    val processedArtists = remember(searchQuery, isAlphabeticalSort, statsState.topArtists) {
        var list = statsState.topArtists
        if (searchQuery.isNotBlank()) {
            list = list.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
        if (isAlphabeticalSort) list.sortedBy { it.name.lowercase(Locale.getDefault()) } else list.sortedByDescending { it.playCount }
    }

    val processedTracks = remember(searchQuery, isAlphabeticalSort, statsState.topTracks) {
        var list = statsState.topTracks
        if (searchQuery.isNotBlank()) {
            list = list.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                    it.secondaryText.contains(searchQuery, ignoreCase = true)
            }
        }
        if (isAlphabeticalSort) list.sortedBy { it.name.lowercase(Locale.getDefault()) } else list.sortedByDescending { it.playCount }
    }

    val isSearching = searchQuery.isNotEmpty()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Live Animated Ambient Mesh
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .blur(80.dp)
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(AardBlue.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(xOffset, size.height * 0.25f),
                    radius = 800f
                )
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(IgniRed.copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(size.width - xOffset, size.height * 0.7f),
                    radius = 700f
                )
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Temporal Axis Filters
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(filters) { filter ->
                    val isSelected = filter == statsState.selectedFilter
                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        label = "CoverFilterBg"
                    )
                    val textColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "CoverFilterText"
                    )

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(bgColor)
                            .bounceClick {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                viewModel.updateStatsFilter(filter)
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = filter.uppercase(Locale.getDefault()),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                            color = textColor,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            // Compact Search & Sort Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(AardBlue),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Search,
                        keyboardType = KeyboardType.Text
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = { keyboardController?.hide() }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), CircleShape),
                    decorationBox = { innerTextField ->
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        "Filter archives...",
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                innerTextField()
                            }
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        keyboardController?.hide()
                                        focusManager.clearFocus()
                                        searchQuery = ""
                                    },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (isAlphabeticalSort) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                        .bounceClick {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            isAlphabeticalSort = !isAlphabeticalSort
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Sort,
                        contentDescription = "Sort",
                        tint = if (isAlphabeticalSort) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Scrollable Content
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Hero Card: Total Manifestation Time
                item {
                    AnimatedVisibility(
                        visible = !isSearching,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        val animatedHours by animateIntAsState(
                            targetValue = statsState.totalListeningHours,
                            animationSpec = tween(1400, easing = FastOutSlowInEasing),
                            label = "CoverHours"
                        )
                        val animatedMins by animateIntAsState(
                            targetValue = statsState.totalListeningMinutes,
                            animationSpec = tween(1400, easing = FastOutSlowInEasing),
                            label = "CoverMins"
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(8.dp, RoundedCornerShape(20.dp), ambientColor = Color.Transparent, spotColor = AardBlue.copy(alpha = 0.2f))
                                .clip(RoundedCornerShape(20.dp))
                                .glassEffect(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
                                .padding(16.dp)
                        ) {
                            Column {
                                Text(
                                    text = "TOTAL MANIFESTATION TIME",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AardBlue,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.5.sp
                                )
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Text(
                                        text = animatedHours.toString(),
                                        style = MaterialTheme.typography.headlineLarge,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "H ",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    Text(
                                        text = animatedMins.toString(),
                                        style = MaterialTheme.typography.headlineLarge,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "M",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                }
                                Text(
                                    text = String.format(Locale.getDefault(), "%.1f%% of lifetime listening", statsState.globalListeningSharePercent),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = AardBlue.copy(alpha = 0.9f),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                        }
                    }
                }

                if (processedArtists.isEmpty() && processedTracks.isEmpty() && isSearching) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No archives match your query.",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Top Artists Section
                if (processedArtists.isNotEmpty()) {
                    item {
                        CoverDetailedSectionTitle(
                            main = "THE VANGUARD",
                            sub = if (isSearching) "Matching Artists" else "Most summoned artists"
                        )
                    }

                    if (!isSearching && !isAlphabeticalSort) {
                        item {
                            val topArtist = processedArtists.first()
                            CoverTopArtistHeroCard(
                                artist = topArtist,
                                onClick = { onArtistSelected(topArtist.name) }
                            )
                        }
                        items(processedArtists.drop(1)) { artist ->
                            val rank = processedArtists.indexOf(artist) + 1
                            CoverStatRow(
                                rank = rank,
                                item = artist,
                                onClick = { onArtistSelected(artist.name) }
                            )
                        }
                    } else {
                        items(processedArtists) { artist ->
                            val rank = processedArtists.indexOf(artist) + 1
                            CoverStatRow(
                                rank = rank,
                                item = artist,
                                onClick = { onArtistSelected(artist.name) }
                            )
                        }
                    }
                }

                // Top Tracks Section
                if (processedTracks.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        CoverDetailedSectionTitle(
                            main = "THE CHANTS",
                            sub = if (isSearching) "Matching Tracks" else "Highest frequencies recorded"
                        )
                    }

                    val maxTrackPlays = statsState.topTracks.maxOfOrNull { it.playCount } ?: 1

                    items(processedTracks) { track ->
                        val rank = processedTracks.indexOf(track) + 1
                        val matchingTrack = remember(track, allTracks) {
                            allTracks.firstOrNull { it.id == track.id }
                                ?: allTracks.firstOrNull {
                                    it.title.equals(track.name, ignoreCase = true) &&
                                        ArtistUtils.getBaseArtist(it.artist).equals(ArtistUtils.getBaseArtist(track.secondaryText), ignoreCase = true)
                                }
                                ?: allTracks.firstOrNull { it.title.equals(track.name, ignoreCase = true) }
                        }

                        CoverRelativeTrackStatRow(
                            rank = rank,
                            item = track,
                            maxPlays = maxTrackPlays,
                            isSearching = isSearching,
                            onClick = {
                                matchingTrack?.let { onPlayTrack(it) }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CoverTopArtistHeroCard(
    artist: StatItem,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .shadow(12.dp, RoundedCornerShape(20.dp), ambientColor = Color.Transparent, spotColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .bounceClick(onClick = onClick)
    ) {
        AsyncImage(
            model = artist.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.4f to Color.Black.copy(alpha = 0.35f),
                        1.0f to Color.Black.copy(alpha = 0.92f)
                    )
                )
        )

        Text(
            text = "#1",
            style = MaterialTheme.typography.displayLarge.copy(fontSize = 90.sp),
            color = Color.White.copy(alpha = 0.12f),
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 10.dp, y = (-12).dp)
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp)
        ) {
            Text(
                text = "TOP ARTIST",
                style = MaterialTheme.typography.labelSmall,
                color = IgniRed,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = artist.name,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${artist.playCount} SUMMONS",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.72f),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun CoverRelativeTrackStatRow(
    rank: Int,
    item: StatItem,
    maxPlays: Int,
    isSearching: Boolean,
    onClick: () -> Unit
) {
    val targetFraction = (item.playCount.toFloat() / maxPlays.coerceAtLeast(1).toFloat()).coerceIn(0.1f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = if (isSearching) 0f else targetFraction,
        animationSpec = tween(1200, delayMillis = (rank * 60).coerceAtMost(600), easing = FastOutSlowInEasing),
        label = "CoverBarFill"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(14.dp))
            .glassEffect(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
            .bounceClick(onClick = onClick)
    ) {
        if (!isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedFraction)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                AardBlue.copy(alpha = 0.22f),
                                AardBlue.copy(alpha = 0.06f)
                            )
                        )
                    )
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "#$rank",
                modifier = Modifier.width(30.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
                color = if (rank == 1 && !isSearching) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
            )

            AsyncImage(
                model = item.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            )

            Column(
                modifier = Modifier
                    .padding(start = 10.dp)
                    .weight(1f)
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.secondaryText.uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = item.playCount.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun CoverStatRow(
    rank: Int,
    item: StatItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "#$rank",
            modifier = Modifier.width(32.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        )

        AsyncImage(
            model = item.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        )

        Column(
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f)
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (item.secondaryText.isNotBlank()) {
                    "${item.playCount} SUMMONS • ${item.secondaryText.uppercase(Locale.getDefault())}"
                } else {
                    "${item.playCount} SUMMONS"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun CoverDetailedSectionTitle(main: String, sub: String) {
    Column(modifier = Modifier.padding(bottom = 6.dp)) {
        Text(
            text = main,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = sub.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

package com.tigerplayer.ui.coverscreen

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.ui.dashboard.CurationFeedMeta
import com.tigerplayer.ui.dashboard.DashboardViewModel
import com.tigerplayer.ui.home.HomeUiState
import com.tigerplayer.ui.player.PlayerViewModel
import com.tigerplayer.ui.theme.TigerNeonOrange
import com.tigerplayer.ui.theme.TigerSurfaceCharcoal
import com.tigerplayer.ui.theme.bounceClick

private val EmeraldNeon = Color(0xFF67F7B1)

/**
 * Cover screen adaptation of [com.tigerplayer.ui.home.HomeScreen].
 * Built incrementally to mirror the essential home dashboard for compact external displays.
 */
@Composable
fun CoverScreenHomeView(
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    dashboardViewModel: DashboardViewModel = hiltViewModel(),
    onClose: (() -> Unit)? = null,
    onOpenDaylistDetail: (() -> Unit)? = null,
    onOpenDiscoveryDetail: (() -> Unit)? = null
) {
    val homeState by playerViewModel.homeUiState.collectAsStateWithLifecycle()
    val daylistTracks by dashboardViewModel.daylistTracks.collectAsStateWithLifecycle()
    val discoveryWeeklyTracks by dashboardViewModel.discoveryWeeklyTracks.collectAsStateWithLifecycle()
    val daylistMeta by dashboardViewModel.daylistMeta.collectAsStateWithLifecycle()
    val discoveryMeta by dashboardViewModel.discoveryMeta.collectAsStateWithLifecycle()

    var internalDaylistOpen by remember { mutableStateOf(false) }
    var internalDiscoveryOpen by remember { mutableStateOf(false) }

    val handleOpenDaylist = {
        if (onOpenDaylistDetail != null) {
            onOpenDaylistDetail()
        } else {
            internalDaylistOpen = true
        }
    }

    val handleOpenDiscovery = {
        if (onOpenDiscoveryDetail != null) {
            onOpenDiscoveryDetail()
        } else {
            internalDiscoveryOpen = true
        }
    }

    CoverScreenHomeContent(
        homeState = homeState,
        daylistTracks = daylistTracks,
        discoveryWeeklyTracks = discoveryWeeklyTracks,
        daylistMeta = daylistMeta,
        discoveryMeta = discoveryMeta,
        onDaylistClick = handleOpenDaylist,
        onDiscoveryClick = handleOpenDiscovery,
        modifier = modifier,
        onClose = onClose
    )

    if (internalDaylistOpen) {
        Dialog(
            onDismissRequest = { internalDaylistOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            CoverScreenDaylistDetailView(
                tracks = daylistTracks,
                meta = daylistMeta,
                onPlayTrack = { track ->
                    playerViewModel.playTrack(track)
                    internalDaylistOpen = false
                },
                onPlayFeed = {
                    if (daylistTracks.isNotEmpty()) {
                        if (daylistTracks.first().id.startsWith("spotify:")) {
                            playerViewModel.playTrack(daylistTracks.first())
                        } else {
                            playerViewModel.setPlaylistAndPlay(daylistTracks, 0)
                        }
                        internalDaylistOpen = false
                    }
                },
                onShuffleFeed = {
                    if (daylistTracks.isNotEmpty()) {
                        val shuffled = daylistTracks.shuffled()
                        if (shuffled.first().id.startsWith("spotify:")) {
                            playerViewModel.playTrack(shuffled.first())
                        } else {
                            playerViewModel.setPlaylistAndPlay(shuffled, 0)
                        }
                        internalDaylistOpen = false
                    }
                },
                onRefresh = dashboardViewModel::refreshForYou,
                onClose = { internalDaylistOpen = false }
            )
        }
    }

    if (internalDiscoveryOpen) {
        Dialog(
            onDismissRequest = { internalDiscoveryOpen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            CoverScreenDiscoverWeeklyDetailView(
                tracks = discoveryWeeklyTracks,
                meta = discoveryMeta,
                onPlayTrack = { track ->
                    playerViewModel.playTrack(track)
                    internalDiscoveryOpen = false
                },
                onPlayFeed = {
                    if (discoveryWeeklyTracks.isNotEmpty()) {
                        if (discoveryWeeklyTracks.first().id.startsWith("spotify:")) {
                            playerViewModel.playTrack(discoveryWeeklyTracks.first())
                        } else {
                            playerViewModel.setPlaylistAndPlay(discoveryWeeklyTracks, 0)
                        }
                        internalDiscoveryOpen = false
                    }
                },
                onShuffleFeed = {
                    if (discoveryWeeklyTracks.isNotEmpty()) {
                        val shuffled = discoveryWeeklyTracks.shuffled()
                        if (shuffled.first().id.startsWith("spotify:")) {
                            playerViewModel.playTrack(shuffled.first())
                        } else {
                            playerViewModel.setPlaylistAndPlay(shuffled, 0)
                        }
                        internalDiscoveryOpen = false
                    }
                },
                onRefresh = dashboardViewModel::refreshForYou,
                onClose = { internalDiscoveryOpen = false }
            )
        }
    }
}

/**
 * Pure stateless rendering component for [CoverScreenHomeView].
 */
@Composable
fun CoverScreenHomeContent(
    homeState: HomeUiState,
    daylistTracks: List<AudioTrack>,
    discoveryWeeklyTracks: List<AudioTrack>,
    daylistMeta: CurationFeedMeta,
    discoveryMeta: CurationFeedMeta,
    onDaylistClick: () -> Unit,
    onDiscoveryClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TigerSurfaceCharcoal)
            .testTag(CoverScreenTestTags.HOME_VIEW)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Bar
            item {
                CoverScreenHomeHeader(
                    onClose = onClose,
                    modifier = Modifier.testTag(CoverScreenTestTags.HOME_HEADER)
                )
            }

            // First Row Section: Daylist and Discovery Weekly Curations
            item {
                CoverScreenCurationSection(
                    daylistTracks = daylistTracks,
                    discoveryWeeklyTracks = discoveryWeeklyTracks,
                    daylistMeta = daylistMeta,
                    discoveryMeta = discoveryMeta,
                    onDaylistClick = onDaylistClick,
                    onDiscoveryClick = onDiscoveryClick
                )
            }

            // Incremental Slot: Preview of upcoming sections (e.g. Total Tracks)
            if (homeState.statistics.totalTracksCount > 0) {
                item {
                    Text(
                        text = "LIBRARY: ${homeState.statistics.totalTracksCount} TRACKS",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 9.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CoverScreenHomeHeader(
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(TigerNeonOrange)
            )
            Text(
                text = "TIGER PLAYER",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
                color = Color.White,
                letterSpacing = 1.2.sp
            )
            Text(
                text = "• HOME",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = TigerNeonOrange,
                letterSpacing = 1.sp
            )
        }

        if (onClose != null) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(32.dp)
                    .testTag(CoverScreenTestTags.HOME_CLOSE_BUTTON)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close Home",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * First row section featuring the elegant dual curation cards (Daylist & Discovery Weekly).
 */
@Composable
fun CoverScreenCurationSection(
    daylistTracks: List<AudioTrack>,
    discoveryWeeklyTracks: List<AudioTrack>,
    daylistMeta: CurationFeedMeta,
    discoveryMeta: CurationFeedMeta,
    onDaylistClick: () -> Unit,
    onDiscoveryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dayPhase = rememberCoverDayPhase()
    val view = LocalView.current

    val daylistSubtitle = when {
        daylistMeta.isLoading -> "Refreshing feed..."
        daylistTracks.isNotEmpty() -> dayPhase.headline
        daylistMeta.lastErrorMessage != null -> "Feed offline"
        else -> dayPhase.headline
    }

    val discoverySubtitle = when {
        discoveryMeta.isLoading -> "Syncing radar..."
        discoveryWeeklyTracks.isNotEmpty() -> "${discoveryWeeklyTracks.size} algorithmic picks"
        discoveryMeta.lastErrorMessage != null -> "Feed offline"
        else -> "Fresh weekly radar"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(CoverScreenTestTags.DISCOVERY_VIEW),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "NEURAL CURATIONS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.65f),
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // DAYLIST CARD
            CoverCurationCard(
                modifier = Modifier
                    .weight(1f)
                    .testTag(CoverScreenTestTags.HOME_DAYLIST_CARD),
                accentColor = dayPhase.accent,
                backgroundColor = Color(0xFF090B14).copy(alpha = 0.90f),
                icon = Icons.Rounded.Timer,
                category = dayPhase.label,
                title = "Daylist",
                subtitle = daylistSubtitle,
                trackCount = daylistTracks.size,
                emptyLabel = "CURATE",
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onDaylistClick()
                }
            )

            // DISCOVERY WEEKLY CARD
            CoverCurationCard(
                modifier = Modifier
                    .weight(1f)
                    .testTag(CoverScreenTestTags.HOME_DISCOVERY_CARD),
                accentColor = EmeraldNeon,
                backgroundColor = Color(0xFF061210).copy(alpha = 0.90f),
                icon = Icons.Rounded.GraphicEq,
                category = "WEEKLY MIX",
                title = "Discovery",
                subtitle = discoverySubtitle,
                trackCount = discoveryWeeklyTracks.size,
                emptyLabel = "EXPLORE",
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onDiscoveryClick()
                }
            )
        }
    }
}

@Composable
private fun CoverCurationCard(
    accentColor: Color,
    backgroundColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    category: String,
    title: String,
    subtitle: String,
    trackCount: Int,
    emptyLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val description = "$title, $category, ${if (trackCount > 0) "$trackCount tracks" else emptyLabel}"

    Surface(
        modifier = modifier
            .height(108.dp)
            .bounceClick { onClick() }
            .semantics {
                role = Role.Button
                contentDescription = description
            },
        shape = RoundedCornerShape(18.dp),
        color = backgroundColor,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
        tonalElevation = 4.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.16f),
                            Color.Transparent
                        )
                    )
                )
                .padding(10.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top icon and count badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.20f))
                            .border(1.dp, accentColor.copy(alpha = 0.35f), CircleShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (trackCount > 0) "$trackCount" else emptyLabel,
                            color = accentColor,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 0.4.sp
                        )
                    }
                }

                // Text block
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = category.uppercase(),
                        color = accentColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Black,
                        fontSize = 9.sp,
                        letterSpacing = 0.8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

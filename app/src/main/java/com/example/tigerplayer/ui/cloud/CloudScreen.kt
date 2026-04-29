package com.example.tigerplayer.ui.cloud

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.tigerplayer.MainActivity
import com.example.tigerplayer.ui.theme.SpotifyGreen
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.theme.glassEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudScreen(
    viewModel: CloudViewModel = hiltViewModel(),
    onNavigateToSpotifyPlaylist: (String, String, String?) -> Unit,
    onNavigateToSpotifyAlbum: (String, String, String?) -> Unit,
    onNavigateToNavidromeLogin: () -> Unit
) {
    val context = LocalContext.current
    val query by viewModel.searchQuery.collectAsState()
    val playlists by viewModel.filteredPlaylists.collectAsState()
    val albums by viewModel.filteredAlbums.collectAsState()
    val isConnected by viewModel.isSpotifyConnected.collectAsState()

    val uiError by viewModel.uiError.collectAsState()
    val isLoadingTracks by viewModel.isLoadingTracks.collectAsState()
    val isLoadingAlbums by viewModel.isLoadingAlbums.collectAsState()
    val isSyncing = isLoadingTracks || isLoadingAlbums

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Playlists", "Albums")

    // --- ERROR PROPAGATION (TOAST) ---
    LaunchedEffect(uiError) {
        uiError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    AnimatedContent(
        targetState = isConnected,
        transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(600)) },
        label = "CloudConnectionRitual"
    ) { connected ->
        if (!connected) {
            CloudConnectPrompt(
                onConnectSpotify = { (context as? MainActivity)?.authenticateSpotify() },
                onConnectNavidrome = onNavigateToNavidromeLogin
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                CloudHeader(
                    query = query,
                    onQueryChange = { viewModel.onSearchQueryChange(it) },
                    onRefresh = { viewModel.forceRefreshArchives() }
                )

                SecondaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    divider = {},
                    indicator = {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(selectedTab),
                            height = 3.dp,
                            color = SpotifyGreen
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title.uppercase(),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = if (selectedTab == index) SpotifyGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        )
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (isSyncing && playlists.isEmpty() && albums.isEmpty()) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = SpotifyGreen
                        )
                    } else {
                        when (selectedTab) {
                            0 -> {
                                if (playlists.isEmpty() && !isSyncing) {
                                    CloudEmptyState(message = if (query.isEmpty()) "No Spotify Playlists Found." else "No Playlists Match Your Query.")
                                } else {
                                    ArchiveGrid(
                                        items = playlists.map { ArchiveItem(it.id, it.name, it.images?.firstOrNull()?.url, "Playlist") },
                                        onClick = { id, name, img -> onNavigateToSpotifyPlaylist(id, name, img) }
                                    )
                                }
                            }
                            1 -> {
                                if (albums.isEmpty() && !isSyncing) {
                                    CloudEmptyState(message = if (query.isEmpty()) "No Saved Albums Found." else "No Albums Match Your Query.")
                                } else {
                                    ArchiveGrid(
                                        items = albums.map { ArchiveItem(it.id, it.name, it.images?.firstOrNull()?.url, it.artists.firstOrNull()?.name ?: "Unknown") },
                                        onClick = { id, name, img -> onNavigateToSpotifyAlbum(id, name, img) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CloudHeader(query: String, onQueryChange: (String) -> Unit, onRefresh: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "CLOUD ARCHIVES",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground
            )
            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .size(42.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), CircleShape)
            ) {
                Icon(WitcherIcons.Refresh, "Refresh", tint = SpotifyGreen)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .glassEffect(CircleShape),
            placeholder = { Text("Search the cloud...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)) },
            leadingIcon = { Icon(WitcherIcons.Search, null, tint = SpotifyGreen, modifier = Modifier.size(20.dp)) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(WitcherIcons.Close, "Clear", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                unfocusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = SpotifyGreen
            ),
            shape = CircleShape,
            singleLine = true
        )
    }
}

@Composable
fun CloudEmptyState(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            WitcherIcons.Library,
            null,
            modifier = Modifier.size(56.dp).graphicsLayer { alpha = 0.15f },
            tint = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CloudConnectPrompt(
    onConnectSpotify: () -> Unit,
    onConnectNavidrome: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().glassEffect(MaterialTheme.shapes.extraLarge),
            border = BorderStroke(1.dp, SpotifyGreen.copy(alpha = 0.3f)),
            color = Color.Transparent
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(WitcherIcons.Library, null, tint = SpotifyGreen, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Spotify Integration",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Authorize access to your Spotify archives to stream playlists and albums directly via TigerPlayer.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onConnectSpotify,
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("OPEN PORTAL", fontWeight = FontWeight.Black, color = Color.Black)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .glassEffect(MaterialTheme.shapes.large)
                .bounceClick { onConnectNavidrome() },
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
            color = Color.Transparent
        ) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    WitcherIcons.Cloud,
                    null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        "Navidrome Server",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Forge the Subsonic ritual connection.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

data class ArchiveItem(val id: String, val name: String, val imageUrl: String?, val subtitle: String)

@Composable
private fun ArchiveGrid(
    items: List<ArchiveItem>,
    onClick: (String, String, String?) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(
            items = items,
            key = { it.id }
        ) { item ->
            Column(modifier = Modifier.bounceClick { onClick(item.id, item.name, item.imageUrl) }) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .glassEffect(MaterialTheme.shapes.large)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
package com.example.tigerplayer.ui.cloud

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.tigerplayer.MainActivity
import com.example.tigerplayer.data.remote.model.SpotifyPlaylist
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.bounceClick
import com.example.tigerplayer.ui.cloud.SpotifyPlaylistScreen

@Composable
fun CloudScreen(
    viewModel: CloudViewModel = hiltViewModel(),
    onNavigateToSpotifyPlaylist: (String, String, String?) -> Unit,
    onNavigateToSpotifyAlbum: (String, String, String?) -> Unit
) {
    val context = LocalContext.current
    val query by viewModel.searchQuery.collectAsState()
    val playlists by viewModel.filteredPlaylists.collectAsState()
    val albums by viewModel.filteredAlbums.collectAsState()
    val isConnected by viewModel.isSpotifyConnected.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Playlists", "Albums")

    // The Ritual Trigger: Fetch data the moment the connection is secured
    LaunchedEffect(isConnected) {
        if (isConnected) {
            viewModel.fetchUserPlaylists()
            viewModel.fetchSavedAlbums()
        }
    }

    AnimatedContent(
        targetState = isConnected,
        transitionSpec = {
            fadeIn(animationSpec = tween(500)) togetherWith fadeOut(animationSpec = tween(500))
        },
        label = "CloudContentSwitch"
    ) { connected ->
        if (!connected) {
            // STATE 1: The Gate is Locked (Login Prompt)
            CloudConnectPrompt(
                onConnectSpotify = {
                    (context as? MainActivity)?.authenticateSpotify()
                }
            )
        } else {
            // STATE 2: The Archives are Open (Search + Library)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF121212)) // Spotify Noir
            ) {
                // THE NEW COMPONENT: Extracted Search Ritual
                SpotifySearchHeader(
                    query = query,
                    onQueryChange = { viewModel.onSearchQueryChange(it) }
                )

                // The Archive Toggles
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = Color(0xFF1DB954), // Spotify Green
                    divider = {},
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = Color(0xFF1DB954),
                            height = 3.dp
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontWeight = FontWeight.Black,
                                    color = if (selectedTab == index) Color.White else Color.Gray
                                )
                            }
                        )
                    }
                }

                // Filtered Content Grids
                Box(modifier = Modifier.fillMaxSize()) {
                    when (selectedTab) {
                        0 -> PlaylistGrid(
                            playlists = playlists,
                            onPlaylistClick = onNavigateToSpotifyPlaylist
                        )
                        1 -> SpotifyAlbumsGrid(
                            viewModel = viewModel,
                            albums = albums,
                            onAlbumClick = onNavigateToSpotifyAlbum
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SpotifySearchHeader(
    query: String,
    onQueryChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Text(
            text = "Spotify Library",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Black
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            placeholder = { Text("Search your archives...", color = Color.Gray) },
            leadingIcon = { Icon(WitcherIcons.Search, null, tint = Color.Gray) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(WitcherIcons.Close, null, tint = Color.White)
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF282828),
                unfocusedContainerColor = Color(0xFF282828),
                focusedIndicatorColor = Color(0xFF1DB954), // Spotify Green
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = Color(0xFF1DB954),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = MaterialTheme.shapes.medium,
            singleLine = true
        )
    }
}


@Composable
private fun CloudConnectPrompt(onConnectSpotify: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Spotify Connect Card
        ElevatedCard(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = WitcherIcons.Library, // You can swap this for a custom Spotify icon later
                    contentDescription = null,
                    tint = Color(0xFF1DB954), // Spotify Green
                    modifier = Modifier.size(64.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Sync with Spotify",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Connect your account to fetch your private playlists and control playback directly from TigerPlayer.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onConnectSpotify,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954)), // Spotify Green
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().height(52.dp).bounceClick { }
                ) {
                    Text("AUTHORIZE SPOTIFY", fontWeight = FontWeight.Black, color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Navidrome Placeholder Card (For the future)
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Navidrome Support",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
                Text(
                    text = "Personal FLAC streaming via Subsonic API is currently being forged.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun PlaylistGrid(
    playlists: List<SpotifyPlaylist>,
    // RECTIFIED: Matches the (ID, Name, URL) signature
    onPlaylistClick: (String, String, String?) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(playlists) { playlist ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .bounceClick {
                        // PASS THE FULL TRINITY: ID, Name, and the First Image URL
                        onPlaylistClick(
                            playlist.id,
                            playlist.name,
                            playlist.images?.firstOrNull()?.url
                        )
                    }
            ) {
                AsyncImage(
                    model = playlist.images?.firstOrNull()?.url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.aspectRatio(1f).clip(MaterialTheme.shapes.medium)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White
                )
            }
        }
    }

}


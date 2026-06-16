package com.example.tigerplayer.ui.youtube

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.tigerplayer.data.remote.api.YouTubeTrack
import com.example.tigerplayer.ui.theme.*

@Composable
fun YouTubeSearchScreen(
    viewModel: YouTubeSearchViewModel = hiltViewModel(),
    isEmbedded: Boolean = false,
    onBackClick: () -> Unit = {}
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedVideoId by remember { mutableStateOf<String?>(null) }

    val content = @Composable { padding: PaddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .graphicsLayer {
                    shadowElevation = 1f
                }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Official YouTube Player (Iframe Embed)
                selectedVideoId?.let { videoId ->
                    YouTubePlayerView(
                        videoId = videoId,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16 / 9f)
                            .padding(16.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }

                when (val state = uiState) {
                    is YouTubeSearchUiState.Idle -> {
                        YouTubeSearchPlaceholder(
                            icon = Icons.Default.Search,
                            message = "Search for your favorite music on YouTube"
                        )
                    }
                    is YouTubeSearchUiState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            color = TigerNeonOrange
                        )
                    }
                    is YouTubeSearchUiState.Success -> {
                        if (state.tracks.isEmpty()) {
                            YouTubeSearchPlaceholder(
                                icon = Icons.Default.Search,
                                message = "No results found"
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = if (isEmbedded) 150.dp else 80.dp)
                            ) {
                                items(state.tracks) { track ->
                                    YouTubeTrackItem(
                                        track = track,
                                        onClick = {
                                            selectedVideoId = track.videoId
                                        }
                                    )
                                }
                            }
                        }
                    }
                    is YouTubeSearchUiState.Error -> {
                        YouTubeErrorState(
                            message = state.message,
                            onRetry = viewModel::retrySearch
                        )
                    }
                }
            }
        }
    }

    if (isEmbedded) {
        Column(modifier = Modifier.fillMaxSize()) {
            YouTubeSearchBar(
                query = searchQuery,
                onQueryChange = viewModel::onSearchQueryChanged,
                onClearClick = viewModel::clearSearch,
                onBackClick = onBackClick,
                isEmbedded = true
            )
            content(PaddingValues(0.dp))
        }
    } else {
        Scaffold(
            containerColor = Color.Black,
            topBar = {
                YouTubeSearchBar(
                    query = searchQuery,
                    onQueryChange = viewModel::onSearchQueryChanged,
                    onClearClick = viewModel::clearSearch,
                    onBackClick = onBackClick
                )
            }
        ) { padding ->
            content(padding)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun YouTubeSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearClick: () -> Unit,
    onBackClick: () -> Unit,
    isEmbedded: Boolean = false
) {
    Surface(
        color = if (isEmbedded) Color.Transparent else Color.Black,
        modifier = Modifier.statusBarsPadding()
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(12.dp))
                .glassEffect(RoundedCornerShape(12.dp)),
            placeholder = { Text("Search YouTube Music...", color = TigerTextLow) },
            leadingIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TigerTextMed
                    )
                }
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClearClick) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TigerTextMed)
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = TigerSurfaceCharcoal,
                unfocusedContainerColor = TigerSurfaceCharcoal,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = TigerNeonOrange,
                focusedTextColor = TigerTextHigh,
                unfocusedTextColor = TigerTextHigh
            ),
            singleLine = true
        )
    }
}

@Composable
private fun YouTubeTrackItem(
    track: YouTubeTrack,
    onClick: () -> Unit
) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TigerTextHigh,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = track.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TigerTextMed,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun YouTubeSearchPlaceholder(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = TigerSurfaceElevated
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            color = TigerTextLow,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun YouTubeErrorState(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Error: $message",
            color = Color.Red.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = TigerSurfaceCharcoal)
        ) {
            Text("Retry", color = TigerTextHigh)
        }
    }
}


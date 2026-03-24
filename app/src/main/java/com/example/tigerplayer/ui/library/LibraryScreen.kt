package com.example.tigerplayer.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tigerplayer.ui.player.PlayerViewModel
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    viewModel: PlayerViewModel,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToAlbum: (String) -> Unit,
    onNavigateToPlaylist: (Long, String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val tabs = listOf("Songs", "Albums", "Artists", "Playlists")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Universal Header with integrated search
        UniversalHeader(
            title = "Archives",
            searchQuery = uiState.searchQuery,
            isSearchActive = uiState.searchQuery.isNotEmpty(),
            onSearchToggle = { if (uiState.searchQuery.isNotEmpty()) viewModel.clearSearch() },
            onSearchQueryChange = { viewModel.onSearchQueryChanged(it) }
        )

        SecondaryScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = Color.Transparent,
            edgePadding = 16.dp,
            divider = {},
            indicator = {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(pagerState.currentPage),
                    height = 3.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = {
                        Text(
                            text = title.uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (pagerState.currentPage == index) FontWeight.Black else FontWeight.Bold,
                            color = if (pagerState.currentPage == index) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp
                        )
                    }
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(0.dp) // GHOST FIX: Managed by MainScreen Scaffold
        ) { page ->
            when (page) {
                0 -> SongsTab(viewModel)
                1 -> AlbumsTab(viewModel, onNavigateToAlbum)
                2 -> ArtistsList(viewModel = viewModel, onArtistClick = onNavigateToArtist)
                3 -> PlaylistsTab(viewModel, onNavigateToPlaylist)
            }
        }
    }
}


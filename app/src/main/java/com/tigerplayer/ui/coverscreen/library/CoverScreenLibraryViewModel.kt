package com.tigerplayer.ui.coverscreen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tigerplayer.data.model.Artist
import com.tigerplayer.data.repository.AudioRepository
import com.tigerplayer.data.repository.SpotifyRepository
import com.tigerplayer.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

enum class CoverScreenLibrarySource {
    LOCAL,
    SPOTIFY
}

@HiltViewModel
class CoverScreenLibraryViewModel @Inject constructor(
    private val audioRepository: AudioRepository,
    private val spotifyRepository: SpotifyRepository,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(CoverScreenLibraryTab.HISTORY)
    val selectedTab: StateFlow<CoverScreenLibraryTab> = _selectedTab.asStateFlow()

    private val _selectedSource = MutableStateFlow(CoverScreenLibrarySource.LOCAL)
    val selectedSource: StateFlow<CoverScreenLibrarySource> = _selectedSource.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _page = MutableStateFlow(0)
    val page: StateFlow<Int> = _page.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val artists: StateFlow<List<Artist>> = combine(
        _selectedSource,
        _searchQuery,
        _page
    ) { source, query, page ->
        ArtistRequest(source, query.trim(), page)
    }
        .distinctUntilChanged()
        .flatMapLatest { request ->
            when (request.source) {
                CoverScreenLibrarySource.LOCAL -> audioRepository.getArtists(
                    query = request.query,
                    limit = PAGE_SIZE,
                    offset = request.page.toOffset()
                )

                CoverScreenLibrarySource.SPOTIFY -> spotifyRepository.getArtists()
                    .map { artists ->
                        filterAndPageArtists(
                            artists = artists,
                            query = request.query,
                            page = request.page,
                            pageSize = PAGE_SIZE
                        )
                    }
            }
        }
        .flowOn(ioDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectTab(tab: CoverScreenLibraryTab) {
        _selectedTab.value = tab
    }

    fun selectSource(source: CoverScreenLibrarySource) {
        _selectedSource.value = source
        _page.value = 0
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        _page.value = 0
    }

    fun setPage(page: Int) {
        _page.value = page.coerceAtLeast(0)
    }

    fun nextPage() {
        _page.update { current -> if (current < Int.MAX_VALUE) current + 1 else current }
    }

    fun previousPage() {
        _page.update { current -> (current - 1).coerceAtLeast(0) }
    }

    private fun Int.toOffset(): Int =
        (toLong() * PAGE_SIZE).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    private data class ArtistRequest(
        val source: CoverScreenLibrarySource,
        val query: String,
        val page: Int
    )

    private companion object {
        const val PAGE_SIZE = 50
    }
}

internal fun filterAndPageArtists(
    artists: List<Artist>,
    query: String,
    page: Int,
    pageSize: Int
): List<Artist> {
    val boundedPageSize = pageSize.coerceIn(1, 50)
    val normalizedQuery = query.trim()
    val matchingArtists = if (normalizedQuery.isEmpty()) {
        artists
    } else {
        artists.filter { artist ->
            artist.name.contains(normalizedQuery, ignoreCase = true) ||
                artist.genres.any { genre -> genre.contains(normalizedQuery, ignoreCase = true) }
        }
    }
    val offset = (page.coerceAtLeast(0).toLong() * boundedPageSize)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()
    return matchingArtists
        .sortedBy { it.name.lowercase() }
        .drop(offset)
        .take(boundedPageSize)
}

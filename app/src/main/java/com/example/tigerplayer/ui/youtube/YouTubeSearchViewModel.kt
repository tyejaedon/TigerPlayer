package com.example.tigerplayer.ui.youtube

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tigerplayer.data.remote.api.YouTubeRepository
import com.example.tigerplayer.data.remote.api.YouTubeTrack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

sealed interface YouTubeSearchUiState {
    object Idle : YouTubeSearchUiState
    object Loading : YouTubeSearchUiState
    data class Success(val tracks: List<YouTubeTrack>) : YouTubeSearchUiState
    data class Error(val message: String) : YouTubeSearchUiState
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class YouTubeSearchViewModel @Inject constructor(
    private val repository: YouTubeRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val uiState: StateFlow<YouTubeSearchUiState> = _searchQuery
        .debounce(500L.milliseconds)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            if (query.isBlank()) {
                flowOf(YouTubeSearchUiState.Idle)
            } else {
                flow<YouTubeSearchUiState> {
                    emit(YouTubeSearchUiState.Loading)
                    repository.searchMusic(query)
                        .onSuccess { tracks ->
                            emit(YouTubeSearchUiState.Success(tracks))
                        }
                        .onFailure { exception ->
                            // Gracefully handle exceptions by emitting UiState.Error
                            val errorMessage = when (exception) {
                                is java.io.IOException -> "Network error. Please check your connection."
                                else -> exception.message ?: "An unexpected error occurred"
                            }
                            emit(YouTubeSearchUiState.Error(errorMessage))
                        }
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = YouTubeSearchUiState.Idle
        )

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    fun retrySearch() {
        val currentQuery = _searchQuery.value
        _searchQuery.value = ""
        _searchQuery.value = currentQuery
    }
}

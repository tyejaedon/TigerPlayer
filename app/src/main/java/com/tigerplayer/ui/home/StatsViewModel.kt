package com.tigerplayer.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.engine.CoverScreenStatsSnapshot
import com.tigerplayer.engine.LibraryEngine
import com.tigerplayer.engine.MetadataEngine
import com.tigerplayer.engine.NetworkEngine
import com.tigerplayer.engine.StatsEngine
import com.tigerplayer.ui.player.DetailedStatsUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

/**
 * Scoped ViewModel for listening stats, analytics, and home curation metrics.
 * Separated from playback and library ViewModels to eliminate recomposition cascades (issue #72).
 */
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val statsEngine: StatsEngine,
    private val libraryEngine: LibraryEngine,
    private val networkEngine: NetworkEngine,
    private val metadataEngine: MetadataEngine
) : ViewModel() {

    private val libraryRefreshTrigger = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val unifiedTracksFlow: SharedFlow<List<AudioTrack>> = libraryRefreshTrigger
        .flatMapLatest { networkEngine.getUnifiedLibraryFlow() }
        .shareIn(viewModelScope, SharingStarted.Lazily, replay = 1)

    val homeUiState: StateFlow<HomeUiState> = libraryEngine.getHomeUiStateFlow(
        unifiedTracksFlow
    ).stateIn(viewModelScope, SharingStarted.Lazily, HomeUiState())

    val detailedStatsState: StateFlow<DetailedStatsUiState> = statsEngine.getDetailedStatsFlow(
        unifiedTracksFlow,
        metadataEngine.artistDetails
    ).stateIn(viewModelScope, SharingStarted.Lazily, DetailedStatsUiState())

    val coverScreenStats: StateFlow<CoverScreenStatsSnapshot> =
        statsEngine.getCoverScreenStatsSnapshotFlow().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(replayExpirationMillis = 5.minutes.inWholeMilliseconds),
            CoverScreenStatsSnapshot()
        )

    private val _sonicFootprintOpenRequest = MutableStateFlow(false)
    val sonicFootprintOpenRequest: StateFlow<Boolean> = _sonicFootprintOpenRequest.asStateFlow()

    fun updateStatsFilter(filter: String) {
        statsEngine.updateStatsFilter(filter)
    }

    fun requestSonicFootprintOpen() {
        _sonicFootprintOpenRequest.value = true
    }

    fun consumeSonicFootprintOpenRequest() {
        _sonicFootprintOpenRequest.value = false
    }

    fun refreshStats() {
        libraryRefreshTrigger.value += 1
    }
}

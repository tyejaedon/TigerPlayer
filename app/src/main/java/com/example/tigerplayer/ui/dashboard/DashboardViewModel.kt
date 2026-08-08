package com.example.tigerplayer.ui.dashboard

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.repository.AudioRepository
import com.example.tigerplayer.data.repository.SpotifyAuthManager
import com.example.tigerplayer.data.repository.SpotifyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CurationFeedMeta(
    val lastAttemptEpochMs: Long = 0L,
    val lastSuccessEpochMs: Long? = null,
    val isLoading: Boolean = true,
    val lastErrorMessage: String? = null,
    val trackCount: Int = 0,
    val totalDurationMs: Long = 0L,
    val uniqueArtistCount: Int = 0
) {
    fun ageMs(now: Long = System.currentTimeMillis()): Long? {
        val anchor = lastSuccessEpochMs ?: lastAttemptEpochMs
        if (anchor <= 0L) return null
        return (now - anchor).coerceAtLeast(0L)
    }

    fun isStale(staleAfterMs: Long, now: Long = System.currentTimeMillis()): Boolean {
        val age = ageMs(now) ?: return false
        return age > staleAfterMs
    }
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel @Inject constructor(
    private val audioRepository: AudioRepository,
    private val spotifyRepository: SpotifyRepository,
    private val spotifyAuthManager: SpotifyAuthManager
) : ViewModel() {

    private val refreshTickMs = MutableStateFlow(System.currentTimeMillis())
    private val spotifyDaylistTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    private val spotifyDiscoveryWeeklyTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    private val _daylistMeta = MutableStateFlow(CurationFeedMeta())
    private val _discoveryMeta = MutableStateFlow(CurationFeedMeta())

    val daylistMeta: StateFlow<CurationFeedMeta> = _daylistMeta
    val discoveryMeta: StateFlow<CurationFeedMeta> = _discoveryMeta

    private val localDaylistTracks: Flow<List<AudioTrack>> = refreshTickMs
        .flatMapLatest { now ->
            val segment = currentDayBucket(now)
            audioRepository.getNeonDaylistTracks(
                segment = segment,
                sinceMillis = now - DAYLIST_LOOKBACK_MS,
                limit = DAYLIST_SIZE
            )
        }
        .catch { e ->
            Log.e("DashboardVM", "Error fetching daylist", e)
            emit(emptyList())
        }

    private val localDiscoveryWeeklyTracks: Flow<List<AudioTrack>> = refreshTickMs
        .flatMapLatest { now ->
            audioRepository.getVaultDiscoveryTracks(
                sinceMillis = now - DISCOVERY_STALE_MS,
                limit = DISCOVERY_SIZE
            )
        }
        .catch { e ->
            Log.e("DashboardVM", "Error fetching discovery", e)
            emit(emptyList())
        }

    val daylistTracks: StateFlow<List<AudioTrack>> = combine(
        spotifyDaylistTracks,
        localDaylistTracks
    ) { spotifyTracks, localTracks ->
        spotifyTracks.ifEmpty { localTracks }.take(DAYLIST_SIZE)
    }
        .onEach { tracks -> updateFeedMeta(_daylistMeta, tracks, refreshTickMs.value) }
        .catch { e ->
            _daylistMeta.value = _daylistMeta.value.copy(
                isLoading = false,
                lastErrorMessage = e.message
            )
            Log.e("DashboardVM", "Error combining daylist feeds", e)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    val discoveryWeeklyTracks: StateFlow<List<AudioTrack>> = combine(
        spotifyDiscoveryWeeklyTracks,
        localDiscoveryWeeklyTracks
    ) { spotifyTracks, localTracks ->
        spotifyTracks.ifEmpty { localTracks }.take(DISCOVERY_SIZE)
    }
        .onEach { tracks -> updateFeedMeta(_discoveryMeta, tracks, refreshTickMs.value) }
        .catch { e ->
            _discoveryMeta.value = _discoveryMeta.value.copy(
                isLoading = false,
                lastErrorMessage = e.message
            )
            Log.e("DashboardVM", "Error combining discovery feeds", e)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = emptyList()
        )

    init {
        refreshForYou()
    }

    fun refreshForYou() {
        val now = System.currentTimeMillis()
        refreshTickMs.value = now
        _daylistMeta.value = _daylistMeta.value.copy(
            lastAttemptEpochMs = now,
            isLoading = true,
            lastErrorMessage = null
        )
        _discoveryMeta.value = _discoveryMeta.value.copy(
            lastAttemptEpochMs = now,
            isLoading = true,
            lastErrorMessage = null
        )

        viewModelScope.launch {
            refreshSpotifyCurations()
        }
    }

    private suspend fun refreshSpotifyCurations() {
        val userToken = spotifyAuthManager.getValidUserToken()
        if (userToken.isBlank()) {
            spotifyDaylistTracks.value = emptyList()
            spotifyDiscoveryWeeklyTracks.value = emptyList()
            return
        }

        try {
            val curation = spotifyRepository.fetchHomeCurations(userToken)
            spotifyDaylistTracks.value = curation.daylist.take(DAYLIST_SIZE)
            spotifyDiscoveryWeeklyTracks.value = curation.discoveryWeekly.take(DISCOVERY_SIZE)
        } catch (e: Exception) {
            Log.e("DashboardVM", "Spotify curation refresh failed, falling back to local mixes.", e)
            spotifyDaylistTracks.value = emptyList()
            spotifyDiscoveryWeeklyTracks.value = emptyList()
        }
    }

    private fun updateFeedMeta(
        metaFlow: MutableStateFlow<CurationFeedMeta>,
        tracks: List<AudioTrack>,
        attemptEpochMs: Long
    ) {
        val now = System.currentTimeMillis()
        val previous = metaFlow.value
        val uniqueArtists = tracks
            .map { it.artist.trim().lowercase() }
            .filter { it.isNotBlank() }
            .distinct()
            .size

        metaFlow.value = previous.copy(
            lastAttemptEpochMs = attemptEpochMs,
            lastSuccessEpochMs = if (tracks.isNotEmpty()) now else previous.lastSuccessEpochMs,
            isLoading = false,
            lastErrorMessage = null,
            trackCount = tracks.size,
            totalDurationMs = tracks.sumOf { it.durationMs },
            uniqueArtistCount = uniqueArtists
        )
    }

    private fun currentDayBucket(now: Long): String {
        val hour = Calendar.getInstance().apply { timeInMillis = now }
            .get(Calendar.HOUR_OF_DAY)

        return when (hour) {
            in 5..11 -> "MORNING"
            in 12..16 -> "AFTERNOON"
            in 17..21 -> "EVENING"
            else -> "NIGHT"
        }
    }

    private companion object {
        const val DAYLIST_SIZE = 20
        const val DISCOVERY_SIZE = 30
        const val STOP_TIMEOUT_MS = 5_000L
        const val DAYLIST_LOOKBACK_MS = 14L * 24L * 60L * 60L * 1_000L
        const val DISCOVERY_STALE_MS = 180L * 24L * 60L * 60L * 1_000L
    }
}

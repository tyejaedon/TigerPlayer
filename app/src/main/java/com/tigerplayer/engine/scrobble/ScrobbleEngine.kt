package com.tigerplayer.engine.scrobble

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.tigerplayer.data.local.MediaSource
import com.tigerplayer.data.local.SettingsDataStore
import com.tigerplayer.data.local.TigerSettingsState
import com.tigerplayer.data.local.dao.PendingScrobbleDao
import com.tigerplayer.data.local.entity.PendingScrobbleEntity
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.repository.LastFmRepository
import com.tigerplayer.data.repository.ScrobbleOutcome
import com.tigerplayer.di.IoDispatcher
import com.tigerplayer.engine.QualifiedPlay
import com.tigerplayer.engine.StatsEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Coordinates Last.fm Now Playing updates, qualified-play scrobbling via [StatsEngine],
 * and durable offline queueing with exponential backoff and reconnect flush.
 */
@Singleton
class ScrobbleEngine(
    private val lastFmRepository: LastFmRepository,
    private val pendingScrobbleDao: PendingScrobbleDao,
    private val settingsDataStore: SettingsDataStore,
    private val statsEngine: StatsEngine,
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    @Inject
    constructor(
        lastFmRepository: LastFmRepository,
        pendingScrobbleDao: PendingScrobbleDao,
        settingsDataStore: SettingsDataStore,
        statsEngine: StatsEngine,
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ) : this(
        lastFmRepository = lastFmRepository,
        pendingScrobbleDao = pendingScrobbleDao,
        settingsDataStore = settingsDataStore,
        statsEngine = statsEngine,
        context = context,
        ioDispatcher = ioDispatcher,
        clockMs = { System.currentTimeMillis() }
    )
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val flushMutex = Mutex()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    val pendingCount: Flow<Int> = pendingScrobbleDao.observeCount()

    @Volatile
    private var latestSettings: TigerSettingsState = TigerSettingsState()

    init {
        scope.launch {
            settingsDataStore.settingsFlow.collect { settings ->
                latestSettings = settings
            }
        }
        scope.launch {
            statsEngine.qualifiedPlays.collect { play ->
                handleQualifiedPlay(play)
            }
        }
        registerNetworkCallback()
    }

    fun clearAuthError() {
        _authError.value = null
    }

    /**
     * Sends a `track.updateNowPlaying` update when playback starts on a qualifying track.
     */
    fun onTrackStarted(track: AudioTrack) {
        scope.launch {
            if (!lastFmRepository.isConnected.value) return@launch
            val settings = settingsDataStore.settingsFlow.first()
            if (!settings.lastfmScrobbleEnabled || !settings.lastfmNowPlayingEnabled) return@launch
            val source = resolveSource(track)
            if (!isSourceAllowed(source, settings.lastfmScrobbleRemote)) return@launch
            if (!hasScrobbleMetadata(track)) return@launch

            val durationSec = (track.durationMs / 1000L).toInt().coerceAtLeast(0)
            val outcome = lastFmRepository.updateNowPlaying(
                artist = track.artist.trim(),
                track = track.title.trim(),
                album = track.album.trim().takeIf { it.isNotEmpty() },
                durationSec = durationSec
            )
            if (outcome is ScrobbleOutcome.AuthInvalid) {
                _authError.value = outcome.reason
            }
        }
    }

    /**
     * Enqueues a [QualifiedPlay] emitted by [StatsEngine] and attempts immediate delivery.
     */
    internal suspend fun handleQualifiedPlay(play: QualifiedPlay) {
        if (!lastFmRepository.isConnected.value) return
        val settings = runCatching { settingsDataStore.settingsFlow.first() }.getOrDefault(latestSettings)
        if (!settings.lastfmScrobbleEnabled) return
        if (!isSourceAllowed(play.source, settings.lastfmScrobbleRemote)) return
        if (!hasScrobbleMetadata(play.track)) return

        val durationSec = (play.track.durationMs / 1000L).toInt().coerceAtLeast(0)
        val entity = PendingScrobbleEntity(
            trackId = play.track.id,
            artist = play.track.artist.trim(),
            track = play.track.title.trim(),
            album = play.track.album.trim().takeIf { it.isNotEmpty() },
            durationSec = durationSec,
            timestampSec = play.startedAtEpochSec,
            createdAtMs = clockMs()
        )
        pendingScrobbleDao.insert(entity)
        flushQueue()
    }

    /**
     * Flushes due scrobbles from Room in batches of up to 50 with exponential backoff on failure.
     */
    suspend fun flushQueue() {
        flushMutex.withLock {
            if (!lastFmRepository.isConnected.value) return
            while (true) {
                val nowMs = clockMs()
                val batch = pendingScrobbleDao.getDueScrobbles(nowMs = nowMs, limit = MAX_BATCH_SIZE)
                if (batch.isEmpty()) break

                when (val outcome = lastFmRepository.scrobbleBatch(batch)) {
                    is ScrobbleOutcome.Success -> {
                        pendingScrobbleDao.deleteByIds(batch.map { it.id })
                        _authError.value = null
                    }
                    is ScrobbleOutcome.PermanentFailure -> {
                        pendingScrobbleDao.deleteByIds(batch.map { it.id })
                    }
                    is ScrobbleOutcome.AuthInvalid -> {
                        _authError.value = outcome.reason
                        break
                    }
                    is ScrobbleOutcome.TransientFailure -> {
                        batch.forEach { item ->
                            val nextAttempt = item.attemptCount + 1
                            if (nextAttempt >= MAX_RETRY_ATTEMPTS) {
                                pendingScrobbleDao.deleteByIds(listOf(item.id))
                            } else {
                                val nextRetry = nowMs + backoffMs(nextAttempt)
                                pendingScrobbleDao.updateRetry(item.id, nextAttempt, nextRetry)
                            }
                        }
                        break
                    }
                }
            }
        }
    }

    internal fun backoffMs(attemptCount: Int): Long {
        val shift = (attemptCount - 1).coerceIn(0, 10)
        return min(BASE_BACKOFF_MS shl shift, MAX_BACKOFF_MS)
    }

    private fun isSourceAllowed(source: MediaSource, includeRemote: Boolean): Boolean =
        includeRemote || source == MediaSource.LOCAL

    private fun hasScrobbleMetadata(track: AudioTrack): Boolean =
        track.artist.isNotBlank() &&
            !track.artist.equals("<unknown>", ignoreCase = true) &&
            track.title.isNotBlank()

    private fun resolveSource(track: AudioTrack): MediaSource = when {
        track.id.startsWith("spotify:", ignoreCase = true) -> MediaSource.SPOTIFY
        track.id.startsWith("navidrome_", ignoreCase = true) ||
            track.id.startsWith("navidrome:", ignoreCase = true) -> MediaSource.NAVIDROME
        else -> MediaSource.LOCAL
    }

    private fun registerNetworkCallback() {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val caps = cm.getNetworkCapabilities(network)
                    val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                    if (hasInternet) {
                        scope.launch {
                            flushQueue()
                        }
                    }
                }
            })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Unable to register network callback: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "ScrobbleEngine"
        const val MAX_BATCH_SIZE = 50
        const val MAX_RETRY_ATTEMPTS = 10
        const val BASE_BACKOFF_MS = 30_000L
        const val MAX_BACKOFF_MS = 6 * 60 * 60 * 1000L
    }
}

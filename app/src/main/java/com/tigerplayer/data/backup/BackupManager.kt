package com.tigerplayer.data.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tigerplayer.data.local.AudioReactiveHapticsProfile
import com.tigerplayer.data.local.DefaultPlayerView
import com.tigerplayer.data.local.MediaSource
import com.tigerplayer.data.local.PrismSpectralAnalysis
import com.tigerplayer.data.local.SettingsDataStore
import com.tigerplayer.data.local.SkipShortAudio
import com.tigerplayer.data.local.ThemeMode
import com.tigerplayer.data.local.TigerAccentStyle
import com.tigerplayer.data.local.TigerSettingsState
import com.tigerplayer.data.local.dao.PlaylistDao
import com.tigerplayer.data.local.dao.TigerDao
import com.tigerplayer.data.local.entity.PlaylistEntity
import com.tigerplayer.data.local.entity.PlaylistTrackCrossRef
import com.tigerplayer.data.local.entity.PlaybackHistoryEntity
import com.google.gson.GsonBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Orchestrates a full backup/restore of user-authored data: playlists, playback history, and
 * app settings. See [BackupManifest] for exactly what is (and, deliberately, is not) included.
 *
 * This class only ever performs file I/O against a caller-supplied [Uri] (expected to come from
 * SAF's `ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`, obtained by the UI layer) and delegates
 * all persistence to [PlaylistDao], [TigerDao], and [SettingsDataStore] â€” it never touches the
 * database or DataStore directly.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistDao: PlaylistDao,
    private val tigerDao: TigerDao,
    private val settingsDataStore: SettingsDataStore
) {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun exportTo(destination: Uri): Result<BackupSummary> = withContext(Dispatchers.IO) {
        try {
            val manifest = buildManifest()
            val json = gson.toJson(manifest)
            val stream = context.contentResolver.openOutputStream(destination, "wt")
                ?: return@withContext Result.failure(IOException("Unable to open destination for writing"))
            stream.use { out -> out.bufferedWriter().use { it.write(json) } }
            Result.success(
                BackupSummary(
                    playlistCount = manifest.playlists.size,
                    trackRefCount = manifest.playlists.sumOf { it.tracks.size },
                    historyCount = manifest.history.size
                )
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Backup export failed", e)
            Result.failure(e)
        }
    }

    suspend fun importFrom(
        source: Uri,
        strategy: RestoreStrategy = RestoreStrategy.MERGE
    ): Result<BackupSummary> = withContext(Dispatchers.IO) {
        try {
            val json = context.contentResolver.openInputStream(source)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: return@withContext Result.failure(IOException("Unable to open backup file"))

            val manifest = runCatching { gson.fromJson(json, BackupManifest::class.java) }
                .getOrNull()
                ?: return@withContext Result.failure(IOException("Backup file is not valid TigerPlayer JSON"))

            if (manifest.schemaVersion > BackupManifest.CURRENT_SCHEMA_VERSION) {
                return@withContext Result.failure(
                    IllegalStateException(
                        "This backup was created by a newer version of TigerPlayer and can't be restored here"
                    )
                )
            }

            restoreSettings(manifest.settings)
            val trackRefCount = restorePlaylists(manifest.playlists, strategy)
            restoreHistory(manifest.history, strategy)

            Result.success(
                BackupSummary(
                    playlistCount = manifest.playlists.size,
                    trackRefCount = trackRefCount,
                    historyCount = manifest.history.size
                )
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Backup import failed", e)
            Result.failure(e)
        }
    }

    // --- EXPORT ---

    private suspend fun buildManifest(): BackupManifest {
        val settings = settingsDataStore.settingsFlow.first()
        val playlists = playlistDao.getAllPlaylistsSync().map { playlist ->
            val crossRefs = playlistDao.getCrossRefsForPlaylistSync(playlist.playlistId)
            PlaylistBackup(
                name = playlist.name,
                artworkUri = playlist.artworkUri,
                createdAt = playlist.createdAt,
                position = playlist.position,
                tracks = crossRefs.map {
                    PlaylistTrackBackup(
                        trackId = it.trackId,
                        dateAdded = it.dateAdded,
                        position = it.position
                    )
                }
            )
        }
        val history = tigerDao.getAllHistorySync().map { entry ->
            HistoryBackup(
                trackId = entry.trackId,
                title = entry.title,
                artist = entry.artist,
                album = entry.album,
                imageUrl = entry.imageUrl,
                durationListenedMs = entry.durationListenedMs,
                timestamp = entry.timestamp,
                source = entry.source.name
            )
        }
        return BackupManifest(
            appVersionName = currentAppVersionName(),
            createdAtEpochMs = System.currentTimeMillis(),
            settings = settings.toBackup(),
            playlists = playlists,
            history = history
        )
    }

    private fun TigerSettingsState.toBackup() = SettingsBackup(
        themeMode = themeMode.name,
        pureAmoledBlack = pureAmoledBlack,
        disablePip = disablePip,
        accentStyle = accentStyle.name,
        defaultPlayerView = defaultPlayerView.name,
        crossfadeDurationSec = crossfadeDurationSec,
        gaplessPlayback = gaplessPlayback,
        audioReactiveHaptics = audioReactiveHaptics,
        audioReactiveHapticsProfile = audioReactiveHapticsProfile.name,
        skipShortAudio = skipShortAudio.name,
        routeToSystemDecoderDsp = routeToSystemDecoderDsp,
        resumeOnBluetoothConnect = resumeOnBluetoothConnect,
        resumeOnWiredHeadsetConnect = resumeOnWiredHeadsetConnect,
        prismEnabled = prismEnabled,
        prismVocals = prismVocals,
        prismBeats = prismBeats,
        prismInstruments = prismInstruments,
        prismSpectralAnalysis = prismSpectralAnalysis.name
    )

    // --- IMPORT ---

    private suspend fun restoreSettings(backup: SettingsBackup) {
        settingsDataStore.setThemeMode(enumOrDefault(backup.themeMode, ThemeMode.SYSTEM))
        settingsDataStore.setPureAmoledBlack(backup.pureAmoledBlack)
        settingsDataStore.setDisablePip(backup.disablePip)
        settingsDataStore.setAccentStyle(enumOrDefault(backup.accentStyle, TigerAccentStyle.NEON_ORANGE))
        settingsDataStore.setDefaultPlayerView(
            enumOrDefault(backup.defaultPlayerView, DefaultPlayerView.ARTWORK_3D)
        )
        settingsDataStore.setCrossfadeDurationSec(backup.crossfadeDurationSec.coerceIn(0, 12))
        settingsDataStore.setGaplessPlayback(backup.gaplessPlayback)
        settingsDataStore.setAudioReactiveHaptics(backup.audioReactiveHaptics)
        settingsDataStore.setAudioReactiveHapticsProfile(
            enumOrDefault(backup.audioReactiveHapticsProfile, AudioReactiveHapticsProfile.BALANCED)
        )
        settingsDataStore.setSkipShortAudio(enumOrDefault(backup.skipShortAudio, SkipShortAudio.OFF))
        settingsDataStore.setRouteToSystemDecoderDsp(backup.routeToSystemDecoderDsp)
        settingsDataStore.setResumeOnBluetoothConnect(backup.resumeOnBluetoothConnect)
        settingsDataStore.setResumeOnWiredHeadsetConnect(backup.resumeOnWiredHeadsetConnect)
        settingsDataStore.setPrismEnabled(backup.prismEnabled)
        settingsDataStore.setPrismMix(
            vocals = backup.prismVocals.coerceIn(0f, 1f),
            beats = backup.prismBeats.coerceIn(0f, 1f),
            instruments = backup.prismInstruments.coerceIn(0f, 1f)
        )
        settingsDataStore.setPrismSpectralAnalysis(
            enumOrDefault(backup.prismSpectralAnalysis, PrismSpectralAnalysis.FFT)
        )
    }

    private suspend fun restorePlaylists(playlists: List<PlaylistBackup>, strategy: RestoreStrategy): Int {
        if (strategy == RestoreStrategy.REPLACE) {
            playlistDao.replaceAllPlaylists()
        }
        var trackRefCount = 0
        playlists.forEach { backup ->
            val newPlaylistId = playlistDao.insertPlaylist(
                PlaylistEntity(
                    name = backup.name,
                    artworkUri = backup.artworkUri,
                    createdAt = backup.createdAt,
                    position = backup.position
                )
            )
            if (backup.tracks.isNotEmpty()) {
                val crossRefs = backup.tracks.map {
                    PlaylistTrackCrossRef(
                        playlistId = newPlaylistId,
                        trackId = it.trackId,
                        dateAdded = it.dateAdded,
                        position = it.position
                    )
                }
                playlistDao.insertTracksBatch(crossRefs)
                trackRefCount += crossRefs.size
            }
        }
        return trackRefCount
    }

    private suspend fun restoreHistory(history: List<HistoryBackup>, strategy: RestoreStrategy) {
        if (strategy == RestoreStrategy.REPLACE) {
            tigerDao.clearHistory()
        }
        if (history.isEmpty()) return
        val entities = history.map {
            PlaybackHistoryEntity(
                id = 0, // force auto-generate; never reuse ids from the backup file
                trackId = it.trackId,
                title = it.title,
                artist = it.artist,
                album = it.album,
                imageUrl = it.imageUrl,
                durationListenedMs = it.durationListenedMs,
                timestamp = it.timestamp,
                source = enumOrDefault(it.source, MediaSource.LOCAL)
            )
        }
        tigerDao.insertHistoryBatch(entities)
    }

    private fun currentAppVersionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: APP_VERSION_NAME_UNKNOWN

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String?, default: T): T {
        if (value.isNullOrBlank()) return default
        return runCatching { enumValueOf<T>(value) }.getOrDefault(default)
    }

    private companion object {
        const val TAG = "BackupManager"
        const val APP_VERSION_NAME_UNKNOWN = "unknown"
    }
}




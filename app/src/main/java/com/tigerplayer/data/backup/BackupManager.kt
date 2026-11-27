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
import com.tigerplayer.data.local.dao.AudioDeviceDao
import com.tigerplayer.data.local.dao.PlaylistDao
import com.tigerplayer.data.local.dao.TigerDao
import com.tigerplayer.data.local.entity.AudioDeviceEntity
import com.tigerplayer.data.local.entity.DevicePresetEntity
import com.tigerplayer.data.local.entity.PlaylistEntity
import com.tigerplayer.data.local.entity.PlaylistTrackCrossRef
import com.tigerplayer.data.local.entity.PlaybackHistoryEntity
import com.tigerplayer.data.local.entity.SmartPlaylistEntity
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
 * all persistence to [PlaylistDao], [TigerDao], and [SettingsDataStore] — it never touches the
 * database or DataStore directly.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistDao: PlaylistDao,
    private val tigerDao: TigerDao,
    private val settingsDataStore: SettingsDataStore,
    private val audioDeviceDao: AudioDeviceDao
) {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun exportTo(destination: Uri): Result<BackupSummary> = withContext(Dispatchers.IO) {
        try {
            val manifest = buildManifest()
            val json = gson.toJson(manifest)
            val stream = openExportStream(destination)
                ?: return@withContext Result.failure(IOException("Unable to open destination for writing"))
            stream.use { out -> out.bufferedWriter().use { it.write(json) } }
            Result.success(
                BackupSummary(
                    playlistCount = manifest.playlists.size,
                    trackRefCount = manifest.playlists.sumOf { it.tracks.size },
                    historyCount = manifest.history.size,
                    audioDeviceCount = manifest.audioDevices.size,
                    devicePresetCount = manifest.devicePresets.size,
                    smartPlaylistCount = manifest.smartPlaylists.size
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

            val manifest = parseManifest(json)
                ?: return@withContext Result.failure(IOException("Backup file is not valid TigerPlayer JSON"))

            if (manifest.schemaVersion > BackupManifest.CURRENT_SCHEMA_VERSION) {
                return@withContext Result.failure(
                    IllegalStateException(
                        "This backup was created by a newer version of TigerPlayer and can't be restored here"
                    )
                )
            }

            val playlists = (manifest.playlists as List<PlaylistBackup>?).orEmpty()
            val history = (manifest.history as List<HistoryBackup>?).orEmpty()
            val audioDevices = (manifest.audioDevices as List<AudioDeviceBackup>?).orEmpty()
            val devicePresets = (manifest.devicePresets as List<DevicePresetBackup>?).orEmpty()
            val smartPlaylists = (manifest.smartPlaylists as List<SmartPlaylistBackup>?).orEmpty()

            restoreSettings(manifest.settings)
            val trackRefCount = restorePlaylists(playlists, strategy)
            restoreHistory(history, strategy)
            val restoredDevices = restoreAudioDevices(audioDevices, strategy)
            val restoredPresets = restorePresets(devicePresets, strategy)
            val restoredSmartPlaylists = restoreSmartPlaylists(smartPlaylists, strategy)

            val knownTrackIds = tigerDao.getCachedTrackIdsSync().toHashSet()
            val unresolvedByPlaylist = computeUnresolvedByPlaylist(playlists, knownTrackIds)
            val unresolvedTrackCount = computeUnresolvedTrackCount(playlists, history, knownTrackIds)

            Result.success(
                BackupSummary(
                    playlistCount = playlists.size,
                    trackRefCount = trackRefCount,
                    historyCount = history.size,
                    audioDeviceCount = restoredDevices,
                    devicePresetCount = restoredPresets,
                    smartPlaylistCount = restoredSmartPlaylists,
                    unresolvedTrackCount = unresolvedTrackCount,
                    unresolvedByPlaylist = unresolvedByPlaylist
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
        val audioDevices = audioDeviceDao.getAllDevicesSync().map { device ->
            AudioDeviceBackup(
                deviceId = device.deviceId,
                displayName = device.displayName,
                deviceType = device.deviceType,
                lastConnectedEpochMs = device.lastConnectedEpochMs,
                assignedPresetId = device.assignedPresetId,
                matchedAutoEqModel = device.matchedAutoEqModel,
                matchedAutoEqSource = device.matchedAutoEqSource,
                targetCurve = device.targetCurve,
                formFactor = device.formFactor
            )
        }
        val presets = audioDeviceDao.getAllPresetsSync().map { preset ->
            DevicePresetBackup(
                presetId = preset.presetId,
                name = preset.name,
                deviceId = preset.deviceId,
                preampDb = preset.preampDb,
                bandsJson = preset.bandsJson,
                isAutoEq = preset.isAutoEq,
                autoEqModel = preset.autoEqModel,
                autoEqSource = preset.autoEqSource,
                targetCurve = preset.targetCurve,
                formFactor = preset.formFactor
            )
        }
        val smartPlaylists = playlistDao.getAllSmartPlaylistsSync().map { smart ->
            SmartPlaylistBackup(
                name = smart.name,
                rulesJson = smart.rulesJson,
                sortBy = smart.sortBy,
                sortDescending = smart.sortDescending,
                limitCount = smart.limitCount,
                createdAt = smart.createdAt,
                updatedAt = smart.updatedAt
            )
        }
        return BackupManifest(
            appVersionName = currentAppVersionName(),
            createdAtEpochMs = System.currentTimeMillis(),
            settings = settings.toBackup(),
            playlists = playlists,
            history = history,
            audioDevices = audioDevices,
            devicePresets = presets,
            smartPlaylists = smartPlaylists
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
        settingsDataStore.setAccentStyle(enumOrDefault(backup.accentStyle, TigerAccentStyle.NEON_RED))
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
            val tracks = (backup.tracks as List<PlaylistTrackBackup>?).orEmpty()
            if (tracks.isNotEmpty()) {
                val crossRefs = tracks.map {
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
        playlistDao.ensureSingletonLikedPlaylist()
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

    private suspend fun restoreAudioDevices(devices: List<AudioDeviceBackup>, strategy: RestoreStrategy): Int {
        if (devices.isEmpty()) return 0
        devices.forEach { backup ->
            val entity = AudioDeviceEntity(
                deviceId = backup.deviceId,
                displayName = backup.displayName,
                deviceType = backup.deviceType,
                lastConnectedEpochMs = backup.lastConnectedEpochMs,
                firstSeenEpochMs = backup.lastConnectedEpochMs,
                assignedPresetId = backup.assignedPresetId,
                matchedAutoEqModel = backup.matchedAutoEqModel,
                matchedAutoEqSource = backup.matchedAutoEqSource,
                targetCurve = backup.targetCurve,
                formFactor = backup.formFactor
            )
            audioDeviceDao.upsertDevice(entity)
        }
        return devices.size
    }

    private suspend fun restorePresets(presets: List<DevicePresetBackup>, strategy: RestoreStrategy): Int {
        if (presets.isEmpty()) return 0
        presets.forEach { backup ->
            val entity = DevicePresetEntity(
                presetId = backup.presetId,
                name = backup.name,
                deviceId = backup.deviceId,
                preampDb = backup.preampDb,
                bandsJson = backup.bandsJson,
                isAutoEq = backup.isAutoEq,
                autoEqModel = backup.autoEqModel,
                autoEqSource = backup.autoEqSource,
                targetCurve = backup.targetCurve,
                formFactor = backup.formFactor
            )
            audioDeviceDao.upsertPreset(entity)
        }
        return presets.size
    }

    private suspend fun restoreSmartPlaylists(
        smartPlaylists: List<SmartPlaylistBackup>,
        strategy: RestoreStrategy
    ): Int {
        if (strategy == RestoreStrategy.REPLACE) {
            playlistDao.clearAllSmartPlaylists()
        }
        if (smartPlaylists.isEmpty()) return 0
        smartPlaylists.forEach { backup ->
            playlistDao.insertSmartPlaylist(
                SmartPlaylistEntity(
                    id = 0L,
                    name = backup.name,
                    rulesJson = backup.rulesJson,
                    sortBy = backup.sortBy,
                    sortDescending = backup.sortDescending,
                    limitCount = backup.limitCount,
                    createdAt = backup.createdAt,
                    updatedAt = backup.updatedAt
                )
            )
        }
        return smartPlaylists.size
    }

    private fun openExportStream(destination: Uri) =
        runCatching { context.contentResolver.openOutputStream(destination, "wt") }.getOrNull()
            ?: runCatching { context.contentResolver.openOutputStream(destination, "w") }.getOrNull()
            ?: runCatching { context.contentResolver.openOutputStream(destination) }.getOrNull()

    /**
     * Parses and normalizes legacy/partial backup JSON before Gson hydration so missing keys or
     * JSON nulls from older app versions never leak null into Kotlin non-null fields via Unsafe.
     */
    private fun parseManifest(rawJson: String): BackupManifest? = runCatching {
        val cleaned = rawJson.removePrefix("\uFEFF").trim()
        if (cleaned.isEmpty()) return null
        val parsed = JsonParser.parseString(cleaned)
        if (!parsed.isJsonObject) return null
        val root = parsed.asJsonObject

        if (!root.has("schemaVersion") || root.get("schemaVersion").isJsonNull) {
            root.addProperty("schemaVersion", BackupManifest.CURRENT_SCHEMA_VERSION)
        }
        if (!root.has("appVersionName") || root.get("appVersionName").isJsonNull) {
            root.addProperty("appVersionName", APP_VERSION_NAME_UNKNOWN)
        }

        val defaultSettings = gson.toJsonTree(TigerSettingsState().toBackup()).asJsonObject
        val settingsObj = if (root.has("settings") && root.get("settings").isJsonObject) {
            root.getAsJsonObject("settings")
        } else {
            JsonObject().also { root.add("settings", it) }
        }
        defaultSettings.entrySet().forEach { (key, defaultValue) ->
            if (!settingsObj.has(key) || settingsObj.get(key).isJsonNull) {
                settingsObj.add(key, defaultValue)
            }
        }

        listOf("playlists", "history", "audioDevices", "devicePresets", "smartPlaylists").forEach { key ->
            if (!root.has(key) || !root.get(key).isJsonArray) {
                root.add(key, JsonArray())
            }
        }

        root.getAsJsonArray("playlists").forEach { element ->
            if (element.isJsonObject) {
                val obj = element.asJsonObject
                if (!obj.has("name") || obj.get("name").isJsonNull) {
                    obj.addProperty("name", "Restored Playlist")
                }
                if (!obj.has("tracks") || !obj.get("tracks").isJsonArray) {
                    obj.add("tracks", JsonArray())
                }
            }
        }

        root.getAsJsonArray("history").forEach { element ->
            if (element.isJsonObject) {
                val obj = element.asJsonObject
                listOf("trackId", "title", "artist", "album").forEach { field ->
                    if (!obj.has(field) || obj.get(field).isJsonNull) {
                        obj.addProperty(field, "")
                    }
                }
                if (!obj.has("source") || obj.get("source").isJsonNull) {
                    obj.addProperty("source", MediaSource.LOCAL.name)
                }
            }
        }

        gson.fromJson(root, BackupManifest::class.java)
    }.getOrNull()

    private fun computeUnresolvedByPlaylist(
        playlists: List<PlaylistBackup>,
        knownTrackIds: Set<String>
    ): Map<String, Int> {
        val result = linkedMapOf<String, Int>()
        playlists.forEach { playlist ->
            val tracks = (playlist.tracks as List<PlaylistTrackBackup>?).orEmpty()
            val missing = tracks.count { it.trackId !in knownTrackIds }
            if (missing > 0) {
                result[playlist.name] = (result[playlist.name] ?: 0) + missing
            }
        }
        return result
    }

    private fun computeUnresolvedTrackCount(
        playlists: List<PlaylistBackup>,
        history: List<HistoryBackup>,
        knownTrackIds: Set<String>
    ): Int {
        val unresolvedIds = hashSetOf<String>()
        playlists.forEach { playlist ->
            val tracks = (playlist.tracks as List<PlaylistTrackBackup>?).orEmpty()
            tracks.forEach { track ->
                if (track.trackId !in knownTrackIds) unresolvedIds.add(track.trackId)
            }
        }
        history.forEach { entry ->
            if (entry.trackId !in knownTrackIds) unresolvedIds.add(entry.trackId)
        }
        return unresolvedIds.size
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




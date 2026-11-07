package com.tigerplayer.data.repository

import android.content.Context
import com.tigerplayer.data.local.dao.TigerDao
import com.tigerplayer.data.model.StorageBreakdown
import com.tigerplayer.data.model.StorageCategory
import com.tigerplayer.data.model.StorageCategoryInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository responsible for calculating disk utilization and managing cache lifecycle.
 */
@Singleton
class StorageRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tigerDao: TigerDao,
    private val lyricsRepository: LyricsRepository,
    private val mediaDataRepository: MediaDataRepository
) {

    /**
     * Inspects the file system and database to compute an aggregated storage breakdown.
     */
    suspend fun getStorageBreakdown(): StorageBreakdown = withContext(Dispatchers.IO) {
        val cacheDir = context.cacheDir
        val externalCacheDir = context.externalCacheDir
        val filesDir = context.filesDir

        val imageCacheDir = File(cacheDir, "image_cache")
        val artworkBytes = getDirectorySize(imageCacheDir)
        val artworkCount = getFileCount(imageCacheDir)

        val lyricsCount = tigerDao.getLyricsCacheCount()
        // Estimated storage footprint: average ~3.5 KB per synchronized lyrics entry
        val lyricsBytes = lyricsCount * 3_500L

        val artistCount = tigerDao.getArtistCacheCount()
        // Estimated storage footprint: average ~2.0 KB per cached artist biography/metadata
        val artistBytes = artistCount * 2_000L

        val waveformCount = tigerDao.getWaveformCacheCount()
        // Estimated storage footprint: average ~1.5 KB per downsampled waveform RMS peak blob
        val waveformBytes = waveformCount * 1_500L

        val internalCacheBytes = getDirectorySize(cacheDir)
        val externalCacheBytes = getDirectorySize(externalCacheDir)
        val totalCacheBytes = internalCacheBytes + externalCacheBytes

        val dbFile = context.getDatabasePath("tiger_database")
        val dbWalFile = context.getDatabasePath("tiger_database-wal")
        val dbShmFile = context.getDatabasePath("tiger_database-shm")
        val dbBytes = (if (dbFile.exists()) dbFile.length() else 0L) +
                (if (dbWalFile.exists()) dbWalFile.length() else 0L) +
                (if (dbShmFile.exists()) dbShmFile.length() else 0L)

        val filesBytes = getDirectorySize(filesDir)
        val coreDataBytes = dbBytes + filesBytes

        val totalAppBytes = totalCacheBytes + coreDataBytes

        StorageBreakdown(
            totalAppSizeBytes = totalAppBytes,
            totalCacheSizeBytes = totalCacheBytes,
            artwork = StorageCategoryInfo(
                category = StorageCategory.ARTWORK,
                sizeBytes = artworkBytes,
                itemCount = artworkCount,
                isPurgeable = true
            ),
            lyrics = StorageCategoryInfo(
                category = StorageCategory.LYRICS,
                sizeBytes = lyricsBytes,
                itemCount = lyricsCount,
                isPurgeable = true
            ),
            artists = StorageCategoryInfo(
                category = StorageCategory.ARTISTS,
                sizeBytes = artistBytes,
                itemCount = artistCount,
                isPurgeable = true
            ),
            waveforms = StorageCategoryInfo(
                category = StorageCategory.WAVEFORMS,
                sizeBytes = waveformBytes,
                itemCount = waveformCount,
                isPurgeable = true
            ),
            internalData = StorageCategoryInfo(
                category = StorageCategory.DATABASE_INTERNAL,
                sizeBytes = coreDataBytes,
                itemCount = null,
                isPurgeable = false
            ),
            isCalculating = false
        )
    }

    /**
     * Purges cached album and artist artwork from Coil disk cache.
     * @return Number of bytes freed.
     */
    suspend fun clearArtworkCache(): Long = withContext(Dispatchers.IO) {
        val imageCacheDir = File(context.cacheDir, "image_cache")
        val sizeBefore = getDirectorySize(imageCacheDir)
        deleteDirectoryContents(imageCacheDir)
        sizeBefore
    }

    /**
     * Purges cached synchronized lyrics from the local database.
     */
    suspend fun clearLyricsCache(): Unit = withContext(Dispatchers.IO) {
        lyricsRepository.clearLyricsCache()
    }

    /**
     * Purges cached artist biographies and remote metadata from the local database.
     */
    suspend fun clearArtistCache(): Unit = withContext(Dispatchers.IO) {
        mediaDataRepository.clearArtistCache()
    }

    /**
     * Purges downsampled audio waveform peaks from the local database.
     */
    suspend fun clearWaveformCache(): Unit = withContext(Dispatchers.IO) {
        tigerDao.clearWaveformCache()
    }

    /**
     * Purges all non-essential temporary caches across the file system and database.
     * Strictly preserves user playlists, playback history, and preferences.
     * @return Number of disk bytes freed.
     */
    suspend fun clearAllTemporaryCaches(): Long = withContext(Dispatchers.IO) {
        val cacheBefore = getDirectorySize(context.cacheDir) + getDirectorySize(context.externalCacheDir)

        // 1. Purge physical caches on disk
        deleteDirectoryContents(context.cacheDir)
        deleteDirectoryContents(context.externalCacheDir)

        // 2. Purge transient database caches
        try {
            lyricsRepository.clearLyricsCache()
            mediaDataRepository.clearArtistCache()
            tigerDao.clearWaveformCache()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Non-fatal if DB clear fails partially during IO purge
        }

        val cacheAfter = getDirectorySize(context.cacheDir) + getDirectorySize(context.externalCacheDir)
        (cacheBefore - cacheAfter).coerceAtLeast(0L)
    }

    private fun getDirectorySize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var total = 0L
        val files = dir.listFiles() ?: return 0L
        for (file in files) {
            total += if (file.isDirectory) getDirectorySize(file) else file.length()
        }
        return total
    }

    private fun getFileCount(dir: File?): Int {
        if (dir == null || !dir.exists()) return 0
        var count = 0
        val files = dir.listFiles() ?: return 0
        for (file in files) {
            if (file.isDirectory) {
                count += getFileCount(file)
            } else {
                count++
            }
        }
        return count
    }

    private fun deleteDirectoryContents(dir: File?): Boolean {
        if (dir == null || !dir.exists()) return false
        var success = true
        val files = dir.listFiles() ?: return false
        for (file in files) {
            val deleted = if (file.isDirectory) {
                deleteDirectoryContents(file) && file.delete()
            } else {
                file.delete()
            }
            if (!deleted) success = false
        }
        return success
    }
}

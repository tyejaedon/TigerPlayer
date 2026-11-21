package com.tigerplayer.data.repository

import android.content.Context
import com.tigerplayer.data.local.dao.TigerDao
import com.tigerplayer.data.model.StorageCategory
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class StorageRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context = mockk<Context>(relaxed = true)
    private val tigerDao = mockk<TigerDao>(relaxed = true)
    private val lyricsRepository = mockk<LyricsRepository>(relaxed = true)
    private val mediaDataRepository = mockk<MediaDataRepository>(relaxed = true)

    private lateinit var storageRepository: StorageRepository

    private lateinit var cacheDir: File
    private lateinit var externalCacheDir: File
    private lateinit var filesDir: File
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        cacheDir = tempFolder.newFolder("cache")
        externalCacheDir = tempFolder.newFolder("external_cache")
        filesDir = tempFolder.newFolder("files")
        dbFile = tempFolder.newFile("tiger_database")
        dbFile.writeBytes(ByteArray(1024)) // 1 KB

        every { context.cacheDir } returns cacheDir
        every { context.externalCacheDir } returns externalCacheDir
        every { context.filesDir } returns filesDir
        every { context.getDatabasePath("tiger_database") } returns dbFile
        every { context.getDatabasePath("tiger_database-wal") } returns File(tempFolder.root, "non_existent_wal")
        every { context.getDatabasePath("tiger_database-shm") } returns File(tempFolder.root, "non_existent_shm")

        storageRepository = StorageRepository(
            context = context,
            tigerDao = tigerDao,
            lyricsRepository = lyricsRepository,
            mediaDataRepository = mediaDataRepository
        )
    }

    @Test
    fun getStorageBreakdownComputesAccurateMetrics() = runBlocking {
        // Create image cache files
        val imageCacheDir = File(cacheDir, "image_cache").apply { mkdirs() }
        File(imageCacheDir, "art1.jpg").writeBytes(ByteArray(2048))
        File(imageCacheDir, "art2.jpg").writeBytes(ByteArray(2048))

        coEvery { tigerDao.getLyricsCacheCount() } returns 10
        coEvery { tigerDao.getArtistCacheCount() } returns 5
        coEvery { tigerDao.getWaveformCacheCount() } returns 8

        val breakdown = storageRepository.getStorageBreakdown()

        assertEquals(StorageCategory.ARTWORK, breakdown.artwork.category)
        assertEquals(4096L, breakdown.artwork.sizeBytes)
        assertEquals(2, breakdown.artwork.itemCount)

        assertEquals(StorageCategory.LYRICS, breakdown.lyrics.category)
        assertEquals(10, breakdown.lyrics.itemCount)
        assertEquals(35_000L, breakdown.lyrics.sizeBytes)

        assertEquals(StorageCategory.ARTISTS, breakdown.artists.category)
        assertEquals(5, breakdown.artists.itemCount)
        assertEquals(10_000L, breakdown.artists.sizeBytes)

        assertEquals(StorageCategory.WAVEFORMS, breakdown.waveforms.category)
        assertEquals(8, breakdown.waveforms.itemCount)
        assertEquals(12_000L, breakdown.waveforms.sizeBytes)

        assertTrue(breakdown.internalData.sizeBytes >= 1024L)
        assertTrue(breakdown.totalCacheSizeBytes >= 4096L)
    }

    @Test
    fun clearArtworkCacheDeletesOnlyImageCacheContents() = runBlocking {
        val imageCacheDir = File(cacheDir, "image_cache").apply { mkdirs() }
        val imageFile = File(imageCacheDir, "art.jpg").apply { writeBytes(ByteArray(1024)) }
        val otherCacheFile = File(cacheDir, "other.tmp").apply { writeBytes(ByteArray(512)) }

        val freed = storageRepository.clearArtworkCache()

        assertEquals(1024L, freed)
        assertFalse(imageFile.exists())
        assertTrue(otherCacheFile.exists())
    }

    @Test
    fun clearLyricsCacheDelegatesToLyricsRepository() = runBlocking {
        storageRepository.clearLyricsCache()
        coVerify(exactly = 1) { lyricsRepository.clearLyricsCache() }
    }

    @Test
    fun clearArtistCacheDelegatesToMediaDataRepository() = runBlocking {
        storageRepository.clearArtistCache()
        coVerify(exactly = 1) { mediaDataRepository.clearArtistCache() }
    }

    @Test
    fun clearWaveformCacheDelegatesToTigerDao() = runBlocking {
        storageRepository.clearWaveformCache()
        coVerify(exactly = 1) { tigerDao.clearWaveformCache() }
    }

    @Test
    fun clearAllTemporaryCachesWipesDiskAndTransients() = runBlocking {
        val f1 = File(cacheDir, "temp1.bin").apply { writeBytes(ByteArray(1024)) }
        val f2 = File(externalCacheDir, "temp2.bin").apply { writeBytes(ByteArray(2048)) }

        val freed = storageRepository.clearAllTemporaryCaches()

        assertTrue(freed >= 3072L)
        assertFalse(f1.exists())
        assertFalse(f2.exists())
        coVerify(exactly = 1) { lyricsRepository.clearLyricsCache() }
        coVerify(exactly = 1) { mediaDataRepository.clearArtistCache() }
        coVerify(exactly = 1) { tigerDao.clearWaveformCache() }
    }
}

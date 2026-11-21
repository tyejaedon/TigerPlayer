package com.tigerplayer.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class StorageBreakdownTest {

    @Test
    fun formatBytesReturnsCorrectUnits() {
        assertEquals("0 B", 0L.formatBytes())
        assertEquals("0 B", (-100L).formatBytes())
        assertEquals("500 B", 500L.formatBytes())
        assertEquals("1.0 KB", 1024L.formatBytes())
        assertEquals("1.5 KB", 1536L.formatBytes())
        assertEquals("1.00 MB", (1024L * 1024L).formatBytes())
        assertEquals("15.50 MB", (15.5 * 1024 * 1024).toLong().formatBytes())
        assertEquals("2.00 GB", (2L * 1024 * 1024 * 1024).formatBytes())
    }

    @Test
    fun storageBreakdownComputesPurgeableAndTotalSizes() {
        val breakdown = StorageBreakdown(
            totalAppSizeBytes = 50_000_000L,
            totalCacheSizeBytes = 30_000_000L,
            artwork = StorageCategoryInfo(StorageCategory.ARTWORK, 15_000_000L, 50, true),
            lyrics = StorageCategoryInfo(StorageCategory.LYRICS, 5_000_000L, 1000, true),
            artists = StorageCategoryInfo(StorageCategory.ARTISTS, 2_000_000L, 300, true),
            waveforms = StorageCategoryInfo(StorageCategory.WAVEFORMS, 3_000_000L, 800, true),
            internalData = StorageCategoryInfo(StorageCategory.DATABASE_INTERNAL, 20_000_000L, null, false)
        )

        assertEquals(25_000_000L, breakdown.totalPurgeableBytes)
        assertEquals("23.84 MB", breakdown.formattedPurgeableSize)
        assertEquals("47.68 MB", breakdown.formattedTotalAppSize)
        assertEquals("28.61 MB", breakdown.formattedTotalCacheSize)
    }
}

package com.tigerplayer.data.model

import java.util.Locale

/**
 * Categorization of storage consumers within TigerPlayer.
 */
enum class StorageCategory(val displayName: String) {
    ARTWORK("Artwork & Images"),
    LYRICS("Synchronized Lyrics"),
    ARTISTS("Artist Metadata"),
    WAVEFORMS("Audio Waveforms"),
    DATABASE_INTERNAL("Core Database & Settings")
}

/**
 * Storage metrics for a distinct subsystem category.
 */
data class StorageCategoryInfo(
    val category: StorageCategory,
    val sizeBytes: Long,
    val itemCount: Int? = null,
    val isPurgeable: Boolean = true
) {
    val formattedSize: String
        get() = sizeBytes.formatBytes()
}

/**
 * Aggregate snapshot of TigerPlayer's on-disk storage utilization.
 */
data class StorageBreakdown(
    val totalAppSizeBytes: Long = 0L,
    val totalCacheSizeBytes: Long = 0L,
    val artwork: StorageCategoryInfo = StorageCategoryInfo(StorageCategory.ARTWORK, 0L),
    val lyrics: StorageCategoryInfo = StorageCategoryInfo(StorageCategory.LYRICS, 0L),
    val artists: StorageCategoryInfo = StorageCategoryInfo(StorageCategory.ARTISTS, 0L),
    val waveforms: StorageCategoryInfo = StorageCategoryInfo(StorageCategory.WAVEFORMS, 0L),
    val internalData: StorageCategoryInfo = StorageCategoryInfo(
        category = StorageCategory.DATABASE_INTERNAL,
        sizeBytes = 0L,
        isPurgeable = false
    ),
    val isCalculating: Boolean = false
) {
    val totalPurgeableBytes: Long
        get() = artwork.sizeBytes + lyrics.sizeBytes + artists.sizeBytes + waveforms.sizeBytes

    val formattedTotalAppSize: String
        get() = totalAppSizeBytes.formatBytes()

    val formattedTotalCacheSize: String
        get() = totalCacheSizeBytes.formatBytes()

    val formattedPurgeableSize: String
        get() = totalPurgeableBytes.formatBytes()
}

/**
 * Formats a raw byte count into a human-readable string (e.g. "12.45 MB", "512 KB", "0 B").
 */
fun Long.formatBytes(): String {
    if (this <= 0L) return "0 B"
    val kb = this / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.2f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
        else -> "$this B"
    }
}

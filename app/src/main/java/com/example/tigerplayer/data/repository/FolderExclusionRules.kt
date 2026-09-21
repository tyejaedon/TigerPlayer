package com.tigerplayer.data.repository

/**
 * THE SINGLE SOURCE OF EXCLUSION TRUTH (issue #50)
 *
 * Decides whether a directory - or a track living in one - should be dropped from the library.
 * Kept as a pure, framework-free object (mirroring [LibraryCacheDiffer]) so the same rule can be
 * unit-tested once and reused by both [com.tigerplayer.data.source.SafFolderScanner]
 * (walking custom directories) and [AudioRepository] (filtering MediaStore-derived tracks), which
 * is what makes exclusion consistent across scan, search and playback.
 */
object FolderExclusionRules {

    /**
     * A directory is excluded if it - or any ancestor - is in [excludedPaths], or if it contains
     * a `.nomedia` marker itself. `.nomedia` only suppresses the directory it lives in (and, by
     * the same convention Android's own MediaScanner uses, everything nested inside it).
     */
    fun isDirectoryExcluded(
        directoryPath: String,
        excludedPaths: Set<String>,
        containsNoMedia: Boolean
    ): Boolean {
        if (containsNoMedia) return true
        return isUnderAnyExcludedPath(directoryPath, excludedPaths)
    }

    /** Whether a track living at [trackPath] falls under any excluded folder. */
    fun isTrackExcluded(trackPath: String?, excludedPaths: Set<String>): Boolean {
        if (trackPath.isNullOrBlank() || excludedPaths.isEmpty()) return false
        val directoryPath = trackPath.substringBeforeLast('/', missingDelimiterValue = "")
        return isUnderAnyExcludedPath(directoryPath, excludedPaths)
    }

    private fun isUnderAnyExcludedPath(path: String, excludedPaths: Set<String>): Boolean {
        if (excludedPaths.isEmpty()) return false
        return excludedPaths.any { excluded ->
            excluded.isNotBlank() && (path == excluded || path.startsWith("$excluded/"))
        }
    }
}

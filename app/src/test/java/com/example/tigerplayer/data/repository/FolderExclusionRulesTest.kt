package com.example.tigerplayer.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coverage for issue #50's exclusion rules - the single decision point shared by
 * [com.example.tigerplayer.data.source.SafFolderScanner] (walking custom directories) and
 * [AudioRepository] (filtering MediaStore-derived tracks), so `.nomedia` and the exclude list
 * behave identically across scan, search and playback regardless of which source found a file.
 */
class FolderExclusionRulesTest {

    @Test
    fun `a directory containing a nomedia marker is excluded`() {
        assertTrue(
            FolderExclusionRules.isDirectoryExcluded(
                directoryPath = "/storage/emulated/0/Music/Private",
                excludedPaths = emptySet(),
                containsNoMedia = true
            )
        )
    }

    @Test
    fun `a directory with no marker and no matching excluded path is not excluded`() {
        assertFalse(
            FolderExclusionRules.isDirectoryExcluded(
                directoryPath = "/storage/emulated/0/Music/Tiger",
                excludedPaths = setOf("/storage/emulated/0/Podcasts"),
                containsNoMedia = false
            )
        )
    }

    @Test
    fun `a directory exactly matching an excluded path is excluded`() {
        assertTrue(
            FolderExclusionRules.isDirectoryExcluded(
                directoryPath = "/storage/emulated/0/Podcasts",
                excludedPaths = setOf("/storage/emulated/0/Podcasts"),
                containsNoMedia = false
            )
        )
    }

    @Test
    fun `a directory nested under an excluded path is excluded`() {
        assertTrue(
            FolderExclusionRules.isDirectoryExcluded(
                directoryPath = "/storage/emulated/0/Podcasts/Season1",
                excludedPaths = setOf("/storage/emulated/0/Podcasts"),
                containsNoMedia = false
            )
        )
    }

    @Test
    fun `a directory sharing only a name prefix is not excluded`() {
        // "/storage/emulated/0/Podcasts2" must not match the excluded "/storage/emulated/0/Podcasts"
        // just because it starts with the same characters.
        assertFalse(
            FolderExclusionRules.isDirectoryExcluded(
                directoryPath = "/storage/emulated/0/Podcasts2",
                excludedPaths = setOf("/storage/emulated/0/Podcasts"),
                containsNoMedia = false
            )
        )
    }

    @Test
    fun `a track with a null path is never excluded`() {
        assertFalse(
            FolderExclusionRules.isTrackExcluded(
                trackPath = null,
                excludedPaths = setOf("/storage/emulated/0/Podcasts")
            )
        )
    }

    @Test
    fun `a track living directly inside an excluded folder is excluded`() {
        assertTrue(
            FolderExclusionRules.isTrackExcluded(
                trackPath = "/storage/emulated/0/Podcasts/episode1.mp3",
                excludedPaths = setOf("/storage/emulated/0/Podcasts")
            )
        )
    }

    @Test
    fun `a track living inside a subfolder of an excluded folder is excluded`() {
        assertTrue(
            FolderExclusionRules.isTrackExcluded(
                trackPath = "/storage/emulated/0/Podcasts/Season1/episode1.mp3",
                excludedPaths = setOf("/storage/emulated/0/Podcasts")
            )
        )
    }

    @Test
    fun `a track outside every excluded folder is not excluded`() {
        assertFalse(
            FolderExclusionRules.isTrackExcluded(
                trackPath = "/storage/emulated/0/Music/Tiger/Neon Drift.flac",
                excludedPaths = setOf("/storage/emulated/0/Podcasts")
            )
        )
    }

    @Test
    fun `an empty exclude set never excludes anything`() {
        assertFalse(
            FolderExclusionRules.isTrackExcluded(
                trackPath = "/storage/emulated/0/Music/Tiger/Neon Drift.flac",
                excludedPaths = emptySet()
            )
        )
    }
}

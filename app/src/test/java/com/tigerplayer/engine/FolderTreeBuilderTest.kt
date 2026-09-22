package com.tigerplayer.engine

import android.net.Uri
import com.tigerplayer.data.model.AudioTrack
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coverage for issue #50's folder browser: [FolderTreeBuilder.index] must produce a correct,
 * drill-down-able directory index from a flat track list - including synthetic ancestor folders
 * that hold no tracks of their own - without needing Android APIs to run.
 */
class FolderTreeBuilderTest {

    private fun track(id: String, path: String) = AudioTrack(
        id = id,
        title = "Title $id",
        artist = "Artist",
        album = "Album",
        uri = mockk<Uri>(relaxed = true),
        artworkUri = mockk<Uri>(relaxed = true),
        durationMs = 200_000L,
        mimeType = "audio/flac",
        isLocal = true,
        path = path
    )

    @Test
    fun `an empty track list still produces a browsable empty root`() {
        val index = FolderTreeBuilder.index(emptyList())

        assertEquals(setOf(""), index.keys)
        val root = index.getValue("")
        assertTrue(root.subfolders.isEmpty())
        assertTrue(root.tracks.isEmpty())
    }

    @Test
    fun `tracks with a null or blank path are excluded from every folder`() {
        val index = FolderTreeBuilder.index(listOf(track("a", path = "").copy(path = null)))

        assertEquals(setOf(""), index.keys)
        assertTrue(index.getValue("").tracks.isEmpty())
    }

    @Test
    fun `a track directly under the root is attributed to the root node`() {
        val index = FolderTreeBuilder.index(listOf(track("a", "/song.flac")))

        val root = index.getValue("")
        assertEquals(listOf("a"), root.tracks.map { it.id })
        assertTrue(root.subfolders.isEmpty())
    }

    @Test
    fun `a nested track creates every intermediate ancestor folder`() {
        val index = FolderTreeBuilder.index(
            listOf(track("a", "/storage/emulated/0/Music/Tiger/song.flac"))
        )

        assertEquals(
            setOf("", "/storage", "/storage/emulated", "/storage/emulated/0",
                "/storage/emulated/0/Music", "/storage/emulated/0/Music/Tiger"),
            index.keys
        )

        val leaf = index.getValue("/storage/emulated/0/Music/Tiger")
        assertEquals(listOf("a"), leaf.tracks.map { it.id })
        assertEquals("Tiger", leaf.name)
        assertTrue("the leaf folder has no subfolders of its own", leaf.subfolders.isEmpty())

        val musicFolder = index.getValue("/storage/emulated/0/Music")
        assertEquals(
            "an intermediate folder with no tracks of its own must still be browsable",
            listOf("/storage/emulated/0/Music/Tiger"),
            musicFolder.subfolders
        )
        assertTrue("an intermediate folder holds no tracks directly", musicFolder.tracks.isEmpty())
    }

    @Test
    fun `sibling folders and tracks in the same directory are both surfaced`() {
        val index = FolderTreeBuilder.index(
            listOf(
                track("a", "/storage/emulated/0/Music/song.flac"),
                track("b", "/storage/emulated/0/Music/Tiger/other.flac"),
                track("c", "/storage/emulated/0/Podcasts/episode.flac")
            )
        )

        val root = index.getValue("/storage/emulated/0")
        assertEquals(setOf("/storage/emulated/0/Music", "/storage/emulated/0/Podcasts"), root.subfolders.toSet())

        val musicFolder = index.getValue("/storage/emulated/0/Music")
        assertEquals(listOf("a"), musicFolder.tracks.map { it.id })
        assertEquals(listOf("/storage/emulated/0/Music/Tiger"), musicFolder.subfolders)
    }

    @Test
    fun `subfolders and tracks are each sorted by display name`() {
        val index = FolderTreeBuilder.index(
            listOf(
                track("z", "/storage/emulated/0/Music/zebra.flac"),
                track("a", "/storage/emulated/0/Music/apple.flac"),
                track("in_b", "/storage/emulated/0/Music/Beta/x.flac"),
                track("in_a", "/storage/emulated/0/Music/Alpha/x.flac")
            )
        )

        val musicFolder = index.getValue("/storage/emulated/0/Music")
        assertEquals(listOf("a", "z"), musicFolder.tracks.map { it.id })
        assertEquals(
            listOf("/storage/emulated/0/Music/Alpha", "/storage/emulated/0/Music/Beta"),
            musicFolder.subfolders
        )
    }
}

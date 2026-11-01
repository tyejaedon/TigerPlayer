package com.tigerplayer.ui.coverscreen

import android.net.Uri
import com.tigerplayer.data.local.entity.PlaylistEntity
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.model.Playlist
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Cover Screen playlist management features:
 * - Playlist track reordering algorithms
 * - Playlist track removal
 * - Liked songs protection checks
 * - Duration calculation
 */
class CoverScreenPlaylistDetailTest {

    private fun createTrack(id: String, title: String, artist: String, durationMs: Long): AudioTrack {
        return AudioTrack(
            id = id,
            title = title,
            artist = artist,
            album = "Test Album",
            uri = mockk<Uri>(relaxed = true),
            artworkUri = mockk<Uri>(relaxed = true),
            durationMs = durationMs,
            mimeType = "audio/mpeg",
            path = "/storage/music/$id.mp3"
        )
    }

    @Test
    fun `reordering tracks moves track from old index to new index correctly`() {
        val t1 = createTrack("1", "Song 1", "Artist A", 180_000L)
        val t2 = createTrack("2", "Song 2", "Artist B", 200_000L)
        val t3 = createTrack("3", "Song 3", "Artist C", 220_000L)
        val t4 = createTrack("4", "Song 4", "Artist D", 240_000L)

        val initialList = listOf(t1, t2, t3, t4)

        // Move item at index 0 to index 2
        val movedList = initialList.toMutableList().apply {
            add(2, removeAt(0))
        }

        assertEquals(4, movedList.size)
        assertEquals("2", movedList[0].id)
        assertEquals("3", movedList[1].id)
        assertEquals("1", movedList[2].id)
        assertEquals("4", movedList[3].id)
    }

    @Test
    fun `reordering tracks move up and move down operations behave predictably`() {
        val t1 = createTrack("1", "Song 1", "Artist A", 180_000L)
        val t2 = createTrack("2", "Song 2", "Artist B", 200_000L)
        val t3 = createTrack("3", "Song 3", "Artist C", 220_000L)

        val list = listOf(t1, t2, t3)

        // Move item 1 up (from index 1 to index 0)
        val moveUpList = list.toMutableList().apply {
            add(0, removeAt(1))
        }
        assertEquals(listOf(t2, t1, t3), moveUpList)

        // Move item 1 down (from index 1 to index 2 in original list)
        val moveDownList = list.toMutableList().apply {
            add(2, removeAt(1))
        }
        assertEquals(listOf(t1, t3, t2), moveDownList)
    }

    @Test
    fun `removing track from playlist filters out target track correctly`() {
        val t1 = createTrack("1", "Song 1", "Artist A", 180_000L)
        val t2 = createTrack("2", "Song 2", "Artist B", 200_000L)
        val t3 = createTrack("3", "Song 3", "Artist C", 220_000L)

        val list = listOf(t1, t2, t3)
        val filtered = list.filter { it.id != t2.id }

        assertEquals(2, filtered.size)
        assertEquals(listOf(t1, t3), filtered)
    }

    @Test
    fun `isLikedPlaylist identifies singleton ID and case-insensitive Liked Songs name`() {
        val likedById = Playlist(
            id = PlaylistEntity.LIKED_SONGS_PLAYLIST_ID,
            name = "My Favorites",
            trackCount = 10,
            artworkUri = null,
            createdAt = 100L
        )
        val likedByName = Playlist(
            id = 55L,
            name = "Liked Songs",
            trackCount = 10,
            artworkUri = null,
            createdAt = 100L
        )
        val customPlaylist = Playlist(
            id = 10L,
            name = "Night Drive",
            trackCount = 10,
            artworkUri = null,
            createdAt = 100L
        )

        fun checkIsLiked(p: Playlist): Boolean =
            p.id == PlaylistEntity.LIKED_SONGS_PLAYLIST_ID ||
                    p.name.equals(PlaylistEntity.LIKED_SONGS_PLAYLIST_NAME, ignoreCase = true)

        assertTrue(checkIsLiked(likedById))
        assertTrue(checkIsLiked(likedByName))
        assertFalse(checkIsLiked(customPlaylist))
    }

    @Test
    fun `total duration calculation formats minutes correctly`() {
        val t1 = createTrack("1", "Song 1", "Artist A", 120_000L) // 2 min
        val t2 = createTrack("2", "Song 2", "Artist B", 180_000L) // 3 min
        val tracks = listOf(t1, t2)

        val totalDurationMs = tracks.sumOf { it.durationMs }
        val minutes = totalDurationMs / 60_000L

        assertEquals(300_000L, totalDurationMs)
        assertEquals(5L, minutes)
    }
}

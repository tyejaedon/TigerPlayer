package com.tigerplayer.ui.coverscreen

import com.tigerplayer.data.local.entity.PlaylistEntity
import com.tigerplayer.data.model.Playlist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying playlist sorting and tag generation for the 3D wheel
 * and cover screen playlist picker in [LocalPlaylistPicker].
 */
class CoverScreenPlaylistWheelTest {

    @Test
    fun `sortCoverScreenPlaylists puts Liked Songs with singleton ID first`() {
        val playlists = listOf(
            Playlist(id = 5L, name = "Acoustic Sessions", trackCount = 10, artworkUri = null, createdAt = 1000L),
            Playlist(id = PlaylistEntity.LIKED_SONGS_PLAYLIST_ID, name = "Liked Songs", trackCount = 42, artworkUri = null, createdAt = 500L),
            Playlist(id = 2L, name = "Cyberpunk Beats", trackCount = 15, artworkUri = null, createdAt = 2000L),
            Playlist(id = 3L, name = "Ambient Void", trackCount = 8, artworkUri = null, createdAt = 3000L)
        )

        val sorted = sortCoverScreenPlaylists(playlists)

        assertEquals(4, sorted.size)
        assertEquals(PlaylistEntity.LIKED_SONGS_PLAYLIST_ID, sorted[0].id)
        assertEquals("Liked Songs", sorted[0].name)
        assertEquals("Acoustic Sessions", sorted[1].name)
        assertEquals("Ambient Void", sorted[2].name)
        assertEquals("Cyberpunk Beats", sorted[3].name)
    }

    @Test
    fun `sortCoverScreenPlaylists puts legacy Liked Songs by name first when ID is arbitrary`() {
        val playlists = listOf(
            Playlist(id = 12L, name = "Synthwave Neon", trackCount = 20, artworkUri = null, createdAt = 1000L),
            Playlist(id = 99L, name = "liked songs", trackCount = 5, artworkUri = null, createdAt = 1200L),
            Playlist(id = 4L, name = "Chill Lofi", trackCount = 12, artworkUri = null, createdAt = 800L)
        )

        val sorted = sortCoverScreenPlaylists(playlists)

        assertEquals(3, sorted.size)
        assertEquals(99L, sorted[0].id)
        assertEquals("liked songs", sorted[0].name)
        assertEquals("Chill Lofi", sorted[1].name)
        assertEquals("Synthwave Neon", sorted[2].name)
    }

    @Test
    fun `sortCoverScreenPlaylists sorts regular playlists alphabetically case-insensitively`() {
        val playlists = listOf(
            Playlist(id = 1L, name = "zen meditation", trackCount = 3, artworkUri = null, createdAt = 100L),
            Playlist(id = 2L, name = "Workout Heavy", trackCount = 10, artworkUri = null, createdAt = 200L),
            Playlist(id = 3L, name = "acoustic morning", trackCount = 7, artworkUri = null, createdAt = 300L),
            Playlist(id = 4L, name = "Baroque Masterpieces", trackCount = 14, artworkUri = null, createdAt = 400L)
        )

        val sorted = sortCoverScreenPlaylists(playlists)

        assertEquals("acoustic morning", sorted[0].name)
        assertEquals("Baroque Masterpieces", sorted[1].name)
        assertEquals("Workout Heavy", sorted[2].name)
        assertEquals("zen meditation", sorted[3].name)
    }

    @Test
    fun `sortCoverScreenPlaylists handles empty list`() {
        val sorted = sortCoverScreenPlaylists(emptyList())
        assertTrue(sorted.isEmpty())
    }

    @Test
    fun `test tags generated properly for playlist carousel, slider, and edit photo`() {
        assertEquals("cover_screen_playlist_carousel", CoverScreenTestTags.PLAYLIST_CAROUSEL)
        assertEquals("cover_screen_playlist_slider", CoverScreenTestTags.PLAYLIST_SLIDER)
        assertEquals("cover_screen_local_playlist_42", CoverScreenTestTags.localPlaylist(42L))
        assertEquals("cover_screen_edit_playlist_photo_42", CoverScreenTestTags.editPlaylistPhoto(42L))
        assertEquals("cover_screen_import_playlist", CoverScreenTestTags.IMPORT_PLAYLIST)
        assertEquals("cover_screen_import_status_overlay", CoverScreenTestTags.IMPORT_STATUS_OVERLAY)
    }
}

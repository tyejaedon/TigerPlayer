package com.tigerplayer.ui.coverscreen

/**
 * Centralized Compose test tags for the cover-screen mini hub, following the [PrismTestTags]
 * convention: tests address nodes by tag rather than by localized display strings.
 */
object CoverScreenTestTags {
    /** Root node of [CoverScreenMiniHub]; also applied by [MainScreen] where it is mounted. */
    const val MINI_HUB_ROOT = "cover_screen_mini_hub_root"
    const val ARTISTS_GRID = "cover_screen_artists_grid"
    const val ARTISTS_EMPTY_STATE = "cover_screen_artists_empty_state"
    const val ARTIST_BACK = "cover_screen_artist_back"

    fun artist(id: String): String = "cover_screen_artist_$id"
    fun album(id: String): String = "cover_screen_album_$id"
}

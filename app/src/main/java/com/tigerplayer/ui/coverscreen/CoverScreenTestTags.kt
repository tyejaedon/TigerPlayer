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
    const val ALBUM_TRACKS = "cover_screen_album_tracks"
    const val ALBUM_BACK = "cover_screen_album_back"
    const val LIBRARY_MENU = "cover_screen_library_menu"
    const val LIBRARY_OPEN = "cover_screen_library_open"
    const val LIBRARY_BACK = "cover_screen_library_back"
    const val LIBRARY_TABS = "cover_screen_library_tabs"
    const val LIBRARY_CONTENT = "cover_screen_library_content"
    const val LOCAL_SOURCE = "cover_screen_local_source"
    const val SPOTIFY_SOURCE = "cover_screen_spotify_source"
    const val LIBRARY_SPOTIFY_AUTH_REQUIRED = "cover_screen_spotify_auth_required"
    const val SEARCH_OPEN = "cover_screen_search_open"
    const val SEARCH_FIELD = "cover_screen_search_field"
    const val SEARCH_VOICE = "cover_screen_search_voice"
    const val SEARCH_CLOSE = "cover_screen_search_close"
    const val SEARCH_VOICE_STATUS = "cover_screen_search_voice_status"

    fun artist(id: String): String = "cover_screen_artist_$id"
    fun album(id: String): String = "cover_screen_album_$id"
    fun track(id: String): String = "cover_screen_track_$id"
    fun queueTrack(id: String): String = "cover_screen_queue_track_$id"
    fun removeTrack(id: String): String = "cover_screen_remove_track_$id"
    fun libraryTab(tab: String): String = "cover_screen_library_tab_$tab"
    fun libraryAlbum(id: String): String = "cover_screen_library_album_$id"
    fun localPlaylist(id: Long): String = "cover_screen_local_playlist_$id"
    fun spotifyPlaylist(id: String): String = "cover_screen_spotify_playlist_$id"
}

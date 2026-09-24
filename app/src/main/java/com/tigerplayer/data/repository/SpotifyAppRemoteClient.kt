package com.tigerplayer.data.repository

/**
 * Snapshot of Spotify App Remote playback state, decoupled from the proprietary SDK's own
 * `PlayerState` type so common code never needs to import it.
 */
data class SpotifyRemotePlayerState(
    val trackUri: String,
    val trackName: String,
    val artistName: String,
    val albumName: String,
    val durationMs: Long,
    val positionMs: Long,
    val isPaused: Boolean,
    val isShuffling: Boolean
)

/**
 * Abstraction over the proprietary Spotify App Remote SDK, used by [SpotifyRepository].
 *
 * The vendored `spotify-app-remote` AAR ([libs/spotify-app-remote-release-0.8.0.aar]) is a
 * proprietary binary that F-Droid will not accept. To keep the `foss` flavor distributable:
 *
 * - `src/full/.../SpotifyAppRemoteClientImpl.kt` backs this interface with the real SDK.
 * - `src/foss/.../SpotifyAppRemoteClientImpl.kt` provides a no-op stub.
 *
 * Common code (this file, [SpotifyRepository]) never imports `com.spotify.android.appremote.*`
 * directly, so it compiles identically in both flavors.
 */
interface SpotifyAppRemoteClient {

    /** `false` in the `foss` flavor; callers/UI should hide App Remote controls when this is false. */
    val isSupported: Boolean

    fun connect(
        clientId: String,
        redirectUri: String,
        onConnectionChanged: (Boolean) -> Unit,
        onPlayerStateChanged: (SpotifyRemotePlayerState?) -> Unit,
        onConnectionFailed: (Throwable) -> Unit
    )

    fun disconnect()
    fun play(uri: String)
    fun pause()
    fun resume()
    fun skipNext()
    fun skipPrevious()
    fun seekTo(positionMs: Long)
    fun toggleShuffle()
    fun toggleRepeat()
}

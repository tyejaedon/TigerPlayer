package com.tigerplayer.data.repository

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `foss`-flavor stub. The proprietary Spotify App Remote SDK is not on this flavor's
 * classpath (see `app/build.gradle.kts`), so this implementation is a deliberate no-op rather
 * than a crash. UI should check [isSupported] to hide App Remote-dependent controls.
 */
@Singleton
class SpotifyAppRemoteClientImpl @Inject constructor() : SpotifyAppRemoteClient {

    private val tag = "SpotifyAppRemote"

    override val isSupported: Boolean = false

    override fun connect(
        clientId: String,
        redirectUri: String,
        onConnectionChanged: (Boolean) -> Unit,
        onPlayerStateChanged: (SpotifyRemotePlayerState?) -> Unit,
        onConnectionFailed: (Throwable) -> Unit
    ) {
        Log.w(tag, "Spotify App Remote is not available in this build; ignoring connect().")
        onConnectionChanged(false)
        // Report the failure too, so a caller that isn't gating on isSupported can't be left
        // waiting on a callback that will never arrive (issue #170).
        onConnectionFailed(
            UnsupportedOperationException("Spotify App Remote is unavailable in the foss flavor.")
        )
    }

    override fun disconnect() = Unit
    override fun play(uri: String) = Unit
    override fun pause() = Unit
    override fun resume() = Unit
    override fun skipNext() = Unit
    override fun skipPrevious() = Unit
    override fun seekTo(positionMs: Long) = Unit
    override fun toggleShuffle() = Unit
    override fun toggleRepeat() = Unit
}


package com.example.tigerplayer.data.repository

import android.content.Context
import android.util.Log
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `full`-flavor implementation backed by the real (proprietary, vendored) Spotify App Remote
 * SDK. See [SpotifyAppRemoteClient] for why this lives behind an interface.
 */
@Singleton
class SpotifyAppRemoteClientImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : SpotifyAppRemoteClient {

    private val tag = "SpotifyAppRemote"

    override val isSupported: Boolean = true

    private var remote: SpotifyAppRemote? = null

    override fun connect(
        clientId: String,
        redirectUri: String,
        onConnectionChanged: (Boolean) -> Unit,
        onPlayerStateChanged: (SpotifyRemotePlayerState?) -> Unit,
        onConnectionFailed: (Throwable) -> Unit
    ) {
        val connectionParams = ConnectionParams.Builder(clientId)
            .setRedirectUri(redirectUri)
            .showAuthView(true)
            .build()

        try {
            SpotifyAppRemote.connect(context, connectionParams, object : Connector.ConnectionListener {
                override fun onConnected(appRemote: SpotifyAppRemote) {
                    remote = appRemote
                    onConnectionChanged(true)
                    subscribeToPlayerState(onPlayerStateChanged)
                }

                override fun onFailure(throwable: Throwable) {
                    Log.e(tag, "App Remote connection failed", throwable)
                    onConnectionChanged(false)
                    onConnectionFailed(throwable)
                }
            })
        } catch (e: Exception) {
            Log.e(tag, "IPC Binder Exception during connect", e)
            onConnectionFailed(e)
        }
    }

    override fun disconnect() {
        try {
            remote?.let { SpotifyAppRemote.disconnect(it) }
        } catch (e: IllegalArgumentException) {
            // Android occasionally unbinds the service before we can disconnect cleanly.
            Log.w(tag, "App Remote was already unbound.")
        } finally {
            remote = null
            Log.d(tag, "Disconnected from Spotify App Remote.")
        }
    }

    override fun play(uri: String) {
        remote?.playerApi?.play(uri)
    }

    override fun pause() {
        remote?.playerApi?.pause()
    }

    override fun resume() {
        remote?.playerApi?.resume()
    }

    override fun skipNext() {
        remote?.playerApi?.skipNext()
    }

    override fun skipPrevious() {
        remote?.playerApi?.skipPrevious()
    }

    override fun seekTo(positionMs: Long) {
        remote?.playerApi?.seekTo(positionMs)
    }

    override fun toggleShuffle() {
        remote?.playerApi?.toggleShuffle()
    }

    override fun toggleRepeat() {
        remote?.playerApi?.toggleRepeat()
    }

    private fun subscribeToPlayerState(onPlayerStateChanged: (SpotifyRemotePlayerState?) -> Unit) {
        try {
            remote?.playerApi?.subscribeToPlayerState()?.setEventCallback { playerState ->
                val track = playerState.track
                if (track == null) {
                    onPlayerStateChanged(null)
                    return@setEventCallback
                }

                onPlayerStateChanged(
                    SpotifyRemotePlayerState(
                        trackUri = track.uri,
                        trackName = track.name,
                        artistName = track.artist.name,
                        durationMs = track.duration,
                        positionMs = playerState.playbackPosition,
                        isPaused = playerState.isPaused,
                        isShuffling = extractShuffleEnabled(playerState)
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to subscribe to player state", e)
        }
    }

    // App Remote versions differ in typed accessors; reflection keeps this resilient.
    private fun extractShuffleEnabled(playerState: Any): Boolean {
        val playbackOptions = runCatching {
            playerState.javaClass.getMethod("getPlaybackOptions").invoke(playerState)
        }.getOrNull() ?: return false

        return runCatching {
            playbackOptions.javaClass.getMethod("isShuffling").invoke(playbackOptions) as? Boolean
        }.getOrNull() ?: false
    }
}


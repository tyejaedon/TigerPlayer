package com.tigerplayer.engine

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresExtension
import com.tigerplayer.data.local.NavidromePrefs
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.repository.AudioRepository
import com.tigerplayer.data.repository.NavidromeRepository
import com.tigerplayer.data.repository.SpotifyAuthManager
import com.tigerplayer.data.source.LocalAudioDataSource
import com.tigerplayer.di.SubsonicHostManager
import com.tigerplayer.utils.NavidromeMapper.toAudioTrack
import kotlinx.coroutines.flow.*
import javax.inject.Inject

/**
 * Thrown by [NetworkEngine.connectToNavidrome] when the resolved server URL is unencrypted
 * (`http://`) and the caller has not explicitly set `allowCleartext = true`. The UI layer is
 * expected to catch this, surface a warning to the user, and retry with acknowledgement -
 * this is the actual per-connection scoping for cleartext traffic (issue #47); the static
 * network security config cannot know the user's server address ahead of time.
 */
class CleartextNotAcknowledgedException(val url: String) :
    Exception("Server address '$url' uses an unencrypted (http://) connection.")

class NetworkEngine @Inject constructor(
    private val navidromePrefs: NavidromePrefs,
    private val hostManager: SubsonicHostManager,
    private val navidromeRepository: NavidromeRepository,
    private val audioRepository: AudioRepository,
    private val authManager: SpotifyAuthManager
) {
    // Exposes remote tracks to be observed by the ViewModel
    private val _remoteTracks = MutableStateFlow<List<AudioTrack>>(emptyList())

    /**
     * 1. THE AUTO RITUAL
     * Emits a boolean true only if valid credentials exist and the server ping succeeds.
     */
    val autoConnectEvent: Flow<Boolean> = combine(
        navidromePrefs.serverUrl,
        navidromePrefs.username,
        navidromePrefs.password
    ) { url, user, pass ->
        if (!url.isNullOrBlank() && !user.isNullOrBlank() && !pass.isNullOrBlank()) {
            val finalUrl = url.ensureValidUrl()
            hostManager.currentBaseUrl = finalUrl
            navidromeRepository.pingServer(user, pass).isSuccess
        } else {
            false
        }
    }

    /**
     * 2. SYNC ARCHIVES
     * Fetches all remote tracks from Navidrome and maps them to AudioTracks.
     */
    suspend fun syncNavidromeArchives() {
        val user = navidromePrefs.username.firstOrNull()
        val pass = navidromePrefs.password.firstOrNull()

        if (user.isNullOrBlank() || pass.isNullOrBlank()) {
            _remoteTracks.value = emptyList()
            return
        }

        navidromeRepository.getAllRemoteTracks(user, pass).onSuccess { remoteList ->
            // Mapping carries no credentials; URIs are signed at request time (issue #44).
            _remoteTracks.value = remoteList.map { it.toAudioTrack() }
        }.onFailure { error ->
            Log.e("NetworkEngine", "Failed to sync Navidrome: ${error.message}")
        }
    }

    /**
     * 3. CONNECT TO NAVIDROME
     * Validates credentials and returns a Kotlin Result so the ViewModel can handle UI success/error.
     *
     * [allowCleartext] must be explicitly set by the caller (after user acknowledgement) before
     * an `http://` server address is permitted; otherwise this fails with
     * [CleartextNotAcknowledgedException] rather than silently sending credentials in the clear.
     */
    suspend fun connectToNavidrome(
        url: String,
        user: String,
        pass: String,
        allowCleartext: Boolean = false
    ): Result<Unit> {
        val finalUrl = url.ensureValidUrl()

        if (finalUrl.startsWith("http://") && !allowCleartext) {
            return Result.failure(CleartextNotAcknowledgedException(finalUrl))
        }

        hostManager.currentBaseUrl = finalUrl

        return navidromeRepository.pingServer(user, pass).map {
            navidromePrefs.saveCredentials(finalUrl, user, pass)
        }
    }

    /**
     * 4. REFRESH LIBRARY
     * Exposes the flow of unified tracks (local + remote) based on current credentials.
     */
    suspend fun getUnifiedLibraryFlow(): Flow<List<AudioTrack>> {
        val user = navidromePrefs.username.firstOrNull()
        val pass = navidromePrefs.password.firstOrNull()
        val url = navidromePrefs.serverUrl.firstOrNull()

        url?.let { hostManager.currentBaseUrl = it }

        return audioRepository.getUnifiedTracks(user, pass, url)
    }

    /**
     * 6. SPOTIFY AUTH
     *
     * With the Authorization Code + PKCE flow, [SpotifyAuthManager.exchangeCodeForToken] already
     * persists the freshly exchanged token before this hook fires (issue #46 migration). This
     * remains as an explicit acknowledgement point for callers rather than re-deriving token
     * state here.
     */
    fun onAuthSuccess(newToken: String) {
        val persisted = authManager.getToken() == newToken
        Log.d("NetworkEngine", "Spotify auth success acknowledged (matches persisted token=$persisted).")
    }

    // --- UTILS ---

    private fun String.ensureValidUrl(): String {
        val clean = when {
            startsWith("http://") || startsWith("https://") -> this
            else -> "https://$this"
        }
        return if (clean.endsWith("/")) clean else "$clean/"
    }
}

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
 *
 * @property url The unencrypted server URL that triggered the exception.
 */
class CleartextNotAcknowledgedException(val url: String) :
    Exception("Server address '$url' uses an unencrypted (http://) connection.")

/**
 * Orchestrates network connections, Navidrome server authentication, archive synchronization,
 * and unified library flow generation.
 */
class NetworkEngine @Inject constructor(
    private val navidromePrefs: NavidromePrefs,
    private val hostManager: SubsonicHostManager,
    private val navidromeRepository: NavidromeRepository,
    private val audioRepository: AudioRepository,
    private val authManager: SpotifyAuthManager
) {
    private val _remoteTracks = MutableStateFlow<List<AudioTrack>>(emptyList())

    /** Emits a boolean true only if valid Navidrome credentials exist and server ping succeeds. */
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

    /** Fetches all remote tracks from Navidrome and maps them to [AudioTrack] domain models. */
    suspend fun syncNavidromeArchives() {
        val user = navidromePrefs.username.firstOrNull()
        val pass = navidromePrefs.password.firstOrNull()

        if (user.isNullOrBlank() || pass.isNullOrBlank()) {
            _remoteTracks.value = emptyList()
            return
        }

        navidromeRepository.getAllRemoteTracks(user, pass).onSuccess { remoteList ->
            _remoteTracks.value = remoteList.map { it.toAudioTrack() }
        }.onFailure { error ->
            Log.e("NetworkEngine", "Failed to sync Navidrome: ${error.message}")
        }
    }

    /**
     * Validates Navidrome credentials and persists them upon success.
     *
     * @param url Server base URL.
     * @param user Username.
     * @param pass Password.
     * @param allowCleartext Whether to allow unencrypted http:// connections without prompting.
     * @return A [Result] indicating success or failure (such as [CleartextNotAcknowledgedException]).
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

    /** Returns the unified library flow (local + remote tracks). */
    suspend fun getUnifiedLibraryFlow(): Flow<List<AudioTrack>> {
        val user = navidromePrefs.username.firstOrNull()
        val pass = navidromePrefs.password.firstOrNull()
        val url = navidromePrefs.serverUrl.firstOrNull()

        url?.let { hostManager.currentBaseUrl = it }

        return audioRepository.getUnifiedTracks(user, pass, url)
    }

    /** Acknowledges successful Spotify authentication. */
    fun onAuthSuccess(newToken: String) {
        val persisted = authManager.getToken() == newToken
        Log.d("NetworkEngine", "Spotify auth success acknowledged (matches persisted token=$persisted).")
    }

    /** Ensures URL has protocol prefix and trailing slash. */
    private fun String.ensureValidUrl(): String {
        val clean = when {
            startsWith("http://") || startsWith("https://") -> this
            else -> "https://$this"
        }
        return if (clean.endsWith("/")) clean else "$clean/"
    }
}

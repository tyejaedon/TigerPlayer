package com.example.tigerplayer.engine

import android.util.Log
import com.example.tigerplayer.data.local.NavidromePrefs
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.repository.AudioRepository
import com.example.tigerplayer.data.repository.NavidromeRepository
import com.example.tigerplayer.data.repository.SpotifyAuthManager
import com.example.tigerplayer.data.source.LocalAudioDataSource
import com.example.tigerplayer.di.SubsonicHostManager
import com.example.tigerplayer.utils.NavidromeMapper.toAudioTrack
import kotlinx.coroutines.flow.*
import javax.inject.Inject

class NetworkEngine @Inject constructor(
    private val navidromePrefs: NavidromePrefs,
    private val hostManager: SubsonicHostManager,
    private val navidromeRepository: NavidromeRepository,
    private val audioRepository: AudioRepository,
    private val authManager: SpotifyAuthManager
) {
    // Exposes remote tracks to be observed by the ViewModel
    private val _remoteTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    val remoteTracks: StateFlow<List<AudioTrack>> = _remoteTracks.asStateFlow()

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
        val url = navidromePrefs.serverUrl.firstOrNull()
        val user = navidromePrefs.username.firstOrNull()
        val pass = navidromePrefs.password.firstOrNull()

        if (url.isNullOrBlank() || user.isNullOrBlank() || pass.isNullOrBlank()) {
            _remoteTracks.value = emptyList()
            return
        }

        navidromeRepository.getAllRemoteTracks(user, pass).onSuccess { remoteList ->
            _remoteTracks.value = remoteList.map { it.toAudioTrack(url, user, pass) }
        }.onFailure { error ->
            Log.e("NetworkEngine", "Failed to sync Navidrome: ${error.message}")
        }
    }

    /**
     * 3. CONNECT TO NAVIDROME
     * Validates credentials and returns a Kotlin Result so the ViewModel can handle UI success/error.
     */
    suspend fun connectToNavidrome(url: String, user: String, pass: String): Result<Unit> {
        val finalUrl = url.ensureValidUrl()
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
     * 5. LOAD LOCAL AUDIO
     * Passes through the scan status flow so the ViewModel can update UI progress bars.
     */
    fun getLocalAudioScanFlow(forceRefresh: Boolean = false): Flow<LocalAudioDataSource.ScanStatus> {
        return audioRepository.getLocalTracksWithProgress(forceRefresh)
    }

    /**
     * 6. SPOTIFY AUTH
     */
    fun onAuthSuccess(newToken: String) {
        authManager.updateToken(newToken)
    }

    // --- UTILS ---

    private fun String.ensureValidUrl(): String {
        val clean = if (startsWith("http")) this else "http://$this"
        return if (clean.endsWith("/")) clean else "$clean/"
    }
}
package com.tigerplayer.data.repository

import android.util.Log
import com.google.gson.Gson
import com.tigerplayer.BuildConfig
import com.tigerplayer.data.local.LastfmPrefs
import com.tigerplayer.data.local.entity.PendingScrobbleEntity
import com.tigerplayer.data.remote.api.LastFmApi
import com.tigerplayer.data.remote.api.LastfmSignature
import com.tigerplayer.data.remote.model.LastFmErrorResponse
import com.tigerplayer.di.IoDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ScrobbleOutcome {
    data object Success : ScrobbleOutcome
    data class TransientFailure(val reason: String) : ScrobbleOutcome
    data class AuthInvalid(val reason: String) : ScrobbleOutcome
    data class PermanentFailure(val reason: String) : ScrobbleOutcome
}

@Singleton
class LastFmRepository(
    private val lastFmApi: LastFmApi,
    private val lastfmPrefs: LastfmPrefs,
    private val ioDispatcher: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO,
    private val apiKey: String = BuildConfig.LASTFM_API_KEY,
    private val apiSecret: String = BuildConfig.LASTFM_API_SECRET
) {
    @Inject
    constructor(
        lastFmApi: LastFmApi,
        lastfmPrefs: LastfmPrefs,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ) : this(
        lastFmApi = lastFmApi,
        lastfmPrefs = lastfmPrefs,
        ioDispatcher = ioDispatcher,
        apiKey = BuildConfig.LASTFM_API_KEY,
        apiSecret = BuildConfig.LASTFM_API_SECRET
    )
    private val gson = Gson()

    val isConnected: StateFlow<Boolean> = lastfmPrefs.isConnected
    val username: StateFlow<String?> = lastfmPrefs.username

    fun isConfigured(): Boolean {
        return apiKey.isNotBlank() &&
            apiKey != "MISSING_API_KEY" &&
            apiSecret.isNotBlank() &&
            apiSecret != "MISSING_API_SECRET"
    }

    fun buildAuthUrl(): String {
        val encodedKey = URLEncoder.encode(apiKey, Charsets.UTF_8.name())
        val encodedCallback = URLEncoder.encode(CALLBACK_URI, Charsets.UTF_8.name())
        return "https://www.last.fm/api/auth/?api_key=$encodedKey&cb=$encodedCallback"
    }

    suspend fun completeAuth(token: String): Result<String> = withContext(ioDispatcher) {
        if (!isConfigured()) {
            return@withContext Result.failure(IllegalStateException("Last.fm API credentials are not configured."))
        }
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Missing Last.fm auth token."))
        }

        try {
            val sigParams = mapOf(
                "api_key" to apiKey,
                "method" to "auth.getSession",
                "token" to cleanToken
            )
            val apiSig = LastfmSignature.sign(sigParams, apiSecret)
            val response = lastFmApi.getSession(
                token = cleanToken,
                apiKey = apiKey,
                apiSig = apiSig
            )

            if (!response.isSuccessful) {
                val errorBody = parseError(response)
                return@withContext Result.failure(
                    IllegalStateException(errorBody?.message ?: "Last.fm auth failed (${response.code()})")
                )
            }

            val session = response.body()?.session
            val sessionKey = session?.key?.trim().orEmpty()
            val user = session?.name?.trim().orEmpty()
            if (sessionKey.isEmpty() || user.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Last.fm returned an incomplete session."))
            }

            lastfmPrefs.saveSession(sessionKey = sessionKey, username = user)
            Result.success(user)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Last.fm auth exchange failed: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun logout() {
        lastfmPrefs.clearSession()
    }

    suspend fun updateNowPlaying(
        artist: String,
        track: String,
        album: String?,
        durationSec: Int
    ): ScrobbleOutcome = withContext(ioDispatcher) {
        if (!isConfigured()) return@withContext ScrobbleOutcome.PermanentFailure("Last.fm not configured")
        val sessionKey = lastfmPrefs.sessionKeySnapshot()
            ?: return@withContext ScrobbleOutcome.AuthInvalid("Not authenticated")

        val baseParams = linkedMapOf(
            "method" to "track.updateNowPlaying",
            "api_key" to apiKey,
            "sk" to sessionKey,
            "artist" to artist,
            "track" to track
        ).apply {
            if (!album.isNullOrBlank()) put("album", album)
            if (durationSec > 0) put("duration", durationSec.toString())
        }

        val signed = LastfmSignature.signedParams(baseParams, apiSecret)
        try {
            val response = lastFmApi.updateNowPlaying(signed)
            mapResponseOutcome(response)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            ScrobbleOutcome.TransientFailure(e.message ?: "Network error")
        } catch (e: Exception) {
            Log.w(TAG, "Now Playing update failed: ${e.message}")
            ScrobbleOutcome.TransientFailure(e.message ?: "Unexpected error")
        }
    }

    suspend fun scrobbleBatch(items: List<PendingScrobbleEntity>): ScrobbleOutcome = withContext(ioDispatcher) {
        if (items.isEmpty()) return@withContext ScrobbleOutcome.Success
        if (!isConfigured()) return@withContext ScrobbleOutcome.PermanentFailure("Last.fm not configured")
        val sessionKey = lastfmPrefs.sessionKeySnapshot()
            ?: return@withContext ScrobbleOutcome.AuthInvalid("Not authenticated")

        val batch = items.take(MAX_BATCH_SIZE)
        val baseParams = linkedMapOf(
            "method" to "track.scrobble",
            "api_key" to apiKey,
            "sk" to sessionKey
        )

        batch.forEachIndexed { index, item ->
            baseParams["artist[$index]"] = item.artist
            baseParams["track[$index]"] = item.track
            baseParams["timestamp[$index]"] = item.timestampSec.toString()
            if (!item.album.isNullOrBlank()) {
                baseParams["album[$index]"] = item.album
            }
            if (item.durationSec > 0) {
                baseParams["duration[$index]"] = item.durationSec.toString()
            }
        }

        val signed = LastfmSignature.signedParams(baseParams, apiSecret)
        try {
            val response = lastFmApi.scrobble(signed)
            mapResponseOutcome(response)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            ScrobbleOutcome.TransientFailure(e.message ?: "Network error")
        } catch (e: Exception) {
            Log.w(TAG, "Scrobble batch failed: ${e.message}")
            ScrobbleOutcome.TransientFailure(e.message ?: "Unexpected error")
        }
    }

    private suspend fun <T> mapResponseOutcome(response: Response<T>): ScrobbleOutcome {
        if (response.isSuccessful) {
            return ScrobbleOutcome.Success
        }
        val err = parseError(response)
        val code = err?.error
        val message = err?.message ?: "HTTP ${response.code()}"
        return when (code) {
            ERROR_INVALID_SESSION -> {
                lastfmPrefs.clearSession()
                ScrobbleOutcome.AuthInvalid(message)
            }
            ERROR_SERVICE_OFFLINE, ERROR_TEMP_UNAVAILABLE, ERROR_RATE_LIMIT -> {
                ScrobbleOutcome.TransientFailure(message)
            }
            ERROR_INVALID_PARAMS, ERROR_INVALID_API_KEY, ERROR_SUSPENDED_KEY -> {
                ScrobbleOutcome.PermanentFailure(message)
            }
            else -> {
                if (response.code() >= 500 || response.code() == 429) {
                    ScrobbleOutcome.TransientFailure(message)
                } else {
                    ScrobbleOutcome.PermanentFailure(message)
                }
            }
        }
    }

    private fun <T> parseError(response: Response<T>): LastFmErrorResponse? {
        val raw = response.errorBody()?.string() ?: return null
        return runCatching { gson.fromJson(raw, LastFmErrorResponse::class.java) }.getOrNull()
    }

    suspend fun getSimilarArtistNames(artistName: String, limit: Int = 8): List<String> = withContext(ioDispatcher) {
        try {
            val response = lastFmApi.getSimilarArtists(artistName = artistName, limit = limit)
            if (!response.isSuccessful) return@withContext emptyList()

            response.body()
                ?.similarArtists
                ?.artists
                .orEmpty()
                .mapNotNull { it.name?.trim() }
                .filter { it.isNotEmpty() && !it.equals(artistName, ignoreCase = true) }
                .distinct()
                .take(limit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Similar artist fetch failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun fetchArtistProfile(artistName: String): ArtistDetails? = withContext(ioDispatcher) {
        try {
            val response = lastFmApi.getArtistInfo(artistName = artistName)
            if (response.isSuccessful) {
                val artistData = response.body()?.artist

                if (artistData != null) {
                    val rawBio = artistData.bio?.summary ?: "The archives hold no lore for this artist."
                    val cleanBio = rawBio.substringBefore("<a href").trim()
                    val genres = artistData.tags?.tag?.mapNotNull { it.name } ?: emptyList()
                    val bestImageUrl =
                        artistData.image?.lastOrNull { !it.url.isNullOrBlank() }?.url

                    if (BuildConfig.DEBUG) {
                        Log.d(TAG, "Fetched artist profile")
                    }

                    return@withContext ArtistDetails(
                        name = artistData.name,
                        bio = cleanBio,
                        genres = genres,
                        imageUrl = bestImageUrl
                    )
                }
            } else {
                Log.e(TAG, "API rejected the request: ${response.code()}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Artist profile fetch failed: ${e.message}")
        }
        return@withContext null
    }

    private companion object {
        const val TAG = "LastFmRepository"
        const val CALLBACK_URI = "tigerplayer://callback/lastfm"
        const val MAX_BATCH_SIZE = 50
        const val ERROR_INVALID_PARAMS = 6
        const val ERROR_INVALID_SESSION = 9
        const val ERROR_INVALID_API_KEY = 10
        const val ERROR_SERVICE_OFFLINE = 11
        const val ERROR_TEMP_UNAVAILABLE = 16
        const val ERROR_SUSPENDED_KEY = 26
        const val ERROR_RATE_LIMIT = 29
    }
}

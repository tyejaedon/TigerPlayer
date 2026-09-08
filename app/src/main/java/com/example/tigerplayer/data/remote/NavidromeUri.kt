package com.example.tigerplayer.data.remote

import android.net.Uri
import androidx.core.net.toUri
import com.example.tigerplayer.data.local.NavidromePrefs
import com.example.tigerplayer.utils.NavidromeSecurity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opaque, credential-free identifiers for Navidrome resources (issue #44).
 *
 * Subsonic authenticates with a salted MD5 token that rotates. Persisting a signed URL - into Room,
 * into the queue snapshot, or into a `MediaItem` - produces a URL that is valid when written and
 * silently dead when replayed. These URIs carry only the resource id, so they are safe to persist
 * indefinitely; the signature is applied at request time by [NavidromeUrlSigner].
 *
 * Format: `navidrome://stream/<id>` and `navidrome://art/<id>`.
 */
object NavidromeUri {

    const val SCHEME = "navidrome"
    const val HOST_STREAM = "stream"
    const val HOST_ART = "art"

    /**
     * Prefix applied to [com.example.tigerplayer.data.model.AudioTrack.id] for remote tracks.
     *
     * Deliberately `navidrome_`, matching what `NavidromeMapper` already emitted before issue #44.
     * Track ids are foreign keys in `playlist_track_cross_ref`, `playback_history`, `lyrics_cache`
     * and `waveform_cache`, and are embedded in the persisted queue snapshot - changing the
     * separator would orphan every one of those rows with no migration to repair them.
     */
    const val TRACK_ID_PREFIX = "navidrome_"

    fun stream(id: String): Uri = build(HOST_STREAM, id)

    fun art(id: String): Uri = build(HOST_ART, id)

    fun isNavidrome(uri: Uri): Boolean = uri.scheme == SCHEME

    /** The resource id, or `null` if [uri] is not one of ours. */
    fun resourceId(uri: Uri): String? {
        if (!isNavidrome(uri)) return null
        return uri.pathSegments.firstOrNull()?.takeIf { it.isNotBlank() }
    }

    fun isStream(uri: Uri): Boolean = isNavidrome(uri) && uri.host == HOST_STREAM

    fun isArt(uri: Uri): Boolean = isNavidrome(uri) && uri.host == HOST_ART

    private fun build(host: String, id: String): Uri = Uri.Builder()
        .scheme(SCHEME)
        .authority(host)
        .appendPath(id)
        .build()
}

/**
 * Turns an opaque [NavidromeUri] into a freshly signed HTTPS/HTTP URL.
 *
 * A new salt and token are generated per call, so a URL is never reused beyond the request that
 * created it. Returns `null` when credentials are absent, which callers must treat as "not
 * playable" rather than falling back to an unsigned request.
 */
@Singleton
class NavidromeUrlSigner @Inject constructor(
    private val navidromePrefs: NavidromePrefs
) {

    /**
     * @return a signed absolute URL, or `null` if [uri] is not a Navidrome URI or the user is not
     *   currently linked to a server.
     */
    fun sign(uri: Uri): Uri? {
        val id = NavidromeUri.resourceId(uri) ?: return null
        val baseUrl = navidromePrefs.serverUrlSnapshot()?.takeIf { it.isNotBlank() } ?: return null
        val user = navidromePrefs.usernameSnapshot()?.takeIf { it.isNotBlank() } ?: return null
        val pass = navidromePrefs.passwordSnapshot()?.takeIf { it.isNotBlank() } ?: return null

        val endpoint = when {
            NavidromeUri.isArt(uri) -> "getCoverArt.view"
            NavidromeUri.isStream(uri) -> "stream.view"
            else -> return null
        }

        val payload = NavidromeSecurity.generateAuthPayload(user, pass)
        val normalizedBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        val builder = "${normalizedBase}rest/$endpoint".toUri()
            .buildUpon()
            .appendQueryParameter("id", id)
            .appendQueryParameter("u", payload.u)
            .appendQueryParameter("t", payload.t)
            .appendQueryParameter("s", payload.s)
            .appendQueryParameter("v", payload.v)
            .appendQueryParameter("c", payload.c)

        if (NavidromeUri.isArt(uri)) {
            builder.appendQueryParameter("size", DEFAULT_ART_SIZE.toString())
        }

        // Deliberately no `format` or `maxBitRate`: Navidrome then streams the original file,
        // preserving the bit-perfect path for FLAC/ALAC sources.
        return builder.build()
    }

    private companion object {
        const val DEFAULT_ART_SIZE = 500
    }
}


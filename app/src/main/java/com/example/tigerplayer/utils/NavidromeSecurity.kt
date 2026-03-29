package com.example.tigerplayer.utils

import java.security.MessageDigest
import java.util.UUID
import androidx.core.net.toUri
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.remote.api.RemoteTrack
import com.example.tigerplayer.utils.NavidromeSecurity

data class NavidromeAuth(
    val u: String,
    val t: String,
    val s: String,
    val v: String = "1.16.1",
    val c: String = "TigerPlayer"
)

object NavidromeSecurity {

    fun generateAuthPayload(username: String, pass: String): NavidromeAuth {
        val salt = UUID.randomUUID().toString().substring(0, 8)
        val token = md5(pass + salt)
        return NavidromeAuth(u = username, t = token, s = salt)
    }

    // This helper is used by AudioRepository to generate valid Stream URIs
    fun generateToken(pass: String, salt: String): String {
        return md5(pass + salt)
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
object NavidromeArtHelper {
    /**
     * Forges the authenticated URL required by Coil to fetch the image.
     */
    fun getCoverArtUrl(
        serverUrl: String, // From your DataStore/HostManager
        username: String,
        pass: String,
        coverArtId: String,
        size: Int = 500 // Subsonic can scale images on the server!
    ): String {
        val payload = NavidromeSecurity.generateAuthPayload(username, pass)

        // Ensure the server URL ends with a slash to prevent malformed URLs
        val baseUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"

        return "${baseUrl}rest/getCoverArt.view?" +
                "id=$coverArtId" +
                "&u=${payload.u}" +
                "&t=${payload.t}" +
                "&s=${payload.s}" +
                "&v=${payload.v}" +
                "&c=${payload.c}" +
                "&size=$size"
    }
}

object NavidromeMapper {

    /**
     * The Great Convergence: Transmutes a RemoteTrack from the server
     * into a standard AudioTrack that your UI and Player already understand.
     */
    fun RemoteTrack.toAudioTrack(
        serverUrl: String,
        username: String,
        pass: String
    ): AudioTrack {
        // Generate the auth tokens needed for the URLs
        val payload = NavidromeSecurity.generateAuthPayload(username, pass)
        val baseUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"

        // 1. FORGE THE AUDIO STREAM URI
        // This is the direct link ExoPlayer/Media3 will use to stream the song
        val streamUrl = "${baseUrl}rest/stream.view?" +
                "id=${this.id}" +
                "&u=${payload.u}" +
                "&t=${payload.t}" +
                "&s=${payload.s}" +
                "&v=${payload.v}" +
                "&c=${payload.c}"

        // 2. FORGE THE COVER ART URI
        // Coil will handle caching this so it doesn't drain data on every scroll
        val artUrl = "${baseUrl}rest/getCoverArt.view?" +
                "id=${this.coverArtId ?: this.albumId ?: this.id}" + // Fallbacks just in case
                "&u=${payload.u}" +
                "&t=${payload.t}" +
                "&s=${payload.s}" +
                "&v=${payload.v}" +
                "&c=${payload.c}" +
                "&size=500"

        return AudioTrack(
            // Prefix the ID so it never collides with a local MediaStore ID
            id = "navidrome_${this.id}",
            title = this.title,
            artist = this.artist,
            album = this.album,
            uri = streamUrl.toUri(),
            artworkUri = artUrl.toUri(),

            // Subsonic returns duration in seconds, Android needs milliseconds!
            durationMs = (this.duration * 1000L),

            mimeType = "audio/${this.suffix.lowercase()}", // e.g., "audio/mp3", "audio/flac"
            isLocal = false,
            isRemote = true,
            bitrate = this.bitRate ?: 0,
            sampleRate = 0, // Navidrome doesn't typically expose sample rate here
            trackNumber = this.track ?: 0,
            serverPath = streamUrl,
            year = this.year?.toString(),
            isliked = false,

            // --- THE LYRICS LINK ---
            // Since remote tracks don't have a local file path (like /storage/emulated/0/...),
            // we feed the stream URL or a unique Navidrome string to the path field.
            // This ensures your LyricsRepository doesn't crash and has a unique key to hash.
            path = "navidrome://${this.id}"
        )
    }
}

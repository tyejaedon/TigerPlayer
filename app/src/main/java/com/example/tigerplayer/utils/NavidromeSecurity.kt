package com.example.tigerplayer.utils

import java.security.MessageDigest
import java.util.UUID
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.remote.NavidromeUri
import com.example.tigerplayer.data.remote.api.RemoteTrack

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

    fun generateToken(pass: String, salt: String): String {
        return md5(pass + salt)
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

object NavidromeMapper {

    /**
     * Transmutes a [RemoteTrack] from the server into a standard [AudioTrack].
     *
     * URIs are deliberately **opaque** (`navidrome://stream/<id>`, `navidrome://art/<id>`): they
     * carry no credentials, so they are safe to persist in the queue snapshot and replay after a
     * restart. Signing happens at request time - see `NavidromeUrlSigner` (issue #44).
     *
     * This is the single mapper for Navidrome tracks. A second, divergent one previously lived in
     * `AudioRepository` and emitted malformed URLs with a duplicated `u=` parameter.
     */
    fun RemoteTrack.toAudioTrack(): AudioTrack {
        val artId = this.coverArtId ?: this.albumId ?: this.id

        return AudioTrack(
            // Prefixed so it never collides with a local MediaStore id, and so MediaSource
            // resolution can identify remote plays.
            id = "${NavidromeUri.TRACK_ID_PREFIX}${this.id}",
            title = this.title,
            artist = this.artist,
            album = this.album,
            uri = NavidromeUri.stream(this.id),
            artworkUri = NavidromeUri.art(artId),

            // Subsonic returns duration in seconds, Android needs milliseconds.
            durationMs = (this.duration * 1000L),

            mimeType = "audio/${this.suffix.lowercase()}",
            isLocal = false,
            isRemote = true,
            bitrate = this.bitRate,
            sampleRate = 0, // Navidrome does not expose sample rate on this endpoint.
            trackNumber = this.track,
            serverPath = this.id,
            year = this.year?.toString(),
            isLiked = false,

            // Remote tracks have no on-disk path; a stable opaque key keeps LyricsRepository happy.
            path = NavidromeUri.stream(this.id).toString()
        )
    }
}

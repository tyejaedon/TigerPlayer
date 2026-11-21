package com.tigerplayer.data.model

import android.content.IntentSender

/**
 * Editable metadata and embedded artwork extracted directly from a local audio file.
 */
data class AudioTagData(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val trackNumber: String = "",
    val discNumber: String = "",
    val year: String = "",
    val genre: String = "",
    val artworkBytes: ByteArray? = null,
    val artworkMimeType: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioTagData) return false
        return title == other.title &&
            artist == other.artist &&
            album == other.album &&
            albumArtist == other.albumArtist &&
            trackNumber == other.trackNumber &&
            discNumber == other.discNumber &&
            year == other.year &&
            genre == other.genre &&
            artworkBytes.contentEquals(other.artworkBytes) &&
            artworkMimeType == other.artworkMimeType
    }

    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + artist.hashCode()
        result = 31 * result + album.hashCode()
        result = 31 * result + albumArtist.hashCode()
        result = 31 * result + trackNumber.hashCode()
        result = 31 * result + discNumber.hashCode()
        result = 31 * result + year.hashCode()
        result = 31 * result + genre.hashCode()
        result = 31 * result + (artworkBytes?.contentHashCode() ?: 0)
        result = 31 * result + (artworkMimeType?.hashCode() ?: 0)
        return result
    }
}

sealed interface ArtworkEditAction {
    data object Keep : ArtworkEditAction
    data class Replace(val bytes: ByteArray, val mimeType: String) : ArtworkEditAction {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Replace) return false
            return bytes.contentEquals(other.bytes) && mimeType == other.mimeType
        }

        override fun hashCode(): Int = 31 * bytes.contentHashCode() + mimeType.hashCode()
    }
    data object Remove : ArtworkEditAction
}

data class TagEditRequest(
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val trackNumber: String,
    val discNumber: String,
    val year: String,
    val genre: String,
    val artworkAction: ArtworkEditAction = ArtworkEditAction.Keep
) {
    fun toBatchRequest(): BatchTagEditRequest = BatchTagEditRequest(
        title = title,
        artist = artist,
        album = album,
        albumArtist = albumArtist,
        trackNumber = trackNumber,
        discNumber = discNumber,
        year = year,
        genre = genre,
        artworkAction = artworkAction
    )
}

/**
 * Batch edit request where null fields preserve each track's existing value.
 */
data class BatchTagEditRequest(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val trackNumber: String? = null,
    val discNumber: String? = null,
    val year: String? = null,
    val genre: String? = null,
    val artworkAction: ArtworkEditAction = ArtworkEditAction.Keep
)

data class TrackTagError(
    val trackId: String,
    val trackTitle: String,
    val reason: String
)

sealed interface TagWriteResult {
    data class Success(val updatedTagsByTrackId: Map<String, AudioTagData>) : TagWriteResult
    data class PermissionRequired(val intentSender: IntentSender) : TagWriteResult
    data class Failure(val errors: List<TrackTagError>) : TagWriteResult
}

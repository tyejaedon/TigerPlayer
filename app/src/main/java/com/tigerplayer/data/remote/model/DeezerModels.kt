package com.tigerplayer.data.remote.model

import com.google.gson.annotations.SerializedName

data class DeezerSearchResponse<T>(
    @SerializedName("data") val data: List<T> = emptyList(),
    @SerializedName("total") val total: Int = 0,
    @SerializedName("next") val next: String? = null
)

data class DeezerTrackDto(
    @SerializedName("id") val id: Long,
    @SerializedName("title") val title: String,
    @SerializedName("title_short") val titleShort: String? = null,
    @SerializedName("link") val link: String? = null,
    @SerializedName("duration") val duration: Int = 0,
    @SerializedName("rank") val rank: Int = 0,
    @SerializedName("explicit_lyrics") val explicitLyrics: Boolean = false,
    @SerializedName("preview") val preview: String? = null,
    @SerializedName("bpm") val bpm: Float? = null,
    @SerializedName("gain") val gain: Float? = null,
    @SerializedName("artist") val artist: DeezerArtistDto? = null,
    @SerializedName("album") val album: DeezerAlbumDto? = null
)

data class DeezerArtistDto(
    @SerializedName("id") val id: Long,
    @SerializedName("name") val name: String,
    @SerializedName("link") val link: String? = null,
    @SerializedName("picture") val picture: String? = null,
    @SerializedName("picture_small") val pictureSmall: String? = null,
    @SerializedName("picture_medium") val pictureMedium: String? = null,
    @SerializedName("picture_big") val pictureBig: String? = null,
    @SerializedName("picture_xl") val pictureXl: String? = null,
    @SerializedName("nb_album") val nbAlbum: Int? = null,
    @SerializedName("nb_fan") val nbFan: Int? = null
) {
    /** Returns the highest-resolution artist portrait URL available (1000x1000 `_xl` preferred). */
    val bestPictureUrl: String?
        get() = listOfNotNull(pictureXl, pictureBig, pictureMedium, picture, pictureSmall)
            .firstOrNull { it.isNotBlank() }
}

data class DeezerAlbumDto(
    @SerializedName("id") val id: Long,
    @SerializedName("title") val title: String,
    @SerializedName("cover") val cover: String? = null,
    @SerializedName("cover_small") val coverSmall: String? = null,
    @SerializedName("cover_medium") val coverMedium: String? = null,
    @SerializedName("cover_big") val coverBig: String? = null,
    @SerializedName("cover_xl") val coverXl: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("nb_tracks") val nbTracks: Int? = null,
    @SerializedName("artist") val artist: DeezerArtistDto? = null,
    @SerializedName("tracks") val tracks: DeezerTrackListWrapper? = null
) {
    /** Returns the highest-resolution album cover URL available (1000x1000 `_xl` preferred). */
    val bestCoverUrl: String?
        get() = listOfNotNull(coverXl, coverBig, coverMedium, cover, coverSmall)
            .firstOrNull { it.isNotBlank() }
}

data class DeezerTrackListWrapper(
    @SerializedName("data") val data: List<DeezerTrackDto> = emptyList()
)

data class DeezerChartResponse(
    @SerializedName("tracks") val tracks: DeezerSearchResponse<DeezerTrackDto>? = null,
    @SerializedName("artists") val artists: DeezerSearchResponse<DeezerArtistDto>? = null,
    @SerializedName("albums") val albums: DeezerSearchResponse<DeezerAlbumDto>? = null
)

package com.tigerplayer.data.remote.api.deezer

import com.google.gson.annotations.SerializedName

data class DeezerErrorDto(
    @SerializedName("type") val type: String? = null,
    @SerializedName("message") val message: String? = null,
    @SerializedName("code") val code: Int? = null
)

data class DeezerSearchResponse<T>(
    @SerializedName("data") val data: List<T> = emptyList(),
    @SerializedName("total") val total: Int = 0,
    @SerializedName("next") val next: String? = null,
    @SerializedName("error") val error: DeezerErrorDto? = null
)

data class DeezerChartResponse(
    @SerializedName("tracks") val tracks: DeezerSearchResponse<DeezerTrackDto>? = null,
    @SerializedName("albums") val albums: DeezerSearchResponse<DeezerAlbumDto>? = null,
    @SerializedName("artists") val artists: DeezerSearchResponse<DeezerArtistDto>? = null,
    @SerializedName("error") val error: DeezerErrorDto? = null
)

data class DeezerTrackDto(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("title") val title: String? = null,
    @SerializedName("duration") val duration: Int = 0,
    @SerializedName("preview") val preview: String? = null,
    @SerializedName("bpm") val bpm: Float? = null,
    @SerializedName("gain") val gain: Float? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("artist") val artist: DeezerArtistDto? = null,
    @SerializedName("album") val album: DeezerAlbumDto? = null,
    @SerializedName("error") val error: DeezerErrorDto? = null
)

data class DeezerAlbumDto(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("title") val title: String? = null,
    @SerializedName("cover_xl") val coverXl: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("error") val error: DeezerErrorDto? = null
)

data class DeezerArtistDto(
    @SerializedName("id") val id: Long = 0L,
    @SerializedName("name") val name: String? = null,
    @SerializedName("picture_xl") val pictureXl: String? = null,
    @SerializedName("error") val error: DeezerErrorDto? = null
)

package com.example.tigerplayer.data.remote.model

import com.google.gson.annotations.SerializedName

data class LastFmResponse(
    @SerializedName("artist") val artist: LastFmArtist?
)

data class LastFmArtist(
    @SerializedName("name") val name: String,
    @SerializedName("stats") val stats: LastFmStats?,
    @SerializedName("tags") val tags: LastFmTags?,
    @SerializedName("bio") val bio: LastFmBio?
)

data class LastFmStats(
    @SerializedName("listeners") val listeners: String?,
    @SerializedName("playcount") val playcount: String?
)

data class LastFmTags(
    @SerializedName("tag") val tag: List<LastFmTag>?
)

data class LastFmTag(
    @SerializedName("name") val name: String
)

data class LastFmBio(
    @SerializedName("summary") val summary: String?,
    @SerializedName("content") val content: String?
)
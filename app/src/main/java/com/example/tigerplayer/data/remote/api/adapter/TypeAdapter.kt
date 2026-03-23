package com.example.tigerplayer.data.remote.api.adapter

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class LastFmResponse(
    @SerializedName("artist") val artist: LastFmArtist?
)

data class LastFmArtist(
    @SerializedName("name") val name: String,
    @SerializedName("stats") val stats: LastFmStats?,
    @SerializedName("tags") val tags: JsonElement?, // THE FIX: Prevents array/object crashes
    @SerializedName("bio") val bio: LastFmBio?
)

data class LastFmStats(
    @SerializedName("listeners") val listeners: String?,
    @SerializedName("playcount") val playcount: String?
)

data class LastFmBio(
    @SerializedName("summary") val summary: String?,
    @SerializedName("content") val content: String?
)
package com.example.tigerplayer.data.remote.model

import com.google.gson.annotations.SerializedName

// 1. The Root Response


data class LastFmResponse(
    @SerializedName("artist") val artist: LastFmArtist?
)

data class LastFmArtist(
    @SerializedName("name") val name: String?,
    @SerializedName("mbid") val mbid: String?,
    @SerializedName("url") val url: String?,
    // Last.fm returns an array of image objects
    @SerializedName("image") val image: List<LastFmImage>?,
    @SerializedName("stats") val stats: LastFmStats?,
    @SerializedName("tags") val tags: LastFmTags?,
    @SerializedName("bio") val bio: LastFmBio?
)

/**
 * THE CRITICAL FIX: Last.fm uses "#text" for the URL string.
 * If this @SerializedName is missing or misspelled, images will always be null.
 */
data class LastFmImage(
    @SerializedName("#text") val url: String?,
    @SerializedName("size") val size: String? // small, medium, large, extralarge, mega
)

data class LastFmStats(
    @SerializedName("listeners") val listeners: String?,
    @SerializedName("playcount") val playcount: String?
)

/**
 * Last.fm nests tags: tags -> tag -> List of objects
 */
data class LastFmTags(
    @SerializedName("tag") val tag: List<LastFmTag>?
)

data class LastFmTag(
    @SerializedName("name") val name: String?
)

data class LastFmBio(
    @SerializedName("summary") val summary: String?,
    @SerializedName("content") val content: String?
)




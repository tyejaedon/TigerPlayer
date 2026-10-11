package com.tigerplayer.data.model

enum class ArtistSource {
    LOCAL,
    SPOTIFY
}

data class Artist(
    val id: String,
    val name: String,
    val source: ArtistSource,
    val trackCount: Int = 0,
    val albumCount: Int = 0,
    val genres: List<String> = emptyList()
)

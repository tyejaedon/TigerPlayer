package com.example.tigerplayer.data.remote.model

import com.google.gson.annotations.SerializedName

// --- SHARED UTILS ---


data class SpotifyOwner(
    @SerializedName("display_name") val displayName: String
)

data class SpotifyArtist(
    @SerializedName("name") val name: String
)

// --- PLAYLIST MODELS ---
data class SpotifyPlaylistResponse(
    @SerializedName("items") val items: List<SpotifyPlaylist>
)

data class SpotifyPlaylist(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("images") val images: List<SpotifyImage>?,
    @SerializedName("owner") val owner: SpotifyOwner,
    @SerializedName("uri") val uri: String
)

// --- ALBUM MODELS (The Volume Archives) ---
data class SpotifySavedAlbumResponse(
    @SerializedName("items") val items: List<SavedAlbumItem>
)

data class SavedAlbumItem(
    @SerializedName("added_at") val addedAt: String,
    @SerializedName("album") val album: SpotifyAlbum
)

data class SpotifyAlbum(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("uri") val uri: String, // Critical for App Remote playback
    @SerializedName("images") val images: List<SpotifyImage>?,
    @SerializedName("artists") val artists: List<SpotifyArtist> // For the "By Artist" subtext in your grid
)

// --- TRACK MODELS (The Essence) ---
data class SpotifyPlaylistTrackResponse(
    @SerializedName("items") val items: List<PlaylistTrackItem>
)

data class PlaylistTrackItem(
    @SerializedName("track") val track: SpotifyTrack?
)

data class SpotifyTrack(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("uri") val uri: String,
    @SerializedName("album") val album: SpotifyAlbum,
    @SerializedName("artists") val artists: List<SpotifyArtist>,
    @SerializedName("duration_ms") val durationMs: Long // Helpful for the UI progress bars
)

data class SpotifyAlbumTrackResponse(
    @SerializedName("items") val items: List<SpotifyTrack>
)
data class SpotifyArtistSearchResponse(
    @SerializedName("artists")
    val artists: ArtistEnvelope
)

data class ArtistEnvelope(
    @SerializedName("items")
    val items: List<SpotifyArtistDetail>
)

data class SpotifyArtistDetail(
    @SerializedName("id")
    val id: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("images")
    val images: List<SpotifyImage>?,
    @SerializedName("genres")
    val genres: List<String>?,
    @SerializedName("popularity")
    val popularity: Int?
)

data class SpotifyImage(
    @SerializedName("url")
    val url: String,
    @SerializedName("height")
    val height: Int,
    @SerializedName("width")
    val width: Int
)


data class AlbumEnvelope(
    val items: List<SpotifyAlbum>
)

data class SpotifyAlbumSearchResponse(
    val albums: AlbumPagingObject
)

data class AlbumPagingObject(
    val items: List<SpotifyAlbumDetail>
)

data class SpotifyAlbumDetail(
    val id: String,
    val name: String,
    val images: List<SpotifyImage>? // Same model we used for Artist profiles
)


data class Followers(val total: Int)

// Re-using the SpotifyImage model from our Artist detail work


package com.example.tigerplayer.data.remote.model

import com.google.gson.annotations.SerializedName

// ==========================================
// --- 1. THE FOUNDATION (Shared Utils) ---
// ==========================================

data class SpotifyImage(
    @SerializedName("url") val url: String,
    @SerializedName("height") val height: Int?,
    @SerializedName("width") val width: Int?
)

data class SpotifyFollowers(
    @SerializedName("total") val total: Int
)

data class SpotifyOwner(
    @SerializedName("display_name") val displayName: String,
    @SerializedName("id") val id: String
)

// ==========================================
// --- 2. THE PORTAL (Generic Paging) ---
// ==========================================

/**
 * Spotify wraps almost all lists in this Paging Object.
 * Using a Generic <T> allows us to use one model for Playlists, Albums, and Tracks.
 */
data class SpotifyPaging<T>(
    @SerializedName("items") val items: List<T>,
    @SerializedName("total") val total: Int,
    @SerializedName("next") val next: String?,
    @SerializedName("limit") val limit: Int,
    @SerializedName("offset") val offset: Int
)

// ==========================================
// --- 3. THE VANGUARD (Artist Models) ---
// ==========================================

interface ArtistBase {
    val id: String
    val name: String
    val uri: String
}

/**
 * Simplified Artist: Found inside Track and Album objects.
 * Contains only the essential coordinates.
 */
data class SpotifyArtistSimplified(
    @SerializedName("id") override val id: String,
    @SerializedName("name") override val name: String,
    @SerializedName("uri") override val uri: String
) : ArtistBase

/**
 * THE ALIGNMENT FIX: Renamed from SpotifyArtistFull to SpotifyArtistDetail
 * to perfectly match the MediaDataRepository's expected type.
 * Contains the "Acoustic Lore" (Popularity, Genres, Images).
 */
data class SpotifyArtistDetail(
    @SerializedName("id") override val id: String,
    @SerializedName("name") override val name: String,
    @SerializedName("uri") override val uri: String,
    @SerializedName("images") val images: List<SpotifyImage>?,
    @SerializedName("genres") val genres: List<String>?,
    @SerializedName("popularity") val popularity: Int?, // 0-100
    @SerializedName("followers") val followers: SpotifyFollowers?
) : ArtistBase

// ==========================================
// --- 4. THE VOLUMES (Album Models) ---
// ==========================================

data class SpotifyAlbum(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("uri") val uri: String,
    @SerializedName("images") val images: List<SpotifyImage>?,
    @SerializedName("artists") val artists: List<SpotifyArtistSimplified>,
    @SerializedName("total_tracks") val totalTracks: Int?,
    @SerializedName("release_date") val releaseDate: String?
)

/**
 * Wrapper for the "User's Saved Albums" endpoint.
 */
data class SavedAlbumItem(
    @SerializedName("added_at") val addedAt: String,
    @SerializedName("album") val album: SpotifyAlbum
)

// ==========================================
// --- 5. THE CHANTS (Track Models) ---
// ==========================================

data class SpotifyTrack(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("uri") val uri: String,
    @SerializedName("duration_ms") val durationMs: Long,
    @SerializedName("popularity") val popularity: Int?, // Only present in 'Full Track' objects
    @SerializedName("artists") val artists: List<SpotifyArtistSimplified>,

    // CRITICAL: Make this nullable. Simplified tracks (like those inside an Album object)
    // do not contain the album field to prevent infinite recursion.
    @SerializedName("album") val album: SpotifyAlbum?
)

/**
 * Wrapper for Playlist tracks, which adds metadata about when it was added.
 */
data class PlaylistTrackItem(
    @SerializedName("added_at") val addedAt: String?,
    @SerializedName("track") val track: SpotifyTrack
)

// ==========================================
// --- 6. THE GRIMOIRES (Playlist Models) ---
// ==========================================

data class SpotifyPlaylist(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("uri") val uri: String,
    @SerializedName("images") val images: List<SpotifyImage>?,
    @SerializedName("owner") val owner: SpotifyOwner,
    @SerializedName("tracks") val trackInfo: PlaylistTrackInfo?
)

data class PlaylistTrackInfo(
    @SerializedName("total") val total: Int,
    @SerializedName("href") val href: String
)

// ==========================================
// --- 7. THE SCRYER (Search Responses) ---
// ==========================================

/**
 * The Root Object returned when hitting the /search endpoint.
 */
data class SpotifySearchResponse(
    @SerializedName("artists") val artists: SpotifyPaging<SpotifyArtistDetail>?, // Aligned with the rename
    @SerializedName("albums") val albums: SpotifyPaging<SpotifyAlbum>?,
    @SerializedName("tracks") val tracks: SpotifyPaging<SpotifyTrack>?,
    @SerializedName("playlists") val playlists: SpotifyPaging<SpotifyPlaylist>?
)

data class SpotifyTokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("expires_in") val expiresIn: Int,
    @SerializedName("refresh_token") val refreshToken: String?,
    @SerializedName("scope") val scope: String?
)

/**
 * Type Aliases for common list responses to keep the code clean.
 */
typealias SpotifyPlaylistResponse = SpotifyPaging<SpotifyPlaylist>
typealias SpotifySavedAlbumResponse = SpotifyPaging<SavedAlbumItem>
typealias SpotifyTrackListResponse = SpotifyPaging<SpotifyTrack>
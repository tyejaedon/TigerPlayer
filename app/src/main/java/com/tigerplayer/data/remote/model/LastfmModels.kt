package com.tigerplayer.data.remote.model

import com.google.gson.annotations.SerializedName

// 1. The Root Response


data class LastFmResponse(
    @SerializedName("artist") val artist: LastFmArtist?
)

data class LastFmSimilarArtistsResponse(
    @SerializedName("similarartists") val similarArtists: LastFmSimilarArtistsContainer?
)

data class LastFmSimilarTracksResponse(
    @SerializedName("similartracks") val similarTracks: LastFmSimilarTracksContainer?
)

data class LastFmSimilarTracksContainer(
    @SerializedName("track") val track: List<LastFmSimilarTrack>?
)

data class LastFmSimilarTrack(
    @SerializedName("name") val name: String?,
    @SerializedName("artist") val artist: LastFmTrackArtist?
)

data class LastFmTrackArtist(
    @SerializedName("name") val name: String?
)

data class LastFmSimilarArtistsContainer(
    @SerializedName("artist") val artists: List<LastFmSimilarArtist>?
)

data class LastFmSimilarArtist(
    @SerializedName("name") val name: String?
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

data class LastFmSessionResponse(
    @SerializedName("session") val session: LastFmSession?
)

data class LastFmSession(
    @SerializedName("name") val name: String?,
    @SerializedName("key") val key: String?,
    @SerializedName("subscriber") val subscriber: Int?
)

data class LastFmNowPlayingResponse(
    @SerializedName("nowplaying") val nowPlaying: LastFmNowPlayingTrack?
)

data class LastFmNowPlayingTrack(
    @SerializedName("track") val track: LastFmTextField?,
    @SerializedName("artist") val artist: LastFmTextField?
)

data class LastFmTextField(
    @SerializedName("#text") val text: String?
)

data class LastFmScrobbleResponse(
    @SerializedName("scrobbles") val scrobbles: LastFmScrobblesContainer?
)

data class LastFmScrobblesContainer(
    @SerializedName("@attr") val attr: LastFmScrobbleAttr?
)

data class LastFmScrobbleAttr(
    @SerializedName("accepted") val accepted: Int?,
    @SerializedName("ignored") val ignored: Int?
)

data class LastFmErrorResponse(
    @SerializedName("error") val error: Int?,
    @SerializedName("message") val message: String?
)





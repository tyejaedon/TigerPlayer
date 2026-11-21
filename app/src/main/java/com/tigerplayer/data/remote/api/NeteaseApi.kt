package com.tigerplayer.data.remote.api

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Public Web API for Netease Cloud Music.
 * Does not require encryption or auth for basic searching and lyrics.
 */
interface NeteaseApi {

    @GET("api/search/get/web")
    suspend fun search(
        @Query("s") query: String,
        @Query("type") type: Int = 1, // 1 = song
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 10
    ): Response<NeteaseSearchResponse>

    @GET("api/song/lyric")
    suspend fun getLyrics(
        @Query("id") songId: Long,
        @Query("lv") lv: Int = 1, // Standard lrc
        @Query("kv") kv: Int = 1, // Karaoke (not used usually)
        @Query("tv") tv: Int = -1, // Translations
        @Query("rv") rv: Int = 1  // Romanization
    ): Response<NeteaseLyricResponse>
}

data class NeteaseSearchResponse(
    val code: Int,
    val result: NeteaseSearchResult?
)

data class NeteaseSearchResult(
    val songs: List<NeteaseSong>?
)

data class NeteaseSong(
    val id: Long,
    val name: String,
    val artists: List<NeteaseArtist>?,
    val album: NeteaseAlbum?,
    val duration: Long?
)

data class NeteaseArtist(
    val name: String
)

data class NeteaseAlbum(
    val name: String
)

data class NeteaseLyricResponse(
    val code: Int,
    val lrc: NeteaseLyricObject?,
    val tlyric: NeteaseLyricObject?, // Translation
    val romalrc: NeteaseLyricObject? // Romanization
)

data class NeteaseLyricObject(
    val lyric: String?
)

package com.tigerplayer.data.remote.api

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

data class KugouCandidate(
    @SerializedName("id") val id: String,
    @SerializedName("accesskey") val accesskey: String,
    @SerializedName("singer") val singer: String? = null,
    @SerializedName("song") val song: String? = null,
    @SerializedName("duration") val duration: Long? = null
)

data class KugouSearchResponse(
    @SerializedName("status") val status: Int,
    @SerializedName("candidates") val candidates: List<KugouCandidate>? = null
)

data class KugouDownloadResponse(
    @SerializedName("status") val status: Int,
    @SerializedName("content") val content: String? = null,
    @SerializedName("fmt") val fmt: String? = null
)

interface KugouApi {
    @GET("https://krcs.kugou.com/search?ver=1&man=yes&client=mobi")
    suspend fun searchLyrics(
        @Query("keyword") keyword: String,
        @Query("duration") durationMs: Long? = null
    ): Response<KugouSearchResponse>

    @GET("https://lyrics.kugou.com/download?ver=1&client=pc&fmt=lrc&charset=utf8")
    suspend fun downloadLyrics(
        @Query("id") id: String,
        @Query("accesskey") accessKey: String
    ): Response<KugouDownloadResponse>
}

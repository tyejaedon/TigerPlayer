package com.tigerplayer.data.remote.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * Retrofit interface for fetching headphone compensation curves and parametric EQ profiles
 * from Jaakko Pasanen's AutoEq database (GitHub raw / CDN endpoints).
 */
interface AutoEqApi {

    /**
     * Fetches the raw ParametricEQ.txt for a given headphone profile path.
     */
    @GET
    suspend fun getParametricEqProfile(@Url url: String): Response<ResponseBody>
}

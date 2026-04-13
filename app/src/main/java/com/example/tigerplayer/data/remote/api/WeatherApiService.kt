package com.example.tigerplayer.data.remote.api

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApiService {
    @GET("v1/forecast")
    suspend fun getCurrentWeather(
        @Query("latitude") lat: Double = -1.2921,
        @Query("longitude") lon: Double = 36.8219,
        @Query("current") current: String = "temperature_2m,relative_humidity_2m,is_day,weather_code,wind_speed_10m"
    ): WeatherResponse
}

@Keep
data class WeatherResponse(
    @SerializedName("current") val current: CurrentWeather
)

@Keep
data class CurrentWeather(
    @SerializedName("temperature_2m") val temperature: Double? = null,
    @SerializedName("relative_humidity_2m") val humidity: Int? = null,
    @SerializedName("weather_code") val weatherCode: Int? = null,
    @SerializedName("wind_speed_10m") val windSpeed: Double? = null,
    @SerializedName("is_day") val isDay: Int? = null

)
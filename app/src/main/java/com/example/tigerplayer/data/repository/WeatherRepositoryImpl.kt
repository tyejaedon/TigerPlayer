package com.example.tigerplayer.data.repository

import android.content.Context
import com.example.tigerplayer.data.remote.api.WeatherApiService
import com.example.tigerplayer.utils.Resource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

// 🔥 THE FIX: Tell Hilt to bind the WeatherRepository interface to the WeatherRepositoryImpl implementation
@Module
@InstallIn(SingletonComponent::class)
abstract class WeatherRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindWeatherRepository(
        weatherRepositoryImpl: WeatherRepositoryImpl
    ): WeatherRepository
}

@Singleton
class WeatherRepositoryImpl @Inject constructor(
    private val api: WeatherApiService,
    @param:ApplicationContext private val context: Context
) : WeatherRepository {

    override fun getWeather(lat: Double, lon: Double): Flow<Resource<WeatherInfo>> = flow {
        emit(Resource.Loading())
        val defaultData = WeatherInfo("--", "Offline", "--", "--", true)

        try {
            val response = withContext(Dispatchers.IO) {
                // The API now properly receives the coordinates passed into the function
                api.getCurrentWeather(lat = lat, lon = lon)
            }

            // Safely extract and format the 5 live sensors
            val tempStr = response.current.temperature?.toInt()?.toString() ?: "--"
            val conditionStr = response.current.weatherCode?.let { mapWmoCodeToCondition(it) } ?: "Unknown"
            val windStr = response.current.windSpeed?.toInt()?.toString()?.plus(" km/h") ?: "-- km/h"
            val humidityStr = response.current.humidity?.toString()?.plus("%") ?: "--%"
            val isDaytime = response.current.isDay == 1

            emit(Resource.Success(WeatherInfo(tempStr, conditionStr, windStr, humidityStr, isDaytime)))

        } catch (e: Exception) {
            emit(Resource.Error(
                message = "Network error: ${e.message}",
                data = defaultData
            ))
        }
    }

    private fun mapWmoCodeToCondition(code: Int): String {
        return when (code) {
            0 -> "Clear Sky"
            1 -> "Mainly Clear"
            2 -> "Partly Cloudy"
            3 -> "Overcast"
            45 -> "Fog"
            48 -> "Depositing Rime Fog"
            51 -> "Light Drizzle"
            53 -> "Moderate Drizzle"
            55 -> "Dense Drizzle"
            56 -> "Light Freezing Drizzle"
            57 -> "Dense Freezing Drizzle"
            61 -> "Light Rain"
            63 -> "Moderate Rain"
            65 -> "Heavy Rain"
            66 -> "Light Freezing Rain"
            67 -> "Heavy Freezing Rain"
            71 -> "Light Snowfall"
            73 -> "Moderate Snowfall"
            75 -> "Heavy Snowfall"
            77 -> "Snow Grains"
            80 -> "Light Rain Showers"
            81 -> "Moderate Rain Showers"
            82 -> "Violent Rain Showers"
            85 -> "Light Snow Showers"
            86 -> "Heavy Snow Showers"
            95 -> "Thunderstorm"
            96 -> "Thunderstorm with Light Hail"
            99 -> "Thunderstorm with Heavy Hail"
            else -> "Unknown"
        }
    }
}
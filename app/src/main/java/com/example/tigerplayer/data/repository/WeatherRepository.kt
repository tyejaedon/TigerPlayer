package com.example.tigerplayer.data.repository

import com.example.tigerplayer.utils.Resource
import kotlinx.coroutines.flow.Flow
interface WeatherRepository {
    fun getWeather(lat: Double, lon: Double): Flow<Resource<WeatherInfo>>
}
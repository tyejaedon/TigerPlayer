package com.tigerplayer.data.repository

import com.tigerplayer.utils.Resource
import kotlinx.coroutines.flow.Flow
interface WeatherRepository {
    fun getWeather(lat: Double, lon: Double): Flow<Resource<WeatherInfo>>
}
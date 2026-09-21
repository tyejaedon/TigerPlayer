package com.tigerplayer.data.repository

// This is the clean, UI-ready data courier
data class WeatherInfo(
    val temperature: String,
    val condition: String,
    val humidity: String, // THE NEW LINK
    val windSpeed: String,
    val isDay: Boolean
)
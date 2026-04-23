package com.example.tigerplayer.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable



@Serializable
data class CurrentWeather(
    val temperature: Double,
    val weathercode: Int,

    // --- THE FUTURE-PROOFING RITUAL ---
    // We set these as nullable with default values (= null).
    // This guarantees that if the Open-Meteo API drops a field or you test
    // a different endpoint, the JSON parser will NEVER crash your app.

    val windspeed: Double? = null,
    val winddirection: Double? = null,


    // Open-Meteo returns 1 for Day and 0 for Night.
    // This is mathematically tied to Nairobi's exact sunset/sunrise times,
    // which is vastly superior to our hardcoded 6:00 PM Calendar check.
    val is_day: Int? = null,

    val time: String? = null
)
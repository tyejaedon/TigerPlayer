package com.example.tigerplayer.ui.home

import android.annotation.SuppressLint
import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tigerplayer.data.repository.WeatherRepository
import com.example.tigerplayer.data.repository.WeatherInfo // We will create this next
import com.example.tigerplayer.utils.Resource
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.tasks.await



@HiltViewModel
// Note: Changed to AndroidViewModel to access the application context for the FusedLocationClient
class HomeViewModel @Inject constructor(
    application: Application,
    private val weatherRepository: WeatherRepository
) : AndroidViewModel(application) {

    private val _weatherUiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val weatherUiState: StateFlow<WeatherUiState> = _weatherUiState.asStateFlow()

    // The Google Location Engine
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)

    init {
        fetchWeather()
    }

    @SuppressLint("MissingPermission") // We handled permissions in the UI
    fun fetchWeather() {
        viewModelScope.launch {
            _weatherUiState.value = WeatherUiState.Loading

            try {
                // 1. Fetch the absolute latest GPS coordinates
                val location: Location? = fusedLocationClient.lastLocation.await()

                // Fallback to Nairobi if GPS is off or unavailable
                val lat = location?.latitude ?: -1.2921
                val lon = location?.longitude ?: 36.8219

                // 2. Command the repository with the live coordinates
                weatherRepository.getWeather(lat, lon).collectLatest { result ->
                    when (result) {
                        is Resource.Loading -> _weatherUiState.value = WeatherUiState.Loading
                        is Resource.Success -> {
                            result.data?.let { info ->
                                _weatherUiState.value = WeatherUiState.Success(
                                    temperature = info.temperature,
                                    condition = info.condition,
                                    windSpeed = info.windSpeed,
                                    humidity = info.humidity,
                                    isDay = info.isDay
                                )
                            }
                        }
                        is Resource.Error -> {
                            _weatherUiState.value = WeatherUiState.Error(
                                errorMessage = result.message ?: "Error",
                                fallbackTemperature = result.data?.temperature ?: "--",
                                fallbackCondition = result.data?.condition ?: "Offline",
                                fallbackWindSpeed = result.data?.windSpeed ?: "--",
                                fallbackHumidity = result.data?.humidity ?: "--",
                                fallbackIsDay = result.data?.isDay ?: true
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // Handle total failure (e.g., Google Play Services missing)
                _weatherUiState.value = WeatherUiState.Error("GPS Failure", "--", "Offline", "--", "--", true)
            }
        }
    }
}

sealed class WeatherUiState {
    object Loading : WeatherUiState()

    data class Success(
        val temperature: String,
        val condition: String,
        val windSpeed: String,
        val humidity: String, // Wired
        val isDay: Boolean
    ) : WeatherUiState()

    data class Error(
        val errorMessage: String,
        val fallbackTemperature: String,
        val fallbackCondition: String,
        val fallbackWindSpeed: String,
        val fallbackHumidity: String, // Wired
        val fallbackIsDay: Boolean
    ) : WeatherUiState()
}
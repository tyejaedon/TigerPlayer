package com.tigerplayer.ui.home

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tigerplayer.data.repository.AutoEqRepository
import com.tigerplayer.engine.AudioDeviceEngine
import com.tigerplayer.engine.AdaptiveDspEngine
import com.tigerplayer.data.repository.WeatherRepository
import com.tigerplayer.data.repository.LocationProvider
import com.tigerplayer.utils.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi


@HiltViewModel
@OptIn(UnstableApi::class)
class HomeViewModel @Inject constructor(
    application: Application,
    private val weatherRepository: WeatherRepository,
    private val locationProvider: LocationProvider,
    private val audioDeviceEngine: AudioDeviceEngine,
    private val autoEqRepository: AutoEqRepository,
    private val adaptiveDspEngine: AdaptiveDspEngine
) : AndroidViewModel(application) {

    private val _weatherUiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val weatherUiState: StateFlow<WeatherUiState> = _weatherUiState.asStateFlow()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        fetchWeather()
        observeAudioDevice()
    }

    private fun observeAudioDevice() {
        viewModelScope.launch {
            audioDeviceEngine.activeDevice.collect { device ->
                val deviceName = device.displayName
                _uiState.update { it.copy(connectedDeviceName = deviceName) }

                if (deviceName.isNotBlank()) {
                    val matched = autoEqRepository.findBestMatch(deviceName)
                    _uiState.update { it.copy(autoEqMatch = matched) }
                } else {
                    _uiState.update { it.copy(autoEqMatch = null) }
                }
            }
        }
    }

    fun applyAutoEqProfile() {
        viewModelScope.launch {
            val match = _uiState.value.autoEqMatch ?: return@launch
            val result = autoEqRepository.fetchGraphicEqGains(match, emptyList()) // Assuming logic handles empty target frequencies or we pass standard 10-band ISO list
            if (result.isSuccess) {
                val (preamp, gains) = result.getOrThrow()
                // In a real implementation we would construct the AcousticNodes or ParametricBands
                // and push them to the AdaptiveDspEngine.
                // For now, this placeholder handles the UI dismissal.
            }
            dismissAutoEqCard()
        }
    }

    fun dismissAutoEqCard() {
        _uiState.update { it.copy(autoEqMatch = null) }
    }

    fun fetchWeather() {
        viewModelScope.launch {
            _weatherUiState.value = WeatherUiState.Loading

            try {
                // 1. Fetch the latest GPS coordinates via the location provider
                // (flavor-specific: Google Play Services in full, no-op in foss)
                val location: Location? = locationProvider.getLastLocation()

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
            } catch (_: Exception) {
                // Handle total failure (e.g., location unavailable or network error)
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

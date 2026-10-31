package com.tigerplayer.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tigerplayer.data.local.entity.AudioDeviceEntity
import com.tigerplayer.data.local.entity.DevicePresetEntity
import com.tigerplayer.data.model.AudioDeviceType
import com.tigerplayer.data.model.AudioOutputDevice
import com.tigerplayer.data.model.HeadphoneSearchResult
import com.tigerplayer.engine.AudioDeviceEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AudioDevicesUiState(
    val activeDevice: AudioOutputDevice = AudioOutputDevice(
        deviceId = AudioOutputDevice.DEVICE_ID_SPEAKER,
        displayName = "Phone Speaker",
        type = AudioDeviceType.PHONE_SPEAKER,
        isConnected = true,
        isActiveRoute = true,
        lastConnectedEpochMs = System.currentTimeMillis()
    ),
    val activePreset: DevicePresetEntity? = null,
    val knownDevices: List<AudioOutputDevice> = emptyList(),
    val activeDevicePresets: List<DevicePresetEntity> = emptyList(),
    val autoApplyEnabled: Boolean = true,
    val autoDetectEnabled: Boolean = true,
    val preferredTargetCurve: String = "Harman Over-Ear 2018",
    val searchQuery: String = "",
    val searchResults: List<HeadphoneSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val selectedDeviceForAutoEq: AudioOutputDevice? = null,
    val showAutoEqDialog: Boolean = false,
    val showCreateWiredDialog: Boolean = false,
    val targetCurveOptions: List<String> = listOf(
        "Harman Over-Ear 2018",
        "Harman In-Ear 2019v2",
        "Diffuse Field"
    )
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class AudioDevicesViewModel @Inject constructor(
    private val engine: AudioDeviceEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(AudioDevicesUiState())
    val uiState: StateFlow<AudioDevicesUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            combine(
                engine.activeDevice,
                engine.activePreset,
                engine.knownDevicesFlow,
                engine.settingsFlow
            ) { activeDev, activePre, devices, settings ->
                _uiState.update { current ->
                    current.copy(
                        activeDevice = activeDev,
                        activePreset = activePre,
                        knownDevices = devices,
                        autoApplyEnabled = settings.autoApplyDeviceEq,
                        autoDetectEnabled = settings.autoDetectAutoEq,
                        preferredTargetCurve = settings.preferredTargetCurve
                    )
                }
            }.collect()
        }

        // Observe presets for the currently active device
        viewModelScope.launch {
            engine.activeDevice.flatMapLatest { device ->
                engine.getPresetsForDeviceFlow(device.deviceId)
            }.collect { presets ->
                _uiState.update { it.copy(activeDevicePresets = presets) }
            }
        }

        // Pre-populate search with top models initially
        onSearchQueryChanged("")
    }

    fun onAutoApplyToggled(enabled: Boolean) {
        viewModelScope.launch {
            engine.setAutoApplyEnabled(enabled)
        }
    }

    fun onAutoDetectToggled(enabled: Boolean) {
        viewModelScope.launch {
            engine.setAutoDetectEnabled(enabled)
        }
    }

    fun onPreferredTargetCurveSelected(curve: String) {
        viewModelScope.launch {
            engine.setPreferredTargetCurve(curve)
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query, isSearching = true) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (query.isNotBlank()) {
                delay(150) // Debounce quick typing
            }
            val results = engine.searchHeadphones(query)
            _uiState.update { it.copy(searchResults = results, isSearching = false) }
        }
    }

    fun openAutoEqDialog(device: AudioOutputDevice) {
        _uiState.update {
            it.copy(
                selectedDeviceForAutoEq = device,
                showAutoEqDialog = true
            )
        }
        onSearchQueryChanged(device.displayName)
    }

    fun closeAutoEqDialog() {
        _uiState.update {
            it.copy(
                selectedDeviceForAutoEq = null,
                showAutoEqDialog = false
            )
        }
    }

    fun selectAutoEqModel(item: HeadphoneSearchResult) {
        val targetDevice = _uiState.value.selectedDeviceForAutoEq ?: _uiState.value.activeDevice
        viewModelScope.launch {
            engine.applyAutoEqToDevice(targetDevice.deviceId, item)
            closeAutoEqDialog()
        }
    }

    fun openCreateWiredDialog() {
        _uiState.update { it.copy(showCreateWiredDialog = true) }
    }

    fun closeCreateWiredDialog() {
        _uiState.update { it.copy(showCreateWiredDialog = false) }
    }

    fun createNamedWiredProfile(
        name: String,
        autoEqModel: String? = null,
        targetCurve: String? = null,
        formFactor: String? = null
    ) {
        viewModelScope.launch {
            val created = engine.createNamedWiredProfile(name, autoEqModel, targetCurve, formFactor)
            closeCreateWiredDialog()
        }
    }

    fun assignPreset(deviceId: String, presetId: String?) {
        viewModelScope.launch {
            engine.assignPresetToDevice(deviceId, presetId)
        }
    }

    fun deleteDevice(deviceId: String) {
        viewModelScope.launch {
            engine.deleteDevice(deviceId)
        }
    }

    fun deletePreset(presetId: String) {
        viewModelScope.launch {
            engine.deletePreset(presetId)
        }
    }
}

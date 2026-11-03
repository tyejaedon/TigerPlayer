package com.tigerplayer.ui.equalizer

import androidx.annotation.OptIn
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.tigerplayer.data.local.PlaybackPrefs
import com.tigerplayer.data.local.SettingsDataStore
import com.tigerplayer.data.model.HeadphoneSearchResult
import com.tigerplayer.data.model.HeadphoneSpecs
import com.tigerplayer.data.repository.AutoEqRepository
import com.tigerplayer.engine.AcousticEnvironmentMode
import com.tigerplayer.engine.AcousticNode
import com.tigerplayer.engine.AdaptiveDspEngine
import com.tigerplayer.engine.AudioDeviceEngine
import com.tigerplayer.engine.FilterType
import com.tigerplayer.utils.BiquadDesigner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import kotlin.math.log10
import kotlin.math.pow

@HiltViewModel
@OptIn(UnstableApi::class)
class EqualizerHubViewModel(
    private val adaptiveDspEngine: AdaptiveDspEngine,
    private val audioDeviceEngine: AudioDeviceEngine,
    private val autoEqRepository: AutoEqRepository,
    private val settingsDataStore: SettingsDataStore,
    private val playbackPrefs: PlaybackPrefs,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    @Inject
    constructor(
        adaptiveDspEngine: AdaptiveDspEngine,
        audioDeviceEngine: AudioDeviceEngine,
        autoEqRepository: AutoEqRepository,
        settingsDataStore: SettingsDataStore,
        playbackPrefs: PlaybackPrefs
    ) : this(
        adaptiveDspEngine,
        audioDeviceEngine,
        autoEqRepository,
        settingsDataStore,
        playbackPrefs,
        Dispatchers.IO,
        Dispatchers.Default
    )

    private val _uiState = MutableStateFlow(EqualizerHubUiState())
    val uiState: StateFlow<EqualizerHubUiState> = _uiState.asStateFlow()

    private var dspDispatchJob: Job? = null
    private var curveComputationJob: Job? = null
    private var searchJob: Job? = null

    init {
        // 1. Observe active connected device from AudioDeviceEngine
        viewModelScope.launch {
            audioDeviceEngine.activeDevice.collect { device ->
                _uiState.update { it.copy(connectedDeviceName = device.displayName) }
            }
        }

        // 2. Observe acoustic environment mode
        viewModelScope.launch {
            playbackPrefs.acousticEnvironmentMode.collect { savedName ->
                val mode = runCatching { AcousticEnvironmentMode.valueOf(savedName) }
                    .getOrDefault(AcousticEnvironmentMode.STUDIO)
                _uiState.update { it.copy(environmentMode = mode) }
            }
        }

        // 3. Compute initial visual curve and dispatch initial DSP parameters
        recomputeCurveAndTelemetry()
        dispatchDspNodes()

        // 4. Preload top AutoEq headphones for quick browsing
        searchAutoEq("")
    }

    // --- Master & Mode Toggles ---

    fun toggleMasterEq(enabled: Boolean) {
        _uiState.update { it.copy(isEqEnabled = enabled) }
        recomputeCurveAndTelemetry()
        dispatchDspNodes()
    }

    fun setBandMode(mode: EqualizerBandMode) {
        if (_uiState.value.bandMode == mode) return

        val newBands = when (mode) {
            EqualizerBandMode.FIVE_BAND -> COMPACT_5_BANDS
            EqualizerBandMode.TEN_BAND -> ISO_10_BANDS
            EqualizerBandMode.AUTOEQ_PARAMETRIC -> _uiState.value.bands
        }

        _uiState.update {
            it.copy(
                bandMode = mode,
                bands = newBands,
                selectedBandIndex = 0,
                activePresetName = if (mode == EqualizerBandMode.AUTOEQ_PARAMETRIC) it.activePresetName else EqualizerPresets.FLAT
            )
        }
        recomputeCurveAndTelemetry()
        dispatchDspNodes()
    }

    fun selectStudioTab(tab: EqualizerStudioTab) {
        _uiState.update { it.copy(studioTab = tab) }
    }

    fun setSelectedBand(index: Int) {
        if (index in _uiState.value.bands.indices) {
            _uiState.update { it.copy(selectedBandIndex = index) }
        }
    }

    // --- Band Parameter Tweaks ---

    fun updateBandGain(bandIndex: Int, gainDb: Float) {
        val currentBands = _uiState.value.bands.toMutableList()
        if (bandIndex !in currentBands.indices) return

        val clampedGain = gainDb.coerceIn(-15f, 15f)
        val roundedGain = (kotlin.math.round(clampedGain * 10f) / 10f)
        val updatedBand = currentBands[bandIndex].copy(gainDb = roundedGain)
        currentBands[bandIndex] = updatedBand

        _uiState.update {
            it.copy(
                bands = currentBands,
                activePresetName = "Custom"
            )
        }
        recomputeCurveAndTelemetry()
        dispatchDspNodes()
    }

    fun stepBandGain(bandIndex: Int, deltaDb: Float) {
        val currentBands = _uiState.value.bands
        if (bandIndex !in currentBands.indices) return
        val newGain = currentBands[bandIndex].gainDb + deltaDb
        updateBandGain(bandIndex, newGain)
    }

    fun updateBandFrequency(bandIndex: Int, freqHz: Float) {
        val currentBands = _uiState.value.bands.toMutableList()
        if (bandIndex !in currentBands.indices) return

        val clampedFreq = freqHz.coerceIn(20f, 20_000f)
        val label = if (clampedFreq >= 1000f) {
            String.format(Locale.US, "%.1f kHz", clampedFreq / 1000f)
        } else {
            String.format(Locale.US, "%.0f Hz", clampedFreq)
        }

        currentBands[bandIndex] = currentBands[bandIndex].copy(
            frequencyHz = clampedFreq,
            label = label
        )

        _uiState.update {
            it.copy(
                bands = currentBands,
                activePresetName = "Custom"
            )
        }
        recomputeCurveAndTelemetry()
        dispatchDspNodes()
    }

    fun updateBandQ(bandIndex: Int, q: Float) {
        val currentBands = _uiState.value.bands.toMutableList()
        if (bandIndex !in currentBands.indices) return

        val clampedQ = q.coerceIn(0.1f, 10f)
        currentBands[bandIndex] = currentBands[bandIndex].copy(qFactor = clampedQ)

        _uiState.update {
            it.copy(
                bands = currentBands,
                activePresetName = "Custom"
            )
        }
        recomputeCurveAndTelemetry()
        dispatchDspNodes()
    }

    fun updateBandFilterType(bandIndex: Int, type: FilterType) {
        val currentBands = _uiState.value.bands.toMutableList()
        if (bandIndex !in currentBands.indices) return

        currentBands[bandIndex] = currentBands[bandIndex].copy(filterType = type)

        _uiState.update {
            it.copy(
                bands = currentBands,
                activePresetName = "Custom"
            )
        }
        recomputeCurveAndTelemetry()
        dispatchDspNodes()
    }

    fun toggleBandEnabled(bandIndex: Int) {
        val currentBands = _uiState.value.bands.toMutableList()
        if (bandIndex !in currentBands.indices) return

        val band = currentBands[bandIndex]
        currentBands[bandIndex] = band.copy(isEnabled = !band.isEnabled)

        _uiState.update { it.copy(bands = currentBands) }
        recomputeCurveAndTelemetry()
        dispatchDspNodes()
    }

    fun setPreamp(preampDb: Float) {
        val clamped = preampDb.coerceIn(-15f, 6f)
        _uiState.update { it.copy(preampDb = clamped) }
        dispatchDspNodes()
    }

    // --- Preset Handling ---

    fun applyPreset(presetName: String) {
        val state = _uiState.value
        val newBands = when (state.bandMode) {
            EqualizerBandMode.FIVE_BAND -> {
                val gains = EqualizerPresets.FIVE_BAND_GAINS[presetName] ?: return
                COMPACT_5_BANDS.mapIndexed { idx, band ->
                    band.copy(gainDb = gains.getOrElse(idx) { 0f })
                }
            }
            EqualizerBandMode.TEN_BAND -> {
                val gains = EqualizerPresets.TEN_BAND_GAINS[presetName] ?: return
                ISO_10_BANDS.mapIndexed { idx, band ->
                    band.copy(gainDb = gains.getOrElse(idx) { 0f })
                }
            }
            EqualizerBandMode.AUTOEQ_PARAMETRIC -> {
                // If in custom AutoEq mode, switch back to 10-band ISO preset
                val gains = EqualizerPresets.TEN_BAND_GAINS[presetName] ?: return
                ISO_10_BANDS.mapIndexed { idx, band ->
                    band.copy(gainDb = gains.getOrElse(idx) { 0f })
                }
            }
        }

        _uiState.update {
            it.copy(
                bandMode = if (it.bandMode == EqualizerBandMode.AUTOEQ_PARAMETRIC) EqualizerBandMode.TEN_BAND else it.bandMode,
                bands = newBands,
                activePresetName = presetName,
                autoEqModel = null,
                autoEqSource = null
            )
        }
        recomputeCurveAndTelemetry()
        dispatchDspNodes()
    }

    fun resetToFlat() {
        applyPreset(EqualizerPresets.FLAT)
    }

    // --- AutoEq Integration ---

    fun searchAutoEq(query: String) {
        _uiState.update { it.copy(autoEqQuery = query, isSearchingAutoEq = true) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch(ioDispatcher) {
            delay(150)
            val results = autoEqRepository.searchHeadphones(query)
            _uiState.update { it.copy(autoEqSearchResults = results, isSearchingAutoEq = false) }
        }
    }

    fun applyAutoEqProfile(item: HeadphoneSearchResult) {
        viewModelScope.launch(ioDispatcher) {
            _uiState.update { it.copy(statusMessage = "Loading ${item.modelName}...") }
            val specsResult = autoEqRepository.fetchHeadphoneSpecs(item)
            if (specsResult.isSuccess) {
                val specs = specsResult.getOrThrow()
                loadSpecsIntoEngine(specs)
            } else {
                _uiState.update { it.copy(statusMessage = "Failed to load AutoEq profile") }
            }
        }
    }

    fun importRawAutoEq(rawText: String, profileName: String = "Imported AutoEq") {
        val parsedSpecs = parseRawAutoEq(rawText, profileName)
        if (parsedSpecs.bands.isNotEmpty()) {
            loadSpecsIntoEngine(parsedSpecs)
        } else {
            _uiState.update { it.copy(statusMessage = "No valid EQ filters found in text") }
        }
    }

    private fun loadSpecsIntoEngine(specs: HeadphoneSpecs) {
        val colors = listOf(
            Color(0xFFFF007F), Color(0xFFFF3366), Color(0xFFFF6633), Color(0xFFFF9900),
            Color(0xFFFFD500), Color(0xFF67F7B1), Color(0xFF39FF14), Color(0xFF00FFCC),
            Color(0xFF00E5FF), Color(0xFF8B5CF6), Color(0xFFC084FC), Color(0xFFF43F5E)
        )

        val parametricBands = specs.bands.mapIndexed { idx, autoBand ->
            val filterType = when (autoBand.filterType.uppercase()) {
                "LOW_SHELF", "LSC", "LS" -> FilterType.LOW_SHELF
                "HIGH_SHELF", "HSC", "HS" -> FilterType.HIGH_SHELF
                else -> FilterType.PEAKING
            }

            val freq = autoBand.frequencyHz
            val label = if (freq >= 1000f) {
                String.format(Locale.US, "%.1fk", freq / 1000f)
            } else {
                String.format(Locale.US, "%.0f", freq)
            }

            ParametricBand(
                id = "autoeq_$idx",
                label = label,
                frequencyHz = freq,
                gainDb = autoBand.gainDb,
                qFactor = autoBand.q,
                filterType = filterType,
                isEnabled = autoBand.isEnabled,
                color = colors[idx % colors.size]
            )
        }

        _uiState.update {
            it.copy(
                bandMode = EqualizerBandMode.AUTOEQ_PARAMETRIC,
                bands = parametricBands,
                selectedBandIndex = 0,
                preampDb = specs.preampDb,
                activePresetName = "${specs.modelName} (${specs.source})",
                autoEqModel = specs.modelName,
                autoEqSource = specs.source,
                targetCurve = specs.targetCurve,
                statusMessage = "Applied AutoEq: ${specs.modelName}"
            )
        }

        recomputeCurveAndTelemetry()
        dispatchDspNodes()
    }

    // --- Acoustic Environment ---

    fun setAcousticEnvironment(mode: AcousticEnvironmentMode) {
        adaptiveDspEngine.setAcousticEnvironmentMode(mode)
        _uiState.update { it.copy(environmentMode = mode) }
        viewModelScope.launch {
            playbackPrefs.saveAcousticEnvironmentMode(mode.name)
        }
    }

    // --- Engine Dispatch & Response Curve ---

    private fun dispatchDspNodes() {
        dspDispatchJob?.cancel()
        dspDispatchJob = viewModelScope.launch(defaultDispatcher) {
            delay(120) // Debounce hardware communication
            val state = _uiState.value
            val acousticNodes = if (state.isEqEnabled) {
                state.bands.map { it.toAcousticNode() }
            } else {
                emptyList()
            }
            adaptiveDspEngine.updateAcousticNodes(acousticNodes)
        }
    }

    private fun recomputeCurveAndTelemetry() {
        curveComputationJob?.cancel()
        curveComputationJob = viewModelScope.launch(defaultDispatcher) {
            val state = _uiState.value
            val sampleRate = adaptiveDspEngine.getSampleRate().toFloat()

            val activeNodes = if (state.isEqEnabled) {
                state.bands.filter { it.isEnabled }.map { it.toAcousticNode() }
            } else {
                emptyList()
            }

            val peakDb = if (activeNodes.isNotEmpty()) {
                BiquadDesigner.calculateCompositePeakGainDb(activeNodes, sampleRate)
            } else {
                0f
            }

            val autoMakeup = if (peakDb > 0.001f) {
                10f.pow(-peakDb / 20f)
            } else {
                1.0f
            }

            // Design biquads for UI frequency curve
            val coeffs = activeNodes.map { node ->
                BiquadDesigner.design(
                    type = node.filterType,
                    freq = node.frequency,
                    gainDb = node.gainDb,
                    q = node.qFactor,
                    sampleRate = sampleRate
                )
            }

            val points = mutableListOf<Offset>()
            val numSteps = 128
            val minLog = log10(20.0)
            val maxLog = log10(20000.0)
            val rangeLog = maxLog - minLog

            for (i in 0..numSteps) {
                val fraction = i.toFloat() / numSteps
                val freq = 10.0.pow(minLog + fraction * rangeLog).toFloat()

                var totalGainDb = state.preampDb
                if (state.isEqEnabled) {
                    coeffs.forEach { c ->
                        totalGainDb += BiquadDesigner.magnitudeAt(freq, c, sampleRate)
                    }
                }

                // Map to normalized range: center 0 dB is 0f, +15 dB is -1f, -15 dB is +1f
                val normY = -(totalGainDb / 15f).coerceIn(-1.2f, 1.2f)
                points.add(Offset(fraction, normY))
            }

            _uiState.update {
                it.copy(
                    compositePeakDb = peakDb,
                    autoMakeupGain = autoMakeup,
                    frequencyCurvePoints = points
                )
            }
        }
    }

    private fun parseRawAutoEq(rawText: String, profileName: String): HeadphoneSpecs {
        var preamp = 0f
        val bands = mutableListOf<com.tigerplayer.data.model.AutoEqBand>()

        val lines = rawText.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("Preamp:", ignoreCase = true)) {
                preamp = trimmed.substringAfter(":")
                    .substringBefore("dB")
                    .trim()
                    .replace(',', '.')
                    .toFloatOrNull() ?: 0f
            } else if (trimmed.startsWith("Filter", ignoreCase = true)) {
                runCatching {
                    val parts = trimmed.split(Regex("\\s+"))
                    // Example format: Filter 1: ON PK Fc 28 Hz Gain 6.5 dB Q 1.41
                    val type = when {
                        parts.contains("LSC") || parts.contains("LS") -> "LOW_SHELF"
                        parts.contains("HSC") || parts.contains("HS") -> "HIGH_SHELF"
                        else -> "PEAKING"
                    }

                    val fcIdx = parts.indexOfFirst { it.equals("Fc", ignoreCase = true) }
                    val gainIdx = parts.indexOfFirst { it.equals("Gain", ignoreCase = true) }
                    val qIdx = parts.indexOfFirst { it.equals("Q", ignoreCase = true) }

                    val freq = if (fcIdx != -1 && fcIdx + 1 < parts.size) {
                        parts[fcIdx + 1].replace(',', '.').toFloat()
                    } else 1000f

                    val gain = if (gainIdx != -1 && gainIdx + 1 < parts.size) {
                        parts[gainIdx + 1].replace(',', '.').toFloat()
                    } else 0f

                    val q = if (qIdx != -1 && qIdx + 1 < parts.size) {
                        parts[qIdx + 1].replace(',', '.').toFloat()
                    } else 1.41f

                    val isEnabled = !trimmed.contains("OFF", ignoreCase = true)

                    bands.add(
                        com.tigerplayer.data.model.AutoEqBand(
                            filterType = type,
                            frequencyHz = freq,
                            gainDb = gain,
                            q = q,
                            isEnabled = isEnabled
                        )
                    )
                }
            }
        }

        return HeadphoneSpecs(
            modelName = profileName,
            source = "Custom Import",
            targetCurve = "Parametric",
            formFactor = "Headphone",
            preampDb = preamp,
            bands = bands,
            rawProfileText = rawText
        )
    }
}

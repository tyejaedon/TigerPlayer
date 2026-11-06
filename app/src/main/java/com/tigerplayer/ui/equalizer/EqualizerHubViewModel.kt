package com.tigerplayer.ui.equalizer

import androidx.annotation.OptIn
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.tigerplayer.data.local.CoverScreenEqPreset
import com.tigerplayer.data.local.PlaybackPrefs
import com.tigerplayer.data.local.SettingsDataStore
import com.tigerplayer.data.model.HeadphoneSearchResult
import com.tigerplayer.data.model.HeadphoneSpecs
import com.tigerplayer.data.repository.AutoEqRepository
import com.tigerplayer.engine.AcousticEnvironmentMode
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round

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
    private var activeAutoEqItem: HeadphoneSearchResult? = null

    init {
        // 1. Observe active connected device & check for AutoEq match
        viewModelScope.launch {
            audioDeviceEngine.activeDevice.collect { device ->
                _uiState.update { it.copy(connectedDeviceName = device.displayName) }
                if (device.displayName.isNotBlank()) {
                    val matched = autoEqRepository.findBestMatch(device.displayName)
                    if (matched != null && _uiState.value.autoEqModel == null) {
                        _uiState.update {
                            it.copy(statusMessage = "Detected ${matched.modelName} profile available")
                        }
                    }
                }
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

        // 3. Observe local AutoEq catalog flow so initial list populates as soon as JSON finishes indexing
        viewModelScope.launch(defaultDispatcher) {
            autoEqRepository.getAvailableHeadphonesFlow().collect { catalog ->
                if (_uiState.value.autoEqQuery.isBlank() && catalog.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            autoEqSearchResults = catalog.take(MAX_UI_SEARCH_RESULTS),
                            isSearchingAutoEq = false
                        )
                    }
                }
            }
        }

        // 4. Observe real-time audio reactive frame for spatial and studio visualizer dynamics
        viewModelScope.launch {
            adaptiveDspEngine.audioReactiveFrame.collect { frame ->
                _uiState.update { it.copy(audioReactiveFrame = frame) }
            }
        }

        // 5. Load saved cover screen / spatial EQ mood preset
        viewModelScope.launch(ioDispatcher) {
            val savedPreset = settingsDataStore.settingsFlow.first().coverScreenEqPreset
            applySpatialMoodInternal(savedPreset.displayName, saveToSettings = false)
        }

        // 6. Compute initial visual curve and dispatch initial DSP parameters
        recomputeCurveAndTelemetry()
        dispatchDspNodes()
    }

    // --- Master & Mode Toggles ---

    fun toggleMasterEq(enabled: Boolean) {
        _uiState.update { it.copy(isEqEnabled = enabled) }
        recomputeCurveAndTelemetry()
        dispatchDspNodes()
    }

    fun setBandMode(mode: EqualizerBandMode) {
        if (_uiState.value.bandMode == mode) return

        val currentAutoEq = activeAutoEqItem
        if (currentAutoEq != null) {
            // Re-apply the active headphone profile in the newly chosen mode
            when (mode) {
                EqualizerBandMode.AUTOEQ_PARAMETRIC -> applyAutoEqProfile(currentAutoEq, forceParametric = true)
                EqualizerBandMode.TEN_BAND -> applyAutoEqAsGraphic(currentAutoEq, EqualizerBandMode.TEN_BAND)
                EqualizerBandMode.FIVE_BAND -> applyAutoEqAsGraphic(currentAutoEq, EqualizerBandMode.FIVE_BAND)
            }
            return
        }

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
                activePresetName = EqualizerPresets.FLAT
            )
        }
        recomputeCurveAndTelemetry()
        dispatchDspNodes()
    }

    fun selectStudioTab(tab: EqualizerStudioTab) {
        _uiState.update { it.copy(studioTab = tab) }
    }

    fun setActiveEngineSource(source: EqualizerEngineSource) {
        if (_uiState.value.activeEngineSource == source) return
        _uiState.update { it.copy(activeEngineSource = source) }
        recomputeCurveAndTelemetry()
        dispatchDspNodes()
    }

    // --- Spatial Nexus Gestures & Moods ---

    fun moveSpatialNode(nodeId: String, newSpatialPos: Offset) {
        val clamped = Offset(newSpatialPos.x.coerceIn(-1f, 1f), newSpatialPos.y.coerceIn(-1f, 1f))
        val updatedNodes = _uiState.value.spatialNodes.map {
            if (it.id == nodeId) it.copy(spatialPos = clamped) else it
        }
        _uiState.update {
            it.copy(
                spatialNodes = updatedNodes,
                spatialMood = "Custom Shape",
                activeEngineSource = EqualizerEngineSource.SPATIAL_NODES
            )
        }
        recomputeCurveAndTelemetry()
        dispatchDspNodes()
    }

    fun setSpatialMoodPreset(mood: String) {
        applySpatialMoodInternal(mood, saveToSettings = true)
        _uiState.update { it.copy(activeEngineSource = EqualizerEngineSource.SPATIAL_NODES) }
        recomputeCurveAndTelemetry()
        dispatchDspNodes()
    }

    private fun applySpatialMoodInternal(mood: String, saveToSettings: Boolean) {
        val nodes = DEFAULT_SPATIAL_NODES
        val updated = when (mood) {
            "Night Drive" -> nodes.map { n ->
                when (n.id) {
                    "sub" -> n.copy(spatialPos = Offset(-0.6f, -0.6f))
                    "air" -> n.copy(spatialPos = Offset(0.6f, -0.4f))
                    "warmth" -> n.copy(spatialPos = Offset(-0.3f, 0.2f))
                    else -> n
                }
            }
            "Pure Vocal" -> nodes.map { n ->
                when (n.id) {
                    "presence" -> n.copy(spatialPos = Offset(0.2f, -0.7f))
                    "sub" -> n.copy(spatialPos = Offset(-0.6f, 0.3f))
                    else -> n
                }
            }
            "Studio Flat" -> nodes.map { n ->
                n.copy(spatialPos = Offset(n.spatialPos.x, 0f))
            }
            else -> nodes
        }
        _uiState.update { it.copy(spatialNodes = updated, spatialMood = mood) }
        if (saveToSettings) {
            CoverScreenEqPreset.entries.firstOrNull { it.displayName == mood }?.let { preset ->
                viewModelScope.launch(ioDispatcher) { settingsDataStore.setCoverScreenEqPreset(preset) }
            }
        }
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
        val roundedGain = round(clampedGain * 10f) / 10f
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
        recomputeCurveAndTelemetry()
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

    // --- AutoEq Integration (Local Bundled Asset Index) ---

    fun searchAutoEq(query: String) {
        _uiState.update { it.copy(autoEqQuery = query, isSearchingAutoEq = true) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch(defaultDispatcher) {
            // Fast 60ms debounce since search is completely in-memory
            if (query.isNotBlank()) delay(60)
            val results = autoEqRepository.searchHeadphones(query)
            _uiState.update {
                it.copy(
                    autoEqSearchResults = results.take(MAX_UI_SEARCH_RESULTS),
                    isSearchingAutoEq = false
                )
            }
        }
    }

    fun applyAutoEqProfile(
        item: HeadphoneSearchResult,
        forceParametric: Boolean = false
    ) {
        activeAutoEqItem = item
        val currentMode = _uiState.value.bandMode

        if (!forceParametric && (currentMode == EqualizerBandMode.TEN_BAND || currentMode == EqualizerBandMode.FIVE_BAND)) {
            applyAutoEqAsGraphic(item, currentMode)
            return
        }

        viewModelScope.launch(defaultDispatcher) {
            val specsResult = autoEqRepository.fetchHeadphoneSpecs(item)
            specsResult.onSuccess { specs ->
                loadSpecsIntoEngine(specs)
            }.onFailure {
                _uiState.update { it.copy(statusMessage = "Failed to load ${item.modelName}") }
            }
        }
    }
    fun applyAutoEqAsGraphic(
        item: HeadphoneSearchResult,
        targetMode: EqualizerBandMode = EqualizerBandMode.TEN_BAND
    ) {
        activeAutoEqItem = item
        viewModelScope.launch(defaultDispatcher) {
            val templateBands = if (targetMode == EqualizerBandMode.FIVE_BAND) {
                COMPACT_5_BANDS
            } else {
                ISO_10_BANDS
            }

            val targetFreqs = templateBands.map { it.frequencyHz }
            val result = autoEqRepository.fetchGraphicEqGains(item, targetFreqs)

            result.onSuccess { (preampDb, gains) ->
                val mappedBands = templateBands.mapIndexed { idx, band ->
                    band.copy(
                        gainDb = gains.getOrElse(idx) { 0f },
                        isEnabled = true
                    )
                }

                _uiState.update {
                    it.copy(
                        bandMode = targetMode,
                        bands = mappedBands,
                        selectedBandIndex = 0,
                        preampDb = preampDb.coerceIn(-15f, 6f),
                        activePresetName = "${item.modelName} (${item.source})",
                        autoEqModel = item.modelName,
                        autoEqSource = item.source,
                        statusMessage = "Applied ${targetBandsLabel(targetMode)} AutoEq: ${item.modelName}"
                    )
                }

                recomputeCurveAndTelemetry()
                dispatchDspNodes()
            }.onFailure {
                _uiState.update { it.copy(statusMessage = "Failed to map graphic curve for ${item.modelName}") }
            }
        }
    }

    private fun targetBandsLabel(mode: EqualizerBandMode): String = when (mode) {
        EqualizerBandMode.FIVE_BAND -> "5-Band"
        EqualizerBandMode.TEN_BAND -> "10-Band"
        EqualizerBandMode.AUTOEQ_PARAMETRIC -> "Parametric"
    }
    fun autoMatchConnectedDevice() {
        val currentDevice = _uiState.value.connectedDeviceName
        if (currentDevice.isBlank()) {
            _uiState.update { it.copy(statusMessage = "No active audio device detected") }
            return
        }
        viewModelScope.launch(defaultDispatcher) {
            val bestMatch = autoEqRepository.findBestMatch(currentDevice)
            if (bestMatch != null) {
                applyAutoEqProfile(bestMatch)
            } else {
                _uiState.update { it.copy(statusMessage = "No AutoEq match for \"$currentDevice\"") }
            }
        }
    }

    fun importRawAutoEq(rawText: String, profileName: String = "Imported AutoEq") {
        val parsedSpecs = autoEqRepository.parseParametricEqText(
            rawText = rawText,
            modelName = profileName,
            source = "Custom Import",
            target = "Parametric",
            form = "Headphone"
        )
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
            val filterType = when (autoBand.filterType.uppercase(Locale.US)) {
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
                preampDb = specs.preampDb.coerceIn(-15f, 6f),
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
        viewModelScope.launch(ioDispatcher) {
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
                when (state.activeEngineSource) {
                    EqualizerEngineSource.PARAMETRIC_BANDS -> {
                        state.bands.filter { it.isEnabled }.map { it.toAcousticNode() }
                    }
                    EqualizerEngineSource.SPATIAL_NODES -> {
                        state.spatialNodes.map { it.toAcousticNode() }
                    }
                }
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
                when (state.activeEngineSource) {
                    EqualizerEngineSource.PARAMETRIC_BANDS -> {
                        state.bands.filter { it.isEnabled }.map { it.toAcousticNode() }
                    }
                    EqualizerEngineSource.SPATIAL_NODES -> {
                        state.spatialNodes.map { it.toAcousticNode() }
                    }
                }
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

            val points = ArrayList<Offset>(NUM_CURVE_STEPS + 1)
            val minLog = log10(20.0)
            val maxLog = log10(20000.0)
            val rangeLog = maxLog - minLog

            for (i in 0..NUM_CURVE_STEPS) {
                val fraction = i.toFloat() / NUM_CURVE_STEPS
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

    companion object {
        private const val MAX_UI_SEARCH_RESULTS = 100
        private const val NUM_CURVE_STEPS = 128
    }
}

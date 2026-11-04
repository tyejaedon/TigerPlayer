package com.tigerplayer.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import com.tigerplayer.data.model.AutoEqBand
import com.tigerplayer.data.model.HeadphoneSearchResult
import com.tigerplayer.data.model.HeadphoneSpecs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.collections.first
import kotlin.collections.indexOfFirst
import kotlin.collections.isNotEmpty

@Singleton
class AutoEqRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) : AutoEqRepository {

    private val repositoryScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val loadMutex = Mutex()
    private val _modelsFlow = MutableStateFlow<List<HeadphoneSearchResult>>(emptyList())
    private val presetsMap = ConcurrentHashMap<String, ParametricPresetJson>()
    private var isLoaded = false
    private val graphicPresetsMap = ConcurrentHashMap<String, GraphicPresetJson>()
    private val graphicLoadMutex = Mutex()
    private var isGraphicLoaded = false

    init {
        // Asynchronously warm the asset cache on startup
        repositoryScope.launch {
            ensureLoaded()
        }
    }

    private suspend fun ensureLoaded(): List<HeadphoneSearchResult> = withContext(Dispatchers.IO) {
        if (isLoaded && _modelsFlow.value.isNotEmpty()) {
            return@withContext _modelsFlow.value
        }

        loadMutex.withLock {
            if (isLoaded && _modelsFlow.value.isNotEmpty()) {
                return@withContext _modelsFlow.value
            }

            try {
                context.assets.open(ASSET_PARAMETRIC_JSON).use { inputStream ->
                    InputStreamReader(inputStream, Charsets.UTF_8).use { reader ->
                        val listType = object : TypeToken<List<ParametricPresetJson>>() {}.type
                        val parsedPresets: List<ParametricPresetJson> = gson.fromJson(reader, listType) ?: emptyList()

                        val searchResults = ArrayList<HeadphoneSearchResult>(parsedPresets.size)

                        for (preset in parsedPresets) {
                            val id = "${preset.source}:${preset.model}"
                            presetsMap[id] = preset

                            searchResults.add(
                                HeadphoneSearchResult(
                                    id = id,
                                    modelName = preset.model,
                                    source = preset.source,
                                    targetCurve = "Harman",
                                    formFactor = "Headphone",
                                    path = ""
                                )
                            )
                        }

                        _modelsFlow.value = searchResults
                        isLoaded = true
                        Log.d(TAG, "Successfully indexed ${searchResults.size} AutoEq profiles from assets.")
                        searchResults
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Failed to load bundled AutoEq JSON: $ASSET_PARAMETRIC_JSON", e)
                emptyList()
            }
        }
    }

    override fun getAvailableHeadphonesFlow(): Flow<List<HeadphoneSearchResult>> {
        if (!isLoaded) {
            repositoryScope.launch {
                try {
                    ensureLoaded()
                } catch (_: Exception) {}
            }
        }
        return _modelsFlow.asStateFlow()
    }

    override suspend fun searchHeadphones(query: String): List<HeadphoneSearchResult> = withContext(Dispatchers.Default) {
        val all = ensureLoaded()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext all

        val tokens = trimmed.lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }
        all.filter { item ->
            val targetStr = "${item.modelName} ${item.source}".lowercase()
            tokens.all { token -> targetStr.contains(token) }
        }
    }

    override suspend fun findBestMatch(deviceName: String): HeadphoneSearchResult? = withContext(Dispatchers.Default) {
        val all = ensureLoaded()
        if (all.isEmpty()) return@withContext null

        val cleaned = cleanDeviceName(deviceName)
        if (cleaned.isBlank()) return@withContext null

        // 1. Exact match (case insensitive)
        all.firstOrNull { it.modelName.equals(cleaned, ignoreCase = true) }?.let { return@withContext it }

        // 2. Substring or token overlap
        val cleanedTokens = cleaned.lowercase().split("[^a-z0-9]+".toRegex()).filter { it.isNotBlank() && it.length > 1 }
        if (cleanedTokens.isEmpty()) return@withContext null

        all.maxByOrNull { item ->
            val itemTokens = item.modelName.lowercase().split("[^a-z0-9]+".toRegex()).filter { it.isNotBlank() }
            val matchedTokens = cleanedTokens.count { token -> itemTokens.contains(token) }
            if (item.modelName.contains(cleaned, ignoreCase = true)) matchedTokens + 10 else matchedTokens
        }?.takeIf { candidate ->
            val candidateTokens = candidate.modelName.lowercase().split("[^a-z0-9]+".toRegex()).filter { it.isNotBlank() }
            cleanedTokens.any { candidateTokens.contains(it) }
        }
    }

    override suspend fun fetchHeadphoneSpecs(item: HeadphoneSearchResult): Result<HeadphoneSpecs> = withContext(Dispatchers.Default) {
        ensureLoaded()

        val cachedPreset = presetsMap[item.id]
            ?: presetsMap["${item.source}:${item.modelName}"]
            ?: presetsMap.values.firstOrNull { it.model.equals(item.modelName, ignoreCase = true) }

        if (cachedPreset != null) {
            val bands = cachedPreset.filters.map { filter ->
                AutoEqBand(
                    filterType = filter.type,
                    frequencyHz = filter.fc.toFloat(),
                    gainDb = filter.gain.toFloat(),
                    q = filter.q.toFloat()
                )
            }

            val rawProfileBuilder = StringBuilder().apply {
                append("Preamp: ${cachedPreset.preamp} dB\n")
                cachedPreset.filters.forEachIndexed { i, f ->
                    append("Filter ${i + 1}: ON ${f.type} Fc ${f.fc} Hz Gain ${f.gain} dB Q ${f.q}\n")
                }
            }

            val specs = HeadphoneSpecs(
                modelName = cachedPreset.model,
                source = cachedPreset.source,
                targetCurve = item.targetCurve,
                formFactor = item.formFactor,
                preampDb = cachedPreset.preamp.toFloat(),
                bands = bands,
                rawProfileText = rawProfileBuilder.toString().trim()
            )

            return@withContext Result.success(specs)
        }

        // Fallback for models without pre-computed parametric profiles
        Result.success(createFallbackSpecs(item))
    }
    private suspend fun ensureGraphicLoaded() = withContext(Dispatchers.IO) {
        if (isGraphicLoaded && graphicPresetsMap.isNotEmpty()) return@withContext

        graphicLoadMutex.withLock {
            if (isGraphicLoaded && graphicPresetsMap.isNotEmpty()) return@withContext
            try {
                context.assets.open(ASSET_GRAPHIC_JSON).use { inputStream ->
                    InputStreamReader(inputStream, Charsets.UTF_8).use { reader ->
                        val listType = object : TypeToken<List<GraphicPresetJson>>() {}.type
                        val parsed: List<GraphicPresetJson> = gson.fromJson(reader, listType) ?: emptyList()
                        for (preset in parsed) {
                            val id = "${preset.source}:${preset.model}"
                            graphicPresetsMap[id] = preset
                        }
                        isGraphicLoaded = true
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e(TAG, "Failed to load bundled GraphicEQ JSON: $ASSET_GRAPHIC_JSON", e)
            }
        }
    }

    override suspend fun fetchGraphicEqGains(
        item: HeadphoneSearchResult,
        targetFrequenciesHz: List<Float>
    ): Result<Pair<Float, List<Float>>> = withContext(Dispatchers.Default) {
        ensureGraphicLoaded()

        val preset = graphicPresetsMap[item.id]
            ?: graphicPresetsMap["${item.source}:${item.modelName}"]
            ?: graphicPresetsMap.values.firstOrNull { it.model.equals(item.modelName, ignoreCase = true) }

        if (preset != null && preset.points.isNotEmpty()) {
            val sortedPoints = preset.points.sortedBy { it.f }
            val interpolatedGains = targetFrequenciesHz.map { targetFreq ->
                interpolateLogFrequency(sortedPoints, targetFreq)
            }
            val maxGain = interpolatedGains.maxOrNull() ?: 0f
            val safePreamp = if (maxGain > 0f) -(kotlin.math.round(maxGain * 10f) / 10f) else 0f

            return@withContext Result.success(safePreamp to interpolatedGains)
        }

        // Fallback: If GraphicEQ isn't found, synthesize gains from the ParametricEQ profile
        val specsResult = fetchHeadphoneSpecs(item)
        if (specsResult.isSuccess) {
            val specs = specsResult.getOrThrow()
            val sampleRate = 48000f
            val coeffs = specs.bands.map { band ->
                val type = when (band.filterType.uppercase()) {
                    "LOW_SHELF", "LSC", "LS" -> com.tigerplayer.engine.FilterType.LOW_SHELF
                    "HIGH_SHELF", "HSC", "HS" -> com.tigerplayer.engine.FilterType.HIGH_SHELF
                    else -> com.tigerplayer.engine.FilterType.PEAKING
                }
                com.tigerplayer.utils.BiquadDesigner.design(
                    type = type,
                    freq = band.frequencyHz,
                    gainDb = band.gainDb,
                    q = band.q,
                    sampleRate = sampleRate
                )
            }
            val synthesizedGains = targetFrequenciesHz.map { freq ->
                var totalDb = 0f
                coeffs.forEach { c ->
                    totalDb += com.tigerplayer.utils.BiquadDesigner.magnitudeAt(freq, c, sampleRate)
                }
                (kotlin.math.round(totalDb.coerceIn(-15f, 15f) * 10f) / 10f)
            }
            return@withContext Result.success(specs.preampDb to synthesizedGains)
        }

        Result.failure(NoSuchElementException("GraphicEQ preset not found for ${item.modelName}"))
    }

    override fun parseParametricEqText(
        rawText: String,
        modelName: String,
        source: String,
        target: String,
        form: String
    ): HeadphoneSpecs {
        var preamp = 0f
        val bands = mutableListOf<AutoEqBand>()

        for (line in rawText.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("Preamp:")) {
                preamp = trimmed.substringAfter("Preamp:")
                    .substringBefore("dB")
                    .trim()
                    .replace(',', '.')
                    .toFloatOrNull() ?: 0f
            } else if (trimmed.startsWith("Filter")) {
                try {
                    val parts = trimmed.split(Regex("\\s+"))
                    val type = parts[3]
                    val fcIndex = parts.indexOf("Fc") + 1
                    val freq = parts[fcIndex].replace(',', '.').toFloat()
                    val gainIndex = parts.indexOf("Gain") + 1
                    val gain = parts[gainIndex].replace(',', '.').toFloat()
                    val qIndex = parts.indexOf("Q") + 1
                    val q = if (qIndex > 0 && qIndex < parts.size) parts[qIndex].replace(',', '.').toFloat() else 1.0f

                    bands.add(AutoEqBand(filterType = type, frequencyHz = freq, gainDb = gain, q = q))
                } catch (_: Exception) {}
            }
        }

        return HeadphoneSpecs(
            modelName = modelName,
            source = source,
            targetCurve = target,
            formFactor = form,
            preampDb = preamp,
            bands = bands,
            rawProfileText = rawText
        )
    }

    /**
     * Interpolates gain on a logarithmic frequency scale (since human pitch perception and
     * octave spacing are logarithmic).
     */
    private fun interpolateLogFrequency(points: List<GraphicPointJson>, targetFreq: Float): Float {
        if (points.isEmpty()) return 0f
        if (targetFreq <= points.first().f) return points.first().g.toFloat()
        if (targetFreq >= points.last().f) return points.last().g.toFloat()

        // Binary search or linear scan for bounding points
        var upperIdx = points.indexOfFirst { it.f >= targetFreq }
        if (upperIdx <= 0) upperIdx = 1

        val p0 = points[upperIdx - 1]
        val p1 = points[upperIdx]

        if (p0.f == p1.f) return p0.g.toFloat()

        val logTarget = kotlin.math.ln(targetFreq.toDouble())
        val logF0 = kotlin.math.ln(p0.f.toDouble())
        val logF1 = kotlin.math.ln(p1.f.toDouble())

        val t = ((logTarget - logF0) / (logF1 - logF0)).coerceIn(0.0, 1.0)
        val interpolated = p0.g + t * (p1.g - p0.g)

        return (kotlin.math.round(interpolated.toFloat() * 10f) / 10f).coerceIn(-15f, 15f)
    }

    private fun cleanDeviceName(name: String): String {
        return name
            .replace(Regex("(?i)\\b(le-|wireless|bluetooth|bt|anc|pro|plus|headset|earbuds|hands-free)\\b"), " ")
            .replace(Regex("[^a-zA-Z0-9 -]"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    private fun createFallbackSpecs(item: HeadphoneSearchResult): HeadphoneSpecs {
        val bands = listOf(
            AutoEqBand("LSC", 105f, 4.5f, 0.71f),
            AutoEqBand("PK", 200f, -1.2f, 1.4f),
            AutoEqBand("PK", 1250f, -1.5f, 2.0f),
            AutoEqBand("PK", 3150f, 2.8f, 1.8f),
            AutoEqBand("HSC", 10000f, -1.0f, 0.71f)
        )
        return HeadphoneSpecs(
            modelName = item.modelName,
            source = item.source,
            targetCurve = item.targetCurve,
            formFactor = item.formFactor,
            preampDb = -4.5f,
            bands = bands,
            rawProfileText = "Preamp: -4.5 dB\nFilter 1: ON LSC Fc 105 Hz Gain 4.5 dB Q 0.71\nFilter 2: ON PK Fc 200 Hz Gain -1.2 dB Q 1.40\nFilter 3: ON PK Fc 1250 Hz Gain -1.5 dB Q 2.00\nFilter 4: ON PK Fc 3150 Hz Gain 2.8 dB Q 1.80\nFilter 5: ON HSC Fc 10000 Hz Gain -1.0 dB Q 0.71"
        )
    }

    // JSON DTO matching autoeq_parametric_presets.json
    private data class ParametricPresetJson(
        @SerializedName("model") val model: String,
        @SerializedName("source") val source: String,
        @SerializedName("preamp") val preamp: Double,
        @SerializedName("filters") val filters: List<FilterJson>
    )
    // Add these DTOs at the bottom of AutoEqRepositoryImpl alongside ParametricPresetJson:
    private data class GraphicPresetJson(
        @SerializedName("model") val model: String,
        @SerializedName("source") val source: String,
        @SerializedName("preamp") val preamp: Double,
        @SerializedName("points") val points: List<GraphicPointJson>
    )

    private data class GraphicPointJson(
        @SerializedName("f") val f: Int,
        @SerializedName("g") val g: Double
    )

    private data class FilterJson(
        @SerializedName("type") val type: String,
        @SerializedName("fc") val fc: Int,
        @SerializedName("gain") val gain: Double,
        @SerializedName("q") val q: Double
    )

    companion object {
        private const val TAG = "AutoEqRepository"
        private const val ASSET_PARAMETRIC_JSON = "autoeq/autoeq_parametric_presets.json"
        private const val ASSET_GRAPHIC_JSON = "autoeq/autoeq_graphic_presets.json"
    }
}

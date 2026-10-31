package com.tigerplayer.data.model

import com.google.gson.annotations.SerializedName

/**
 * Model item from the AutoEq database index representing an indexed headphone measurement.
 */
data class HeadphoneSearchResult(
    @SerializedName("id") val id: String,
    @SerializedName("name") val modelName: String,
    @SerializedName("source") val source: String,
    @SerializedName("form") val formFactor: String,
    @SerializedName("target") val targetCurve: String,
    @SerializedName("path") val path: String
)

/**
 * Single parametric equalizer filter band from an AutoEq profile.
 */
data class AutoEqBand(
    @SerializedName("filterType") val filterType: String,
    @SerializedName("frequencyHz") val frequencyHz: Float,
    @SerializedName("gainDb") val gainDb: Float,
    @SerializedName("q") val q: Float,
    @SerializedName("isEnabled") val isEnabled: Boolean = true
)

/**
 * Full acoustic specifications and parametric EQ curve for a specific headphone measurement.
 */
data class HeadphoneSpecs(
    val modelName: String,
    val source: String,
    val targetCurve: String,
    val formFactor: String,
    val preampDb: Float,
    val bands: List<AutoEqBand>,
    val rawProfileText: String? = null
) {
    fun toDevicePreset(
        deviceId: String? = null,
        presetName: String? = null,
        gson: com.google.gson.Gson = com.google.gson.Gson()
    ): com.tigerplayer.data.local.entity.DevicePresetEntity {
        val bandsJson = gson.toJson(bands)
        val name = presetName ?: "$modelName ($source)"
        return com.tigerplayer.data.local.entity.DevicePresetEntity(
            presetId = java.util.UUID.randomUUID().toString(),
            name = name,
            deviceId = deviceId,
            preampDb = preampDb,
            bandsJson = bandsJson,
            isAutoEq = true,
            autoEqModel = modelName,
            autoEqSource = source,
            targetCurve = targetCurve,
            formFactor = formFactor,
            createdAtEpochMs = System.currentTimeMillis(),
            updatedAtEpochMs = System.currentTimeMillis()
        )
    }
}

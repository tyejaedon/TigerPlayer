package com.tigerplayer.data.repository

import com.tigerplayer.data.model.HeadphoneSearchResult
import com.tigerplayer.data.model.HeadphoneSpecs
import kotlinx.coroutines.flow.Flow

interface AutoEqRepository {
    fun getAvailableHeadphonesFlow(): Flow<List<HeadphoneSearchResult>>
    suspend fun searchHeadphones(query: String): List<HeadphoneSearchResult>
    suspend fun findBestMatch(deviceName: String): HeadphoneSearchResult?
    suspend fun fetchHeadphoneSpecs(item: HeadphoneSearchResult): Result<HeadphoneSpecs>

    suspend fun fetchGraphicEqGains(
        item: HeadphoneSearchResult,
        targetFrequenciesHz: List<Float>
    ): Result<Pair<Float, List<Float>>>

    fun parseParametricEqText(
        rawText: String,
        modelName: String,
        source: String,
        target: String,
        form: String
    ): HeadphoneSpecs
}

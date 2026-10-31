package com.tigerplayer.data.repository

import android.content.Context
import android.content.res.AssetManager
import com.google.gson.Gson
import com.tigerplayer.data.model.HeadphoneSearchResult
import com.tigerplayer.data.remote.api.AutoEqApi
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Response
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

@RunWith(RobolectricTestRunner::class)
class AutoEqRepositoryTest {

    private val context = mockk<Context>()
    private val assetManager = mockk<AssetManager>()
    private val autoEqApi = mockk<AutoEqApi>(relaxed = true)
    private val gson = Gson()

    private lateinit var repository: AutoEqRepositoryImpl

    private val sampleModels = listOf(
        HeadphoneSearchResult(
            id = "sony_wh_1000xm4_oratory",
            modelName = "Sony WH-1000XM4",
            source = "oratory1990",
            formFactor = "Over-Ear",
            targetCurve = "Harman Over-Ear 2018",
            path = "oratory1990/harman_over-ear_2018/Sony WH-1000XM4"
        ),
        HeadphoneSearchResult(
            id = "apple_airpods_pro_2_crinacle",
            modelName = "Apple AirPods Pro 2",
            source = "crinacle",
            formFactor = "In-Ear",
            targetCurve = "Harman In-Ear 2019v2",
            path = "crinacle/harman_in-ear_2019v2/Apple AirPods Pro 2"
        ),
        HeadphoneSearchResult(
            id = "sennheiser_hd_600_oratory",
            modelName = "Sennheiser HD 600",
            source = "oratory1990",
            formFactor = "Over-Ear",
            targetCurve = "Harman Over-Ear 2018",
            path = "oratory1990/harman_over-ear_2018/Sennheiser HD 600"
        ),
        HeadphoneSearchResult(
            id = "moondrop_blessing_3_crinacle",
            modelName = "Moondrop Blessing 3",
            source = "crinacle",
            formFactor = "In-Ear",
            targetCurve = "Harman In-Ear 2019v2",
            path = "crinacle/harman_in-ear_2019v2/Moondrop Blessing 3"
        )
    )

    @Before
    fun setUp() {
        every { context.assets } returns assetManager
        val json = gson.toJson(sampleModels)
        val byteOut = ByteArrayOutputStream()
        GZIPOutputStream(byteOut).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        every { assetManager.open("autoeq/models_index.json.gz") } answers {
            ByteArrayInputStream(byteOut.toByteArray())
        }

        repository = AutoEqRepositoryImpl(context, autoEqApi, gson)
    }

    @Test
    fun `searchHeadphones returns all models when query is blank`() = runTest {
        val results = repository.searchHeadphones("")
        assertEquals(4, results.size)
    }

    @Test
    fun `searchHeadphones filters by model name and brand`() = runTest {
        val sonyResults = repository.searchHeadphones("sony")
        assertEquals(1, sonyResults.size)
        assertEquals("Sony WH-1000XM4", sonyResults.first().modelName)

        val sennResults = repository.searchHeadphones("HD 600")
        assertEquals(1, sennResults.size)
        assertEquals("Sennheiser HD 600", sennResults.first().modelName)
    }

    @Test
    fun `searchHeadphones filters by form factor and source`() = runTest {
        val inEarResults = repository.searchHeadphones("in-ear")
        assertEquals(2, inEarResults.size)
        assertTrue(inEarResults.any { it.modelName == "Apple AirPods Pro 2" })
        assertTrue(inEarResults.any { it.modelName == "Moondrop Blessing 3" })

        val crinacleResults = repository.searchHeadphones("crinacle")
        assertEquals(2, crinacleResults.size)
    }

    @Test
    fun `findBestMatch cleans noise tokens and matches candidate`() = runTest {
        val match1 = repository.findBestMatch("LE-WH-1000XM4")
        assertNotNull(match1)
        assertEquals("Sony WH-1000XM4", match1?.modelName)

        val match2 = repository.findBestMatch("AirPods Pro 2 Wireless Headset")
        assertNotNull(match2)
        assertEquals("Apple AirPods Pro 2", match2?.modelName)

        val match3 = repository.findBestMatch("HD 600")
        assertNotNull(match3)
        assertEquals("Sennheiser HD 600", match3?.modelName)
    }

    @Test
    fun `parseParametricEqText correctly parses preamp and PEQ filters`() {
        val rawText = """
            Preamp: -4.8 dB
            Filter 1: ON PK Fc 31 Hz Gain 5.2 dB Q 1.41
            Filter 2: ON LSC Fc 105 Hz Gain 4.5 dB Q 0.71
            Filter 3: ON PK Fc 3200 Hz Gain 3.0 dB Q 2.00
            Filter 4: ON HSC Fc 10000 Hz Gain -2.0 dB Q 0.71
        """.trimIndent()

        val specs = repository.parseParametricEqText(
            rawText = rawText,
            modelName = "Sony WH-1000XM4",
            source = "oratory1990",
            target = "Harman Over-Ear 2018",
            form = "Over-Ear"
        )

        assertEquals("Sony WH-1000XM4", specs.modelName)
        assertEquals("Harman Over-Ear 2018", specs.targetCurve)
        assertEquals("Over-Ear", specs.formFactor)
        assertEquals(-4.8f, specs.preampDb, 0.001f)
        assertEquals(4, specs.bands.size)

        val band1 = specs.bands[0]
        assertEquals("PK", band1.filterType)
        assertEquals(31f, band1.frequencyHz, 0.01f)
        assertEquals(5.2f, band1.gainDb, 0.01f)
        assertEquals(1.41f, band1.q, 0.01f)

        val band2 = specs.bands[1]
        assertEquals("LSC", band2.filterType)
        assertEquals(105f, band2.frequencyHz, 0.01f)
        assertEquals(4.5f, band2.gainDb, 0.01f)
    }

    @Test
    fun `fetchHeadphoneSpecs parses live response from AutoEqApi`() = runTest {
        val rawParametric = """
            Preamp: -3.5 dB
            Filter 1: ON PK Fc 60 Hz Gain 2.5 dB Q 1.20
            Filter 2: ON HSC Fc 8000 Hz Gain -1.8 dB Q 0.71
        """.trimIndent()

        val responseBody = rawParametric.toResponseBody("text/plain".toMediaTypeOrNull())
        coEvery { autoEqApi.getParametricEqProfile(any()) } returns Response.success(responseBody)

        val item = sampleModels.first()
        val result = repository.fetchHeadphoneSpecs(item)
        assertTrue(result.isSuccess)

        val specs = result.getOrThrow()
        assertEquals(item.modelName, specs.modelName)
        assertEquals(-3.5f, specs.preampDb, 0.001f)
        assertEquals(2, specs.bands.size)
        assertEquals("PK", specs.bands[0].filterType)
        assertEquals(60f, specs.bands[0].frequencyHz, 0.01f)
    }

    @Test
    fun `toDevicePreset converts HeadphoneSpecs into Room entity`() {
        val rawText = "Preamp: -4.0 dB\nFilter 1: ON PK Fc 100 Hz Gain 3.0 dB Q 1.0"
        val specs = repository.parseParametricEqText(
            rawText = rawText,
            modelName = "Sony WH-1000XM4",
            source = "oratory1990",
            target = "Harman Over-Ear 2018",
            form = "Over-Ear"
        )

        val preset = specs.toDevicePreset(deviceId = "AA:BB:CC:DD:EE:FF")
        assertEquals("Sony WH-1000XM4 (oratory1990)", preset.name)
        assertEquals("AA:BB:CC:DD:EE:FF", preset.deviceId)
        assertEquals(-4.0f, preset.preampDb, 0.001f)
        assertEquals(true, preset.isAutoEq)
        assertEquals("Sony WH-1000XM4", preset.autoEqModel)
        assertEquals("oratory1990", preset.autoEqSource)
        assertEquals("Harman Over-Ear 2018", preset.targetCurve)
        assertEquals("Over-Ear", preset.formFactor)
    }

    @Test
    fun `fetchHeadphoneSpecs returns valid curve even when network fails`() = runTest {
        coEvery { autoEqApi.getParametricEqProfile(any()) } throws RuntimeException("Network down")
        val item = sampleModels[1]
        val result = repository.fetchHeadphoneSpecs(item)
        assertTrue(result.isSuccess)

        val specs = result.getOrThrow()
        assertEquals(item.modelName, specs.modelName)
        assertEquals(item.targetCurve, specs.targetCurve)
        assertEquals(item.formFactor, specs.formFactor)
        assertTrue(specs.bands.isNotEmpty())
    }
}

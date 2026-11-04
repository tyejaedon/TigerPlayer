package com.tigerplayer.data.repository

import android.content.Context
import android.content.res.AssetManager
import com.google.gson.Gson
import com.tigerplayer.data.model.HeadphoneSearchResult
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream

@RunWith(RobolectricTestRunner::class)
class AutoEqRepositoryTest {

    private val context = mockk<Context>()
    private val assetManager = mockk<AssetManager>()
    private val gson = Gson()

    private lateinit var repository: AutoEqRepositoryImpl

    private val samplePresets = listOf(
        mapOf(
            "model" to "Sony WH-1000XM4",
            "source" to "oratory1990",
            "preamp" to -3.5,
            "filters" to listOf(
                mapOf("type" to "PK", "fc" to 60, "gain" to 2.5, "q" to 1.20),
                mapOf("type" to "HSC", "fc" to 8000, "gain" to -1.8, "q" to 0.71)
            )
        ),
        mapOf(
            "model" to "Apple AirPods Pro 2",
            "source" to "crinacle",
            "preamp" to -2.0,
            "filters" to listOf(
                mapOf("type" to "PK", "fc" to 100, "gain" to 1.5, "q" to 1.41)
            )
        ),
        mapOf(
            "model" to "Sennheiser HD 600",
            "source" to "oratory1990",
            "preamp" to -4.0,
            "filters" to listOf(
                mapOf("type" to "PK", "fc" to 31, "gain" to 3.0, "q" to 1.41)
            )
        ),
        mapOf(
            "model" to "Moondrop Blessing 3",
            "source" to "crinacle",
            "preamp" to -1.5,
            "filters" to listOf(
                mapOf("type" to "PK", "fc" to 500, "gain" to -2.0, "q" to 1.0)
            )
        )
    )

    private val sampleGraphicPresets = listOf(
        mapOf(
            "model" to "Sony WH-1000XM4",
            "source" to "oratory1990",
            "preamp" to -3.5,
            "points" to listOf(
                mapOf("f" to 31, "g" to 5.0),
                mapOf("f" to 63, "g" to 4.0),
                mapOf("f" to 125, "g" to 2.0),
                mapOf("f" to 250, "g" to 0.0),
                mapOf("f" to 500, "g" to -1.0),
                mapOf("f" to 1000, "g" to 0.0),
                mapOf("f" to 2000, "g" to 1.5),
                mapOf("f" to 4000, "g" to 3.0),
                mapOf("f" to 8000, "g" to 4.0),
                mapOf("f" to 16000, "g" to 2.0)
            )
        )
    )

    @Before
    fun setUp() {
        every { context.assets } returns assetManager
        val parametricJson = gson.toJson(samplePresets)
        every { assetManager.open("autoeq/autoeq_parametric_presets.json") } answers {
            ByteArrayInputStream(parametricJson.toByteArray(Charsets.UTF_8))
        }

        val graphicJson = gson.toJson(sampleGraphicPresets)
        every { assetManager.open("autoeq/autoeq_graphic_presets.json") } answers {
            ByteArrayInputStream(graphicJson.toByteArray(Charsets.UTF_8))
        }

        repository = AutoEqRepositoryImpl(context, gson)
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
        val crinacleResults = repository.searchHeadphones("crinacle")
        assertEquals(2, crinacleResults.size)
        assertTrue(crinacleResults.any { it.modelName == "Apple AirPods Pro 2" })
        assertTrue(crinacleResults.any { it.modelName == "Moondrop Blessing 3" })
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
    fun `fetchHeadphoneSpecs retrieves cached profile from bundled assets`() = runTest {
        val item = repository.searchHeadphones("Sony").first()
        val result = repository.fetchHeadphoneSpecs(item)
        assertTrue(result.isSuccess)

        val specs = result.getOrThrow()
        assertEquals("Sony WH-1000XM4", specs.modelName)
        assertEquals(-3.5f, specs.preampDb, 0.001f)
        assertEquals(2, specs.bands.size)
        assertEquals("PK", specs.bands[0].filterType)
        assertEquals(60f, specs.bands[0].frequencyHz, 0.01f)
    }

    @Test
    fun `fetchGraphicEqGains interpolates gains from graphic presets`() = runTest {
        val item = repository.searchHeadphones("Sony").first()
        val result = repository.fetchGraphicEqGains(item, listOf(31f, 63f, 125f))
        assertTrue(result.isSuccess)

        val (preamp, gains) = result.getOrThrow()
        assertEquals(-5.0f, preamp, 0.01f)
        assertEquals(3, gains.size)
        assertEquals(5.0f, gains[0], 0.1f)
        assertEquals(4.0f, gains[1], 0.1f)
        assertEquals(2.0f, gains[2], 0.1f)
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
    fun `fetchHeadphoneSpecs returns fallback curve when model not in presets`() = runTest {
        val unknownItem = HeadphoneSearchResult(
            id = "unknown_model",
            modelName = "Unknown Headphones",
            source = "community",
            formFactor = "Over-Ear",
            targetCurve = "Harman",
            path = ""
        )
        val result = repository.fetchHeadphoneSpecs(unknownItem)
        assertTrue(result.isSuccess)

        val specs = result.getOrThrow()
        assertEquals(unknownItem.modelName, specs.modelName)
        assertEquals(unknownItem.targetCurve, specs.targetCurve)
        assertEquals(unknownItem.formFactor, specs.formFactor)
        assertTrue(specs.bands.isNotEmpty())
    }
}

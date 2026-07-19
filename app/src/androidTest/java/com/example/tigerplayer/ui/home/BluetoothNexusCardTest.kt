package com.example.tigerplayer.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.tigerplayer.ui.theme.TigerPlayerTheme
import com.example.tigerplayer.utils.BluetoothDeviceInfo
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BluetoothNexusCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun connectedDevice_renders_rich_metadata() {
        val deviceInfo = BluetoothDeviceInfo(
            name = "Aero Buds",
            address = "AA:BB:11:22:CC:DD",
            batteryLevel = 84,
            isConnected = true,
            listeningTimeMs = 3_726_000L,
            type = "Bluetooth",
            codec = "AAC",
            profile = "A2DP",
            transport = "Dual",
            deviceClass = "Audio/Video",
            maskedAddress = "AA:BB:**:**:CC:DD"
        )

        composeRule.setContent {
            TigerPlayerTheme {
                BluetoothNexusCard(deviceInfo = deviceInfo)
            }
        }

        composeRule.onNodeWithText("AERO BUDS").assertIsDisplayed()
        composeRule.onNodeWithText("AAC • A2DP • Dual").assertIsDisplayed()
        composeRule.onNodeWithText("Audio/Video").assertIsDisplayed()
        composeRule.onNodeWithText("84%").assertIsDisplayed()
        composeRule.onNodeWithText("BATTERY").assertIsDisplayed()
        composeRule.onNodeWithText("1H 2M").assertIsDisplayed()
        composeRule.onNodeWithText("AA:BB:**:**:CC:DD").assertIsDisplayed()
        composeRule.onNodeWithText("NEXUS CONNECTED").assertIsDisplayed()
    }

    @Test
    fun unknown_metadata_uses_fallback_labels() {
        val deviceInfo = BluetoothDeviceInfo(
            name = "Mystery Bud",
            isConnected = true,
            batteryLevel = -1,
            codec = "",
            profile = "",
            transport = "",
            deviceClass = "",
            maskedAddress = ""
        )

        composeRule.setContent {
            TigerPlayerTheme {
                BluetoothNexusCard(deviceInfo = deviceInfo)
            }
        }

        composeRule.onNodeWithText("Unknown").assertIsDisplayed()
        composeRule.onNodeWithText("N/A • A2DP • Unknown").assertIsDisplayed()
        composeRule.onNodeWithText("Audio Device").assertIsDisplayed()
        composeRule.onNodeWithText("Hidden").assertIsDisplayed()
    }

    @Test
    fun disconnectedDevice_cardNotRendered() {
        composeRule.setContent {
            TigerPlayerTheme {
                BluetoothNexusCard(deviceInfo = BluetoothDeviceInfo(isConnected = false))
            }
        }

        val cardNodeMissing = runCatching {
            composeRule.onNodeWithText("NEXUS CONNECTED").fetchSemanticsNode()
        }.isFailure

        assertTrue(cardNodeMissing)
    }
}


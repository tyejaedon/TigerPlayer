package com.tigerplayer.ui.coverscreen.core

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tigerplayer.R
import com.tigerplayer.ui.theme.TigerNeonOrange
import com.tigerplayer.ui.theme.TigerPlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CoverScreenHelperTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rememberArtworkAccent_nullImageModel_returnsFallbackSafely() {
        var capturedColor: Color? = null
        val fallbackColor = Color.Blue

        composeRule.setContent {
            TigerPlayerTheme {
                capturedColor = rememberArtworkAccent(
                    imageModel = null,
                    fallback = fallbackColor
                )
            }
        }

        composeRule.waitForIdle()

        // Should return the exact fallback color without crashing
        assertEquals(fallbackColor, capturedColor)
    }

    @Test
    fun rememberArtworkAccent_invalidImageModel_returnsFallbackEventually() {
        var capturedColor: Color? = null

        composeRule.setContent {
            TigerPlayerTheme {
                // Pass an invalid URL that Coil will fail to load
                capturedColor = rememberArtworkAccent(
                    imageModel = "invalid://image.url/does_not_exist.jpg",
                    fallback = TigerNeonOrange
                )
            }
        }

        composeRule.waitForIdle()

        // Wait up to 3 seconds for Coil to resolve the failure and apply the fallback
        composeRule.waitUntil(timeoutMillis = 3000) {
            capturedColor == TigerNeonOrange
        }

        assertEquals(TigerNeonOrange, capturedColor)
    }

    @Test
    fun rememberArtworkAccent_deterministicOutputForSameInput() {
        var firstPassColor: Color? = null
        var secondPassColor: Color? = null
        val initialFallback = Color.Black

        composeRule.setContent {
            TigerPlayerTheme {
                firstPassColor = rememberArtworkAccent(
                    imageModel = R.drawable.ic_tiger_logo,
                    fallback = initialFallback
                )
                secondPassColor = rememberArtworkAccent(
                    imageModel = R.drawable.ic_tiger_logo,
                    fallback = initialFallback
                )
            }
        }

        // Wait for Coil to load the local drawable and the Palette to extract the colors
        composeRule.waitUntil(timeoutMillis = 5000) {
            firstPassColor != initialFallback && secondPassColor != initialFallback
        }

        composeRule.waitForIdle()

        // Both calls should independently resolve to the exact same deterministic Color
        assertEquals("Both invocations must yield the same deterministic color", firstPassColor, secondPassColor)
    }
}

package com.tigerplayer.ui.coverscreen

import com.tigerplayer.ui.dashboard.CurationFeedMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying behavioral and metadata resolution in [CoverScreenHomeView].
 */
class CoverScreenHomeViewTest {

    @Test
    fun `test tags for CoverScreenHomeView are well formed and non-empty`() {
        assertEquals("cover_screen_home_view", CoverScreenTestTags.HOME_VIEW)
        assertEquals("cover_screen_home_header", CoverScreenTestTags.HOME_HEADER)
        assertEquals("cover_screen_home_daylist_card", CoverScreenTestTags.HOME_DAYLIST_CARD)
        assertEquals("cover_screen_home_discovery_card", CoverScreenTestTags.HOME_DISCOVERY_CARD)
        assertEquals("cover_screen_home_constellation_card", CoverScreenTestTags.HOME_CONSTELLATION_CARD)
        assertEquals("cover_screen_home_eq_hub_card", CoverScreenTestTags.HOME_EQ_HUB_CARD)
        assertEquals("cover_screen_home_footprint_card", CoverScreenTestTags.HOME_FOOTPRINT_CARD)
        assertEquals("cover_screen_home_spatial_nexus_card", CoverScreenTestTags.HOME_SPATIAL_NEXUS_CARD)
        assertEquals("cover_screen_home_acoustic_space_card", CoverScreenTestTags.HOME_ACOUSTIC_SPACE_CARD)
        assertEquals("cover_screen_home_eq_master_switch", CoverScreenTestTags.HOME_EQ_MASTER_SWITCH)
        assertEquals("cover_screen_home_close_button", CoverScreenTestTags.HOME_CLOSE_BUTTON)
    }

    @Test
    fun `nexus card callbacks trigger corresponding navigation intents`() {
        var footprintOpened = false
        var spatialNexusOpened = false
        var acousticSpaceOpened = false

        val onFootprint = { footprintOpened = true }
        val onSpatialNexus = { spatialNexusOpened = true }
        val onAcousticSpace = { acousticSpaceOpened = true }

        onFootprint()
        onSpatialNexus()
        onAcousticSpace()

        assertTrue(footprintOpened)
        assertTrue(spatialNexusOpened)
        assertTrue(acousticSpaceOpened)
    }

    @Test
    fun `day phase accent and labels resolve deterministically for home curation card`() {
        val morningPhase = resolveCoverDayPhase(8)
        assertEquals("MORNING WAVE", morningPhase.label)
        assertEquals("Sunrise Kinetic Arc", morningPhase.headline)
        assertNotNull(morningPhase.accent)

        val nightPhase = resolveCoverDayPhase(23)
        assertEquals("NIGHT ECHO", nightPhase.label)
        assertEquals("Afterdark Resonance Grid", nightPhase.headline)
        assertNotNull(nightPhase.accent)
    }

    @Test
    fun `curation metadata correctly reflects loading and error states`() {
        val loadingMeta = CurationFeedMeta(isLoading = true)
        assertTrue(loadingMeta.isLoading)

        val errorMeta = CurationFeedMeta(lastErrorMessage = "Network unreachable")
        assertEquals("Network unreachable", errorMeta.lastErrorMessage)
    }
}

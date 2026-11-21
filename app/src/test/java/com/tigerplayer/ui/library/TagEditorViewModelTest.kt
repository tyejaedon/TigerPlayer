package com.tigerplayer.ui.library

import android.content.IntentSender
import android.net.Uri
import com.tigerplayer.data.model.AudioTagData
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.model.BatchTagEditRequest
import com.tigerplayer.data.model.TagWriteResult
import com.tigerplayer.data.model.TrackTagError
import com.tigerplayer.engine.MetadataEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TagEditorViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var metadataEngine: MetadataEngine
    private lateinit var viewModel: TagEditorViewModel

    private val localTrack1 = AudioTrack(
        id = "1",
        title = "Old Title",
        artist = "Old Artist",
        album = "Old Album",
        uri = mockk<Uri>(relaxed = true),
        artworkUri = mockk<Uri>(relaxed = true),
        durationMs = 180_000L,
        mimeType = "audio/mpeg",
        isLocal = true,
        isRemote = false,
        trackNumber = 1,
        year = "2020"
    )

    private val localTrack2 = localTrack1.copy(id = "2", title = "Track Two", trackNumber = 2)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        metadataEngine = mockk(relaxed = true)
        viewModel = TagEditorViewModel(metadataEngine)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `openForTracks single track loads all eight tag fields`() = runTest {
        coEvery { metadataEngine.readLocalTrackTags(localTrack1) } returns Result.success(
            AudioTagData(
                title = "Silver for Monsters",
                artist = "Marcin",
                album = "Wild Hunt",
                albumArtist = "CDPR",
                trackNumber = "3",
                discNumber = "1",
                year = "2015",
                genre = "Soundtrack"
            )
        )

        viewModel.openForTracks(listOf(localTrack1))

        val state = viewModel.uiState.value
        assertFalse(state.isBatchMode)
        assertFalse(state.isLoading)
        assertEquals("Silver for Monsters", state.title)
        assertEquals("Marcin", state.artist)
        assertEquals("Wild Hunt", state.album)
        assertEquals("CDPR", state.albumArtist)
        assertEquals("3", state.trackNumber)
        assertEquals("1", state.discNumber)
        assertEquals("2015", state.year)
        assertEquals("Soundtrack", state.genre)
    }

    @Test
    fun `batch mode only writes explicitly enabled fields`() = runTest {
        val requestSlot = slot<BatchTagEditRequest>()
        coEvery {
            metadataEngine.writeLocalTrackTags(
                tracks = any(),
                edit = capture(requestSlot),
                createBackup = any()
            )
        } returns TagWriteResult.Success(emptyMap())

        viewModel.openForTracks(listOf(localTrack1, localTrack2))
        assertTrue(viewModel.uiState.value.isBatchMode)
        assertTrue(viewModel.uiState.value.enabledFields.isEmpty())

        viewModel.updateField(TagField.GENRE, "Synthwave")
        viewModel.requestSave()
        assertTrue(viewModel.uiState.value.showConfirmDialog)

        viewModel.confirmAndSave()

        assertEquals("Synthwave", requestSlot.captured.genre)
        assertNull(requestSlot.captured.title)
        assertNull(requestSlot.captured.artist)
        assertTrue(viewModel.uiState.value.saveSuccess)
    }

    @Test
    fun `permission required sets pendingIntentSender and retries when granted`() = runTest {
        val sender = mockk<IntentSender>(relaxed = true)
        coEvery {
            metadataEngine.readLocalTrackTags(localTrack1)
        } returns Result.success(AudioTagData(title = "T", artist = "A", album = "Al"))
        coEvery {
            metadataEngine.writeLocalTrackTags(any(), any(), any())
        } returnsMany listOf(
            TagWriteResult.PermissionRequired(sender),
            TagWriteResult.Success(emptyMap())
        )

        viewModel.openForTracks(listOf(localTrack1))
        viewModel.confirmAndSave()

        assertEquals(sender, viewModel.uiState.value.pendingIntentSender)
        assertFalse(viewModel.uiState.value.saveSuccess)

        viewModel.onWritePermissionResult(granted = true)

        assertNull(viewModel.uiState.value.pendingIntentSender)
        assertTrue(viewModel.uiState.value.saveSuccess)
        coVerify(exactly = 2) { metadataEngine.writeLocalTrackTags(any(), any(), any()) }
    }

    @Test
    fun `write failure and denied permission surface clear error messages`() = runTest {
        coEvery {
            metadataEngine.readLocalTrackTags(localTrack1)
        } returns Result.success(AudioTagData(title = "T"))
        coEvery {
            metadataEngine.writeLocalTrackTags(any(), any(), any())
        } returns TagWriteResult.Failure(
            listOf(TrackTagError("1", "Old Title", "Disk full"))
        )

        viewModel.openForTracks(listOf(localTrack1))
        viewModel.confirmAndSave()

        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.errorMessage!!.contains("Disk full"))

        viewModel.onWritePermissionResult(granted = false)
        assertTrue(viewModel.uiState.value.errorMessage!!.contains("denied", ignoreCase = true))
    }
}

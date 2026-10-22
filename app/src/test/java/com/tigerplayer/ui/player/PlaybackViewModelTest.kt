package com.tigerplayer.ui.player

import com.tigerplayer.data.local.CoverScreenEqPreset
import com.tigerplayer.data.model.AudioTrack
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class PlaybackViewModelTest {

    private val playbackStateManager = mockk<PlaybackStateManager>(relaxed = true)
    private val viewModel = PlaybackViewModel(playbackStateManager)

    @Test
    fun `playback controls delegate to PlaybackStateManager`() {
        val track = mockk<AudioTrack>(relaxed = true)
        val tracks = listOf(track)

        viewModel.togglePlayPause()
        verify(exactly = 1) { playbackStateManager.togglePlayPause() }

        viewModel.playTrack(track)
        verify(exactly = 1) { playbackStateManager.playTrack(track, emptyList()) }

        viewModel.setPlaylistAndPlay(tracks, 2)
        verify(exactly = 1) { playbackStateManager.setPlaylistAndPlay(tracks, 2) }

        viewModel.playAll(tracks)
        verify(exactly = 1) { playbackStateManager.playAll(tracks) }

        viewModel.skipToNext()
        verify(exactly = 1) { playbackStateManager.skipToNext() }

        viewModel.skipToPrevious()
        verify(exactly = 1) { playbackStateManager.skipToPrevious() }

        viewModel.seekTo(15_000L)
        verify(exactly = 1) { playbackStateManager.seekTo(15_000L) }

        viewModel.toggleShuffle()
        verify(exactly = 1) { playbackStateManager.toggleShuffle() }

        viewModel.toggleRepeat()
        verify(exactly = 1) { playbackStateManager.toggleRepeat() }
    }

    @Test
    fun `volume and eq controls delegate to PlaybackStateManager`() {
        viewModel.setCoverScreenVolume(0.8f)
        verify(exactly = 1) { playbackStateManager.setCoverScreenVolume(0.8f) }

        viewModel.adjustCoverScreenVolume(0.1f)
        verify(exactly = 1) { playbackStateManager.adjustCoverScreenVolume(0.1f) }

        viewModel.setCoverScreenEqPreset(CoverScreenEqPreset.NIGHT_DRIVE)
        verify(exactly = 1) { playbackStateManager.setCoverScreenEqPreset(CoverScreenEqPreset.NIGHT_DRIVE) }
    }

    @Test
    fun `speed and pitch controls delegate to PlaybackStateManager`() {
        viewModel.setPlaybackSpeed(1.25f)
        verify(exactly = 1) { playbackStateManager.setPlaybackSpeed(1.25f) }

        viewModel.increasePlaybackSpeed()
        verify(exactly = 1) { playbackStateManager.increasePlaybackSpeed() }

        viewModel.decreasePlaybackSpeed()
        verify(exactly = 1) { playbackStateManager.decreasePlaybackSpeed() }

        viewModel.setPitch(0.9f)
        verify(exactly = 1) { playbackStateManager.setPitch(0.9f) }

        viewModel.increasePitch()
        verify(exactly = 1) { playbackStateManager.increasePitch() }

        viewModel.decreasePitch()
        verify(exactly = 1) { playbackStateManager.decreasePitch() }
    }

    @Test
    fun `queue and sleep timer controls delegate to PlaybackStateManager`() {
        val track = mockk<AudioTrack>(relaxed = true)

        viewModel.addToQueue(track)
        verify(exactly = 1) { playbackStateManager.addToQueue(track) }

        viewModel.addNextToQueue(track)
        verify(exactly = 1) { playbackStateManager.addNextToQueue(track) }

        viewModel.removeFromQueue(1)
        verify(exactly = 1) { playbackStateManager.removeFromQueue(1) }

        viewModel.clearQueue()
        verify(exactly = 1) { playbackStateManager.clearQueue() }

        viewModel.moveQueueItem(1, 2)
        verify(exactly = 1) { playbackStateManager.moveQueueItem(1, 2) }

        viewModel.playQueueItem(0)
        verify(exactly = 1) { playbackStateManager.playQueueItem(0) }

        viewModel.setSleepTimerDuration(45)
        verify(exactly = 1) { playbackStateManager.setSleepTimerDuration(45) }

        viewModel.setSleepTimerEndOfTrack()
        verify(exactly = 1) { playbackStateManager.setSleepTimerEndOfTrack() }

        viewModel.setSleepTimerEndOfQueue()
        verify(exactly = 1) { playbackStateManager.setSleepTimerEndOfQueue() }

        viewModel.cancelSleepTimer()
        verify(exactly = 1) { playbackStateManager.cancelSleepTimer() }
    }
}

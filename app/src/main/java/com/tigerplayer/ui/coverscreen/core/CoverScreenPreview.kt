package com.tigerplayer.ui.coverscreen.core

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tigerplayer.data.model.AudioTrack as Track
import com.tigerplayer.engine.AudioReactiveFrame
import com.tigerplayer.ui.coverscreen.CoverAccessibleControls
import com.tigerplayer.ui.coverscreen.CoverMicroWaveform
import com.tigerplayer.ui.coverscreen.CoverScreenWindowState
import com.tigerplayer.ui.coverscreen.TrackInfoText
import com.tigerplayer.ui.coverscreen.shouldShowCoverControls
import com.tigerplayer.ui.coverscreen.shouldShowCoverWaveform
import com.tigerplayer.ui.player.PlayerUiState
import com.tigerplayer.ui.theme.TigerPlayerTheme
import com.tigerplayer.ui.theme.TigerSurfaceCharcoal

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
@Preview(
    name = "Galaxy Z Flip 5/6 Cover Screen (720x748)",
    widthDp = 360,
    heightDp = 374,
    showBackground = true,
    backgroundColor = 0xFF121212
)
@Preview(
    name = "Motorola Razr+ Cover Screen (1056x1066)",
    widthDp = 412,
    heightDp = 416,
    showBackground = true,
    backgroundColor = 0xFF121212
)
@Preview(
    name = "Cover Screen Flex Mode / Dual",
    widthDp = 720,
    heightDp = 360,
    showBackground = true,
    backgroundColor = 0xFF121212
)
annotation class CoverScreenPreviews


@Composable
fun CoverScreenMiniHubContent(
    uiState: PlayerUiState,
    track: Track?,
    windowState: CoverScreenWindowState,
    modifier: Modifier = Modifier,
    isVortexImmersive: Boolean = false,
    onTogglePlayPause: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    onSkipPrevious: () -> Unit = {},
    onToggleShuffle: () -> Unit = {},
    onCycleRepeat: () -> Unit = {},
    onSeek: (Long) -> Unit = {}
) {
    val isTightScreen = windowState.heightDp in 1..360
    val verticalPadding = if (isTightScreen) 4.dp else 8.dp
    val showWaveform = shouldShowCoverWaveform(
        hasModalOverlay = false,
        fullBleedLyricsVisible = false,
        isVortexImmersive = isVortexImmersive
    )
    val waveformBottomClearance = if (showWaveform) 34.dp else 0.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TigerSurfaceCharcoal)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 12.dp,
                    end = 12.dp,
                    top = verticalPadding,
                    bottom = verticalPadding + waveformBottomClearance
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (isVortexImmersive) Arrangement.Top else Arrangement.Top
        ) {
            TrackInfoText(
                track = track,
                subtitle = if (uiState.isPlaying) "NOW PLAYING" else "PAUSED",
                lyrics = uiState.currentLyrics,
                currentPosition = uiState.currentPosition,
                isLarge = false,
                statsVisible = false,
                isVortexImmersive = isVortexImmersive,
                onToggleVortexImmersive = {},
                onDoubleTapAlbumArt = {},
                onOpenQueue = {},
                onOpenLibrary = {},
                onMediaCyclePositioned = {},
                onSeek = onSeek,
                screenHeightDp = windowState.heightDp.dp
            )

            if (shouldShowCoverControls(isVortexImmersive)) {
                Spacer(modifier = Modifier.height(if (isTightScreen) 39.dp else 53.dp))
                CoverAccessibleControls(
                    isPlaying = uiState.isPlaying,
                    isShuffleEnabled = uiState.isShuffleEnabled,
                    repeatMode = uiState.repeatMode,
                    onToggleShuffle = onToggleShuffle,
                    onCycleRepeat = onCycleRepeat,
                    onLongPressPlayPause = {},
                    onTogglePlayPause = onTogglePlayPause,
                    onPrevious = onSkipPrevious,
                    onNext = onSkipNext
                )
            }
        }

        if (showWaveform) {
            CoverMicroWaveform(
                amplitudes = uiState.currentWaveform,
                bass = uiState.audioReactiveFrame.bass,
                energy = uiState.audioReactiveFrame.energy,
                isPlaying = uiState.isPlaying,
                isRenderingActive = false,
                duration = track?.durationMs ?: 240_000L,
                currentPosition = uiState.currentPosition,
                onSeek = onSeek,
                artworkUri = track?.artworkUri,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(30.dp)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@CoverScreenPreviews
@Composable
fun CoverScreenPreview() {
    CoverScreenMiniHubPreview()
}

@CoverScreenPreviews
@Composable
private fun CoverScreenMiniHubPreview() {
    val sampleTrack = remember {
        Track(
            id = "preview_1",
            title = "Starboy",
            artist = "The Weeknd, Daft Punk",
            album = "Starboy",
            uri = Uri.EMPTY,
            artworkUri = Uri.EMPTY,
            durationMs = 230_000L,
            mimeType = "audio/mpeg"
        )
    }

    val sampleUiState = remember {
        PlayerUiState(
            currentTrack = sampleTrack,
            isPlaying = true,
            currentPosition = 65_000L,
            currentWaveform = listOf(0.2f, 0.5f, 0.8f, 0.6f, 0.9f, 0.4f, 0.7f, 0.3f),
            audioReactiveFrame = AudioReactiveFrame(
                bass = 0.75f,
                mid = 0.5f,
                treble = 0.35f,
                energy = 0.65f
            )
        )
    }

    TigerPlayerTheme(darkTheme = true) {
        CoverScreenMiniHubContent(
            uiState = sampleUiState,
            track = sampleTrack,
            windowState = CoverScreenWindowState(
                widthDp = 360,
                heightDp = 374,
                isCoverScreen = true,
                hasSeparatingHinge = false
            )
        )
    }
}

@Preview(name = "Cover Screen - Flex Mode (Dual Panel)", widthDp = 720, heightDp = 360)
@Composable
private fun CoverScreenMiniHubFlexPreview() {
    val sampleTrack = remember {
        Track(
            id = "preview_2",
            title = "DNA.",
            artist = "Kendrick Lamar",
            album = "DAMN.",
            uri = Uri.EMPTY,
            artworkUri = Uri.EMPTY,
            durationMs = 185_000L,
            mimeType = "audio/mpeg"
        )
    }

    TigerPlayerTheme(darkTheme = true) {
        CoverScreenMiniHubContent(
            uiState = PlayerUiState(isPlaying = true, currentTrack = sampleTrack),
            track = sampleTrack,
            windowState = CoverScreenWindowState(
                widthDp = 720,
                heightDp = 360,
                isCoverScreen = true,
                hasSeparatingHinge = true
            )
        )
    }
}

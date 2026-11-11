package com.tigerplayer.ui.player

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.ui.theme.TigerPlayerTheme
import java.util.concurrent.TimeUnit

/**
 * Harmonized high-performance player seeker and audio-reactive waveform scrubber.
 *
 * Delegates to [PlayerWaveform] to render the 3-tier harmonic wave hierarchy over the playback slider.
 */
@Composable
fun FieryWavySeeker(
    uiState: PlayerUiState,
    track: AudioTrack,
    onSeek: (Long) -> Unit,
    textColor: Color,
    modifier: Modifier = Modifier,
    artworkUri: Any? = track.artworkUri,
    energy: Float = uiState.audioReactiveFrame.energy,
    bass: Float = uiState.audioReactiveFrame.bass,
    flux: Float = uiState.audioReactiveFrame.flux,
    baseWaveAmplitude: Dp = (8 * uiState.waveformSensitivity).dp
) {
    PlayerWaveform(
        uiState = uiState,
        track = track,
        onSeek = onSeek,
        textColor = textColor,
        modifier = modifier,
        artworkUri = artworkUri,
        energy = energy,
        bass = bass,
        flux = flux,
        baseWaveAmplitude = baseWaveAmplitude
    )
}

/**
 * Formats duration in milliseconds into `mm:ss` display format.
 */
@SuppressLint("DefaultLocale")
fun formatDuration(ms: Long): String {
    val safeMs = ms.coerceAtLeast(0L)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(safeMs)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(safeMs) % 60
    return String.format("%02d:%02d", minutes, seconds)
}

@Preview(showBackground = true, backgroundColor = 0xFF0E0C0A)
@Composable
fun PlayerSeekerPreview() {
    TigerPlayerTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0E0C0A))
                .padding(vertical = 16.dp, horizontal = 12.dp)
        ) {
            PlayerWaveform(
                amplitudes = listOf(0.2f, 0.5f, 0.8f, 0.4f, 0.9f, 0.3f),
                bass = 0.5f,
                energy = 0.7f,
                isPlaying = true,
                duration = 180_000L,
                currentPosition = 45_000L,
                onSeek = {},
                textColor = Color.White
            )
        }
    }
}

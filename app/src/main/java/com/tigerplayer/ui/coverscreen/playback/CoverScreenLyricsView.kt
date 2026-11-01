package com.tigerplayer.ui.coverscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.tigerplayer.data.model.Lyrics
import com.tigerplayer.ui.player.LyricsDisplay
import com.tigerplayer.ui.theme.TigerNeonOrange

@Composable
fun CoverScreenLyricsView(
    lyrics: Lyrics,
    currentPosition: Long,
    onDoubleTap: () -> Unit,
    onSeek: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            // We rely on the FluidVortexView beneath us to shine through.
            // A semi-transparent black overlay ensures text contrast.
            .background(Color.Black.copy(alpha = 0.55f))
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { onDoubleTap() })
            }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        LyricsDisplay(
            lyrics = lyrics,
            currentPosition = currentPosition,
            textColor = Color.White,
            activeColor = TigerNeonOrange,
            onLineClick = onSeek
        )
    }
}

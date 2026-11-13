package com.tigerplayer.ui.player

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tigerplayer.data.model.LyricLine
import com.tigerplayer.data.model.LyricWord
import com.tigerplayer.data.model.Lyrics

@Preview(
    name = "Cover Screen Active Lyric Center",
    device = Devices.FOLDABLE,
    showBackground = true,
    backgroundColor = 0xFF121212
)
@Composable
fun CoverScreenLyricsPreview() {
    val dummyLines = listOf(
        LyricLine(
            timeMs = 1000L,
            durationMs = 2500L,
            text = "Is this the real life?",
            words = emptyList()
        ),
        LyricLine(
            timeMs = 4000L,
            durationMs = 3000L,
            text = "Is this just fantasy?",
            words = listOf(
                LyricWord(4000L, 4500L, "Is "),
                LyricWord(4500L, 5000L, "this "),
                LyricWord(5000L, 5500L, "just "),
                LyricWord(5500L, 7000L, "fantasy?")
            )
        ),
        LyricLine(
            timeMs = 8000L,
            durationMs = 3000L,
            text = "Caught in a landslide",
            words = emptyList()
        ),
        LyricLine(
            timeMs = 12000L,
            durationMs = 4000L,
            text = "No escape from reality",
            words = emptyList()
        )
    )

    MaterialTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(360.dp) // Approximate Flip cover screen dimensions
                    .height(340.dp)
                    .border(2.dp, Color.Green) // Visible bounding box constraint!
            ) {
                // Key forces full recomposition and state reset when we change the position
                key(8500L) {
                    LyricsDisplay(
                        lyrics = Lyrics.Synced(lines = dummyLines, hasWordLevelTiming = true),
                        currentPosition = 8500L, // Matches the middle of the "Caught in a landslide" line (8000L - 11000L)
                        textColor = Color.White
                    )
                }
            }
        }
    }
}

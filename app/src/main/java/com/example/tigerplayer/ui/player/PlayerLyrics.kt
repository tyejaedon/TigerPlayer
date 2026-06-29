package com.example.tigerplayer.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tigerplayer.ui.theme.igniRed

data class LyricLine(val timeMs: Long, val text: String)

private fun parseLrc(lrc: String?): List<LyricLine> {
    if (lrc.isNullOrBlank()) return emptyList()
    val regex = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?](.*)""")
    return lrc.lines().mapNotNull { line ->
        val match = regex.find(line)
        if (match != null) {
            val (min, sec, msStr, text) = match.destructured
            val ms = when (msStr.length) {
                0 -> 0L
                1 -> msStr.toLong() * 100
                2 -> msStr.toLong() * 10
                else -> msStr.toLong()
            }
            LyricLine(min.toLong() * 60000 + sec.toLong() * 1000 + ms, text.trim())
        } else null
    }
}

@Composable
fun LyricsDisplay(lyrics: String?, currentPosition: Long, textColor: Color) {
    if (lyrics.isNullOrBlank()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(
                "NO LYRICS FOUND",
                color = textColor.copy(0.4f),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
        }
    } else {
        val parsed = remember(lyrics) { parseLrc(lyrics) }
        val listState = rememberLazyListState()
        val activeIndex = remember(currentPosition, parsed) {
            parsed.indexOfLast { it.timeMs <= currentPosition }.coerceAtLeast(0)
        }

        LaunchedEffect(activeIndex) {
            if (parsed.isNotEmpty()) {
                listState.animateScrollToItem(activeIndex)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 200.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            itemsIndexed(
                items = parsed,
                key = { index, line -> "lyric_${line.timeMs}_$index" }
            ) { index, line ->
                val isActive = index == activeIndex
                val scale by animateFloatAsState(
                    targetValue = if (isActive) 1.1f else 1f,
                    label = "LyricScale"
                )

                Text(
                    text = line.text.ifBlank { "•••" },
                    color = if (isActive) MaterialTheme.igniRed else textColor.copy(0.4f),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = if (isActive) 24.sp else 20.sp
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(vertical = 12.dp, horizontal = 24.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                )
            }
        }
    }
}

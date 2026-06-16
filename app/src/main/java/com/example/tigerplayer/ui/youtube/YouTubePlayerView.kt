package com.example.tigerplayer.ui.youtube

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

@Composable
fun YouTubePlayerView(
    videoId: String,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            YouTubePlayerView(context).apply {
                addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
                    override fun onReady(youTubePlayer: YouTubePlayer) {
                        if (autoPlay) {
                            youTubePlayer.loadVideo(videoId, 0f)
                        } else {
                            youTubePlayer.cueVideo(videoId, 0f)
                        }
                    }
                })
            }
        },
        update = { view ->
            // If videoId changes, we might need to handle it. 
            // However, creating a new view or using the listener might be better.
        }
    )
}

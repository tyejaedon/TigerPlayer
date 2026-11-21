package com.tigerplayer.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import androidx.glance.appwidget.updateAll
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.tigerplayer.ui.theme.DominantColorExtractor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observes [Player] state inside [com.tigerplayer.service.AudioPlayerService] and keeps
 * [WidgetStateStore] and all installed [TigerPlayerWidget] instances synchronized,
 * including updates driven from the media notification or external controllers.
 */
@Singleton
class PlaybackWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val widgetStateStore: WidgetStateStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val imageLoader by lazy { ImageLoader(context) }

    private var attachedPlayer: Player? = null
    private var progressTickerJob: Job? = null
    private var syncJob: Job? = null
    private var lastArtworkKey: String? = null
    private var lastArtworkPath: String? = null
    private var lastPrimaryNeonArgb: Int = WidgetPlaybackState.DEFAULT_PRIMARY_NEON_ARGB
    private var lastSecondaryNeonArgb: Int = WidgetPlaybackState.DEFAULT_SECONDARY_NEON_ARGB

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            scheduleSync(forceArtworkRefresh = true)
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            scheduleSync(forceArtworkRefresh = true)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateProgressTicker(isPlaying)
            scheduleSync(forceArtworkRefresh = false)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            scheduleSync(forceArtworkRefresh = false)
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            scheduleSync(forceArtworkRefresh = false)
        }
    }

    fun attach(player: Player) {
        if (attachedPlayer === player) return
        detach()
        attachedPlayer = player
        player.addListener(playerListener)
        updateProgressTicker(player.isPlaying)
        scheduleSync(forceArtworkRefresh = true)
    }

    fun detach() {
        progressTickerJob?.cancel()
        progressTickerJob = null
        syncJob?.cancel()
        syncJob = null
        attachedPlayer?.removeListener(playerListener)
        attachedPlayer = null
    }

    private fun updateProgressTicker(isPlaying: Boolean) {
        progressTickerJob?.cancel()
        if (!isPlaying) return
        progressTickerJob = scope.launch {
            while (isActive) {
                delay(PROGRESS_TICK_MS)
                syncFromPlayer(forceArtworkRefresh = false)
            }
        }
    }

    private fun scheduleSync(forceArtworkRefresh: Boolean) {
        syncJob?.cancel()
        syncJob = scope.launch {
            syncFromPlayer(forceArtworkRefresh = forceArtworkRefresh)
        }
    }

    private suspend fun syncFromPlayer(forceArtworkRefresh: Boolean) {
        val player = attachedPlayer ?: return
        val mediaItem = player.currentMediaItem
        val metadata = player.mediaMetadata
        val trackId = mediaItem?.mediaId.orEmpty()
        val title = metadata.title?.toString().orEmpty()
        val artist = metadata.artist?.toString().orEmpty()
        val isPlaying = player.isPlaying
        val positionMs = player.currentPosition.coerceAtLeast(0L)
        val durationMs = player.duration.takeIf { it > 0L } ?: 0L
        val artworkUri = metadata.artworkUri
        val artworkData = metadata.artworkData
        val artworkKey = "${trackId}|${artworkUri?.toString().orEmpty()}|${artworkData?.size ?: 0}"

        try {
            if (forceArtworkRefresh || artworkKey != lastArtworkKey) {
                val bitmap = loadArtworkBitmap(artworkData, artworkUri)
                if (bitmap != null) {
                    val palette = DominantColorExtractor.extractCoverNeonPalette(bitmap)
                    lastPrimaryNeonArgb = palette.primary.toArgb()
                    lastSecondaryNeonArgb = palette.secondary.toArgb()
                    lastArtworkPath = persistWidgetArtwork(bitmap)
                } else {
                    lastPrimaryNeonArgb = WidgetPlaybackState.DEFAULT_PRIMARY_NEON_ARGB
                    lastSecondaryNeonArgb = WidgetPlaybackState.DEFAULT_SECONDARY_NEON_ARGB
                    lastArtworkPath = null
                }
                lastArtworkKey = artworkKey
            }

            widgetStateStore.write(
                WidgetPlaybackState(
                    trackId = trackId,
                    title = title,
                    artist = artist,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    artworkPath = lastArtworkPath,
                    primaryNeonArgb = lastPrimaryNeonArgb,
                    secondaryNeonArgb = lastSecondaryNeonArgb,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
            TigerPlayerWidget().updateAll(context)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update playback widget", e)
        }
    }

    private suspend fun loadArtworkBitmap(artworkData: ByteArray?, artworkUri: Uri?): Bitmap? {
        return withContext(Dispatchers.IO) {
            if (artworkData != null && artworkData.isNotEmpty()) {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(artworkData, 0, artworkData.size, options)
                val maxDim = maxOf(options.outWidth, options.outHeight)
                val sampleSize = if (maxDim > ARTWORK_SIZE_PX) maxDim / ARTWORK_SIZE_PX else 1
                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize.coerceAtLeast(1)
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                return@withContext BitmapFactory.decodeByteArray(
                    artworkData,
                    0,
                    artworkData.size,
                    decodeOptions
                )
            }

            if (artworkUri == null) return@withContext null
            val request = ImageRequest.Builder(context)
                .data(artworkUri)
                .size(ARTWORK_SIZE_PX)
                .allowHardware(false)
                .build()
            val result = imageLoader.execute(request) as? SuccessResult ?: return@withContext null
            val drawable = result.drawable
            val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: ARTWORK_SIZE_PX
            val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: ARTWORK_SIZE_PX
            val bitmap = Bitmap.createBitmap(
                width.coerceAtMost(ARTWORK_SIZE_PX),
                height.coerceAtMost(ARTWORK_SIZE_PX),
                Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            bitmap
        }
    }

    private suspend fun persistWidgetArtwork(bitmap: Bitmap): String? {
        return withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, ARTWORK_CACHE_DIR).apply { mkdirs() }
            val outFile = File(dir, ARTWORK_FILE_NAME)
            val tempFile = File(dir, "$ARTWORK_FILE_NAME.tmp")
            val scaled = if (bitmap.width > ARTWORK_SIZE_PX || bitmap.height > ARTWORK_SIZE_PX) {
                Bitmap.createScaledBitmap(bitmap, ARTWORK_SIZE_PX, ARTWORK_SIZE_PX, true)
            } else {
                bitmap
            }
            FileOutputStream(tempFile).use { stream ->
                scaled.compress(Bitmap.CompressFormat.PNG, 90, stream)
            }
            if (tempFile.renameTo(outFile)) outFile.absolutePath else tempFile.absolutePath
        }
    }

    companion object {
        private const val TAG = "PlaybackWidgetUpdater"
        private const val PROGRESS_TICK_MS = 5_000L
        private const val ARTWORK_SIZE_PX = 256
        private const val ARTWORK_CACHE_DIR = "widget_art"
        private const val ARTWORK_FILE_NAME = "current_artwork.png"
    }
}

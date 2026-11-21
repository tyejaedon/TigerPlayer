package com.tigerplayer.widget

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.tigerplayer.service.AudioPlayerService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "WidgetTransportActions"
private const val CONTROLLER_CONNECT_TIMEOUT_MS = 5_000L

/**
 * Toggles play/pause via [MediaController] connected to [AudioPlayerService],
 * ensuring transport controls work even when the app UI is backgrounded or killed.
 */
class TogglePlayPauseAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        executeMediaCommand(context) { controller ->
            if (controller.isPlaying) {
                controller.pause()
            } else {
                if (controller.mediaItemCount == 0) {
                    controller.prepare()
                }
                controller.play()
            }
        }
    }
}

/**
 * Skips to the next track via [MediaController] connected to [AudioPlayerService].
 */
class SkipNextAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        executeMediaCommand(context) { controller ->
            if (controller.hasNextMediaItem()) {
                controller.seekToNextMediaItem()
            } else {
                controller.seekToNext()
            }
            if (!controller.isPlaying && controller.mediaItemCount > 0) {
                controller.play()
            }
        }
    }
}

/**
 * Skips to the previous track via [MediaController] connected to [AudioPlayerService].
 */
class SkipPreviousAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        executeMediaCommand(context) { controller ->
            if (controller.hasPreviousMediaItem()) {
                controller.seekToPreviousMediaItem()
            } else {
                controller.seekToPrevious()
            }
            if (!controller.isPlaying && controller.mediaItemCount > 0) {
                controller.play()
            }
        }
    }
}

@OptIn(UnstableApi::class)
private suspend fun executeMediaCommand(
    context: Context,
    command: (MediaController) -> Unit
) {
    val appContext = context.applicationContext
    val sessionToken = SessionToken(
        appContext,
        ComponentName(appContext, AudioPlayerService::class.java)
    )
    withContext(Dispatchers.Main) {
        val controllerFuture = MediaController.Builder(appContext, sessionToken).buildAsync()
        try {
            val controller = withTimeoutOrNull(CONTROLLER_CONNECT_TIMEOUT_MS) {
                suspendCancellableCoroutine { continuation ->
                    controllerFuture.addListener(
                        {
                            try {
                                continuation.resume(controllerFuture.get())
                            } catch (e: Exception) {
                                continuation.resumeWithException(e)
                            }
                        },
                        ContextCompat.getMainExecutor(appContext)
                    )
                }
            }
            if (controller != null) {
                command(controller)
            } else {
                Log.w(TAG, "Timed out connecting MediaController to AudioPlayerService")
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute widget media command", e)
        } finally {
            MediaController.releaseFuture(controllerFuture)
        }
    }
}

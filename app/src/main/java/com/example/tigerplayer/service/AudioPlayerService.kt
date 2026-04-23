package com.example.tigerplayer.service

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.Equalizer
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.tigerplayer.MainActivity
import com.example.tigerplayer.R
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

enum class EqMode { BALANCE, TRANSPARENCY, ISOLATION }
class AudioPlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private var equalizer: Equalizer? = null
    private var currentMode = EqMode.BALANCE

    companion object {
        private const val CUSTOM_COMMAND_SHUFFLE = "ACTION_SHUFFLE"
        private const val CUSTOM_COMMAND_REPEAT = "ACTION_REPEAT"
        const val ACTION_SET_EQ = "ACTION_SET_EQ"
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // 1. High-Fidelity Audio Setup
        val audioSink = DefaultAudioSink.Builder(this)
            .setEnableFloatOutput(true)
            .build()

        val renderersFactory = DefaultRenderersFactory(this).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        }

        player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        // 2. The "OS Sync" Listener
        // This ensures that if the OS, a Bluetooth device, or the UI changes the state,
        // the notification shade is immediately forced to redraw.
        player.addListener(object : Player.Listener {
            override fun onRepeatModeChanged(repeatMode: Int) = invalidateCustomLayout()
            override fun onShuffleModeEnabledChanged(enabled: Boolean) = invalidateCustomLayout()
        })

        // 3. MediaSession Setup
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(CustomMediaSessionCallback())
            .setSessionActivity(pendingIntent)
            .build()

        invalidateCustomLayout()
    }

    fun applyEqMode(mode: EqMode) {
        currentMode = mode
        val eq = equalizer ?: return

        when (mode) {
            EqMode.BALANCE -> {
                // Flatten all bands
                for (i in 0 until eq.numberOfBands) {
                    eq.setBandLevel(i.toShort(), 0)
                }
            }
            EqMode.TRANSPARENCY -> {
                // Boost High-Mids and Treble for clarity
                eq.setBandLevel(0, -200) // Cut Bass
                eq.setBandLevel(3, 600)  // Boost 2kHz (+6dB)
                eq.setBandLevel(4, 800)  // Boost 4kHz (+8dB)
            }
            EqMode.ISOLATION -> {
                // The "Igni" Boost (Sub-bass emphasis)
                eq.setBandLevel(0, 1000) // Heavy Bass (+10dB)
                eq.setBandLevel(1, 400)  // Mid Bass (+4dB)
                eq.setBandLevel(4, -400) // Cut Treble (-4dB)
            }
        }
    }


    /**
     * Updates the Notification Shade using Material Design Icons.
     * Ensure these R.drawable IDs exist in your project.
     */
    private fun invalidateCustomLayout() {
        // Shuffle Icon Selection
        val shuffleIcon = if (player.shuffleModeEnabled) {
            R.drawable.ic_material_shuffle_on // Material Shuffle (Enabled/Colored)
        } else {
            R.drawable.ic_material_shuffle_off // Material Shuffle (Disabled/Grey)
        }

        // Repeat Icon Selection
        val repeatIcon = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> R.drawable.ic_material_repeat_one
            Player.REPEAT_MODE_ALL -> R.drawable.ic_material_repeat_all
            else -> R.drawable.ic_material_repeat_off
        }

        val shuffleButton = CommandButton.Builder()
            .setSessionCommand(SessionCommand(CUSTOM_COMMAND_SHUFFLE, Bundle.EMPTY))
            .setIconResId(shuffleIcon)
            .setDisplayName("Shuffle")
            .build()

        val repeatButton = CommandButton.Builder()
            .setSessionCommand(SessionCommand(CUSTOM_COMMAND_REPEAT, Bundle.EMPTY))
            .setIconResId(repeatIcon)
            .setDisplayName("Repeat")
            .build()

        // This call pushes the update to the System UI / Notification Shade
        mediaSession?.setCustomLayout(listOf(shuffleButton, repeatButton))
    }

    // Standard MediaSession Service boilerplate
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }


    // ... inside AudioPlayerService ...

    private inner class CustomMediaSessionCallback : MediaSession.Callback {
        @OptIn(UnstableApi::class)
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            // 1. Define the commands the System UI is allowed to send
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(CUSTOM_COMMAND_SHUFFLE, Bundle.EMPTY))
                .add(SessionCommand(CUSTOM_COMMAND_REPEAT, Bundle.EMPTY))
                .add(SessionCommand(ACTION_SET_EQ, Bundle.EMPTY))
                .build()

            // 2. THE OPTIMIZATION: Build the initial layout immediately for the handshake
            val initialLayout = createCustomLayoutList()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setCustomLayout(initialLayout) // Push layout during the first handshake
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CUSTOM_COMMAND_SHUFFLE -> player.shuffleModeEnabled = !player.shuffleModeEnabled
                CUSTOM_COMMAND_REPEAT -> {
                    player.repeatMode = when (player.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                }
                ACTION_SET_EQ -> {
                    val modeName = args.getString("mode") ?: EqMode.BALANCE.name
                    applyEqMode(EqMode.valueOf(modeName))
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    /**
     * Refactored to return the list so it can be used in both
     * invalidateCustomLayout() and onConnect().
     */
    private fun createCustomLayoutList(): List<CommandButton> {
        val shuffleIcon = if (player.shuffleModeEnabled) {
            R.drawable.ic_material_shuffle_on
        } else {
            R.drawable.ic_material_shuffle_off
        }

        val repeatIcon = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> R.drawable.ic_material_repeat_one
            Player.REPEAT_MODE_ALL -> R.drawable.ic_material_repeat_all
            else -> R.drawable.ic_material_repeat_off
        }

        return listOf(
            CommandButton.Builder()
                .setSessionCommand(SessionCommand(CUSTOM_COMMAND_SHUFFLE, Bundle.EMPTY))
                .setIconResId(shuffleIcon)
                .setDisplayName("Shuffle")
                .setEnabled(true)
                .build(),
            CommandButton.Builder()
                .setSessionCommand(SessionCommand(CUSTOM_COMMAND_REPEAT, Bundle.EMPTY))
                .setIconResId(repeatIcon)
                .setDisplayName("Repeat")
                .setEnabled(true)
                .build()
        )
    }


}
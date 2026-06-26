package com.example.tigerplayer.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.tigerplayer.MainActivity
import com.example.tigerplayer.R
import com.example.tigerplayer.engine.AcousticNode
import com.example.tigerplayer.engine.AcousticEnvironmentMode
import com.example.tigerplayer.engine.AdaptiveDspEngine
import com.example.tigerplayer.engine.FilterType
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.pow

data class PeqBand(val type: String, val frequency: Float, val gain: Float, val q: Float)
data class PeqProfile(val name: String, val preamp: Float, val bands: List<PeqBand>, val rawText: String = "")

@Suppress("DEPRECATION")
@UnstableApi
@AndroidEntryPoint
class AudioPlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer

    @Inject lateinit var adaptiveDspEngine: AdaptiveDspEngine

    private var isBitPerfectMode = true
    private var currentProfile: PeqProfile? = null

    companion object {
        private const val CUSTOM_COMMAND_SHUFFLE = "ACTION_SHUFFLE"
        private const val CUSTOM_COMMAND_REPEAT = "ACTION_REPEAT"
        const val ACTION_TOGGLE_DSP = "ACTION_TOGGLE_DSP"
        const val ACTION_LOAD_PEQ = "ACTION_LOAD_PEQ"
        const val ACTION_SET_ACOUSTIC_ENVIRONMENT = "ACTION_SET_ACOUSTIC_ENVIRONMENT"
        const val EXTRA_ACOUSTIC_ENVIRONMENT_MODE = "EXTRA_ACOUSTIC_ENVIRONMENT_MODE"
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // 🔥 THE FIX 1: Removed Float Output to prevent the Samsung HAL Timing Glitch
        val audioSink: AudioSink = DefaultAudioSink.Builder(this)
            .setAudioProcessors(arrayOf(adaptiveDspEngine))
            .build()

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParams: Boolean
            ): AudioSink {
                return audioSink
            }
        }.apply {
            setExtensionRendererMode(EXTENSION_RENDERER_MODE_PREFER)
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        setAudioOffloadEnabled(true)

        player.addListener(object : Player.Listener {
            override fun onRepeatModeChanged(repeatMode: Int) = invalidateCustomLayout()
            override fun onShuffleModeEnabledChanged(enabled: Boolean) = invalidateCustomLayout()
        })

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(CustomMediaSessionCallback())
            .setSessionActivity(pendingIntent)
            .build()

        invalidateCustomLayout()
    }

    @OptIn(UnstableApi::class)
    private fun setAudioOffloadEnabled(enabled: Boolean) {
        isBitPerfectMode = enabled

        val offloadMode = if (enabled) {
            AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
        } else {
            AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
        }

        // 🔥 THE FIX 2: Gracefully shutdown the player before hot-swapping Audio Offload.
        // This forces ExoPlayer to clear the AudioSink hardware, preventing PTS Discontinuity.
        val wasPlaying = player.isPlaying
        val currentPosition = player.currentPosition

        player.stop()

        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setAudioOffloadPreferences(
                AudioOffloadPreferences.Builder()
                    .setAudioOffloadMode(offloadMode)
                    .build()
            )
            .build()

        if (enabled) {
            player.volume = 1f
        } else {
            currentProfile?.let { applyProfileToDsp(it) }
        }

        player.seekTo(currentPosition)
        player.prepare()
        if (wasPlaying) {
            player.play()
        }
    }

    private fun applyProfileToDsp(profile: PeqProfile) {
        if (isBitPerfectMode) return

        player.volume = 10.0.pow(profile.preamp / 20.0).toFloat()

        val acousticNodes = profile.bands.mapIndexed { index, band ->
            val type = when (band.type.uppercase()) {
                "LS" -> FilterType.LOW_SHELF
                "HS" -> FilterType.HIGH_SHELF
                else -> FilterType.PEAKING
            }

            AcousticNode(
                id = "band_$index", label = "Band $index", filterType = type,
                frequency = band.frequency, gainDb = band.gain, qFactor = band.q
            )
        }

        adaptiveDspEngine.updateAcousticNodes(acousticNodes)
    }

    fun loadAutoEqPreset(rawText: String, profileName: String) {
        currentProfile = AutoEqParser.parse(rawText, profileName)
        setAudioOffloadEnabled(false)
    }

    private fun invalidateCustomLayout() {
        mediaSession?.setCustomLayout(createCustomLayoutList())
    }

    private fun createCustomLayoutList(): List<CommandButton> {
        val shuffleIcon = if (player.shuffleModeEnabled) R.drawable.ic_material_shuffle_on else R.drawable.ic_material_shuffle_off
        val repeatIcon = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> R.drawable.ic_material_repeat_one
            Player.REPEAT_MODE_ALL -> R.drawable.ic_material_repeat_all
            else -> R.drawable.ic_material_repeat_off
        }
        val dspIcon = if (isBitPerfectMode) R.drawable.ic_material_shuffle_off else R.drawable.ic_material_shuffle_on

        return listOf(
            CommandButton.Builder().setSessionCommand(SessionCommand(CUSTOM_COMMAND_SHUFFLE, Bundle.EMPTY)).setIconResId(shuffleIcon).setDisplayName("Shuffle").setEnabled(true).build(),
            CommandButton.Builder().setSessionCommand(SessionCommand(CUSTOM_COMMAND_REPEAT, Bundle.EMPTY)).setIconResId(repeatIcon).setDisplayName("Repeat").setEnabled(true).build(),
            CommandButton.Builder().setSessionCommand(SessionCommand(ACTION_TOGGLE_DSP, Bundle.EMPTY)).setIconResId(dspIcon).setDisplayName(if (isBitPerfectMode) "Bit-Perfect" else "Aural Nexus Active").setEnabled(true).build()
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private inner class CustomMediaSessionCallback : MediaSession.Callback {
        @OptIn(UnstableApi::class)
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(CUSTOM_COMMAND_SHUFFLE, Bundle.EMPTY)).add(SessionCommand(CUSTOM_COMMAND_REPEAT, Bundle.EMPTY))
                .add(SessionCommand(ACTION_TOGGLE_DSP, Bundle.EMPTY))
                .add(SessionCommand(ACTION_LOAD_PEQ, Bundle.EMPTY))
                .add(SessionCommand(ACTION_SET_ACOUSTIC_ENVIRONMENT, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session).setAvailableSessionCommands(sessionCommands).setCustomLayout(createCustomLayoutList()).build()
        }

        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CUSTOM_COMMAND_SHUFFLE -> player.shuffleModeEnabled = !player.shuffleModeEnabled
                CUSTOM_COMMAND_REPEAT -> {
                    player.repeatMode = when (player.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                }
                ACTION_TOGGLE_DSP -> {
                    setAudioOffloadEnabled(!isBitPerfectMode)
                    invalidateCustomLayout()
                }
                ACTION_LOAD_PEQ -> {
                    val rawText = args.getString("peq_raw_text") ?: ""
                    val profileName = args.getString("peq_profile_name") ?: "Custom Nexus Shape"
                    loadAutoEqPreset(rawText, profileName)
                    invalidateCustomLayout()
                }
                ACTION_SET_ACOUSTIC_ENVIRONMENT -> {
                    val modeName = args.getString(EXTRA_ACOUSTIC_ENVIRONMENT_MODE)
                    val mode = runCatching {
                        AcousticEnvironmentMode.valueOf(modeName ?: AcousticEnvironmentMode.OFF.name)
                    }.getOrDefault(AcousticEnvironmentMode.OFF)
                    adaptiveDspEngine.setAcousticEnvironmentMode(mode)
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }
}

object AutoEqParser {
    fun parse(rawText: String, profileName: String): PeqProfile {
        var preamp = 0f
        val bands = mutableListOf<PeqBand>()

        val lines = rawText.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("Preamp:")) {
                preamp = trimmed.substringAfter("Preamp:").substringBefore("dB").trim().replace(',', '.').toFloatOrNull() ?: 0f
            } else if (trimmed.startsWith("Filter")) {
                try {
                    val parts = trimmed.split(Regex("\\s+"))
                    val type = parts[3]
                    val fcIndex = parts.indexOf("Fc") + 1
                    val freq = parts[fcIndex].replace(',', '.').toFloat()
                    val gainIndex = parts.indexOf("Gain") + 1
                    val gain = parts[gainIndex].replace(',', '.').toFloat()
                    val qIndex = parts.indexOf("Q") + 1
                    val q = if (qIndex > 0 && qIndex < parts.size) parts[qIndex].replace(',', '.').toFloat() else 1.0f

                    bands.add(PeqBand(type, freq, gain, q))
                } catch (_: Exception) {}
            }
        }
        return PeqProfile(profileName, preamp, bands, rawText)
    }
}
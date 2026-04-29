package com.example.tigerplayer.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.ui.graphics.Color
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
import com.example.tigerplayer.engine.dsp.AcousticNode
import com.example.tigerplayer.engine.dsp.AdaptiveDspEngine
import com.example.tigerplayer.engine.dsp.FilterType
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.pow


// --- PEQ Data Models ---
data class PeqBand(val type: String, val frequency: Float, val gain: Float, val q: Float)
data class PeqProfile(val name: String, val preamp: Float, val bands: List<PeqBand>, val rawText: String = "")

@Suppress("DEPRECATION")
@UnstableApi
@AndroidEntryPoint // 🔥 REQUIRED: Allows Hilt to inject into the Android Service
class AudioPlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer

    // 🔥 THE NEW FLAGSHIP DSP ENGINE
    @Inject lateinit var adaptiveDspEngine: AdaptiveDspEngine

    private var isBitPerfectMode = true
    private var currentProfile: PeqProfile? = null

    companion object {
        private const val CUSTOM_COMMAND_SHUFFLE = "ACTION_SHUFFLE"
        private const val CUSTOM_COMMAND_REPEAT = "ACTION_REPEAT"
        const val ACTION_TOGGLE_DSP = "ACTION_TOGGLE_DSP"
        const val ACTION_LOAD_PEQ = "ACTION_LOAD_PEQ"
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // 1. Bit-Perfect Audio Sink Setup WITH DSP INJECTION
        val audioSink: AudioSink = DefaultAudioSink.Builder(this)
            .setAudioProcessors(arrayOf(adaptiveDspEngine)) // 🔥 INTERCEPTS THE RAW PCM BYTE STREAM
            .setEnableFloatOutput(false) // Force integer output for exact bit-depth matching
            .build()

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParams: Boolean
            ): AudioSink {
                return audioSink
            }
        }.apply {
            setExtensionRendererMode(EXTENSION_RENDERER_MODE_PREFER)
        }

        // 2. CRUCIAL PATCH: Proper Audio Attributes & Focus
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, true) // Auto-handle Audio Focus (Pause on call, duck on notifications)
            .setHandleAudioBecomingNoisy(true) // Pause when headphones are unplugged
            .setWakeMode(C.WAKE_MODE_LOCAL) // Keeps CPU awake during background Offload playback
            .build()

        // Enable Offload by default for true bit-perfect bypass
        setAudioOffloadEnabled(true)

        player.addListener(object : Player.Listener {
            override fun onRepeatModeChanged(repeatMode: Int) = invalidateCustomLayout()
            override fun onShuffleModeEnabledChanged(enabled: Boolean) = invalidateCustomLayout()

            // NOTE: We no longer need onAudioSessionIdChanged!
            // Our custom DSP works universally inside ExoPlayer, immune to Android AudioSession bugs.
        })

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(CustomMediaSessionCallback())
            .setSessionActivity(pendingIntent)
            .build()

        invalidateCustomLayout()
    }

    /**
     * THE BIT-PERFECT SWITCH
     * Audio Offload bypasses the Android OS AudioFlinger mixer AND our custom AudioProcessors entirely,
     * sending the raw FLAC payload straight to the hardware DAC.
     */
    @OptIn(UnstableApi::class)
    private fun setAudioOffloadEnabled(enabled: Boolean) {
        isBitPerfectMode = enabled
        val offloadMode = if (enabled) {
            AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
        } else {
            AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
        }

        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setAudioOffloadPreferences(
                AudioOffloadPreferences.Builder()
                    .setAudioOffloadMode(offloadMode)
                    .build()
            )
            .build()

        if (enabled) {
            // Bypass mode: Reset volume
            player.volume = 1f
        } else {
            // DSP Mode: Re-apply current Aural Nexus profile to the engine
            currentProfile?.let { applyProfileToDsp(it) }
        }
    }

    /**
     * THE NEW DSP PIPELINE
     * Translates the UI's AutoEq commands into Mathematical AcousticNodes
     * and feeds them instantly to our custom hardware engine.
     */
    private fun applyProfileToDsp(profile: PeqProfile) {
        if (isBitPerfectMode) return

        // Apply Pre-amp to ExoPlayer volume to prevent digital clipping!
        player.volume = 10.0.pow(profile.preamp / 20.0).toFloat()

        // Map AutoEq bands to our Custom DSP Engine
        val acousticNodes = profile.bands.mapIndexed { index, band ->
            val type = when (band.type.uppercase()) {
                "LS" -> FilterType.LOW_SHELF
                "HS" -> FilterType.HIGH_SHELF
                else -> FilterType.PEAKING
            }

            AcousticNode(
                id = "band_$index",
                label = "Band $index",
                filterType = type,
                frequency = band.frequency,
                gainDb = band.gain,
                qFactor = band.q,
                color = Color.Transparent // Colors are handled entirely by the UI
            )
        }

        adaptiveDspEngine.updateAcousticNodes(acousticNodes)
        Log.d("DSP_Engine", "Quantum Adaptive DSP Profile Applied: ${profile.name}")
    }

    fun loadAutoEqPreset(rawText: String, profileName: String) {
        currentProfile = AutoEqParser.parse(rawText, profileName)
        // Disable bit-perfect and force the engine to inject the new curve
        setAudioOffloadEnabled(false)
    }

    private fun invalidateCustomLayout() {
        mediaSession?.setCustomLayout(createCustomLayoutList())
    }

    private fun createCustomLayoutList(): List<CommandButton> {

        val shuffleIcon =
            if (player.shuffleModeEnabled)
                R.drawable.ic_material_shuffle_on
            else
                R.drawable.ic_material_shuffle_off

        val repeatIcon = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> R.drawable.ic_material_repeat_one
            Player.REPEAT_MODE_ALL -> R.drawable.ic_material_repeat_all
            else -> R.drawable.ic_material_repeat_off
        }

        val dspIcon =
            if (isBitPerfectMode)
                R.drawable.ic_material_shuffle_off
            else
                R.drawable.ic_material_shuffle_on

        return listOf(
            // 🔥 THE FIX: Use empty constructor and chain .setSessionCommand()
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
                .build(),

            CommandButton.Builder()
                .setSessionCommand(SessionCommand(ACTION_TOGGLE_DSP, Bundle.EMPTY))
                .setIconResId(dspIcon)
                .setDisplayName(if (isBitPerfectMode) "Bit-Perfect" else "Aural Nexus Active")
                .setEnabled(true)
                .build()
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
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(CUSTOM_COMMAND_SHUFFLE, Bundle.EMPTY))
                .add(SessionCommand(CUSTOM_COMMAND_REPEAT, Bundle.EMPTY))
                .add(SessionCommand(ACTION_TOGGLE_DSP, Bundle.EMPTY))
                .add(SessionCommand(ACTION_LOAD_PEQ, Bundle.EMPTY))
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setCustomLayout(createCustomLayoutList())
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
                ACTION_TOGGLE_DSP -> {
                    // Toggle between raw bit-perfect and hardware DSP
                    setAudioOffloadEnabled(!isBitPerfectMode)
                    invalidateCustomLayout()
                }
                ACTION_LOAD_PEQ -> {
                    val rawText = args.getString("peq_raw_text") ?: ""
                    val profileName = args.getString("peq_profile_name") ?: "Custom Nexus Shape"
                    loadAutoEqPreset(rawText, profileName)
                    invalidateCustomLayout()
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }
}

/**
 * THE ARCHIVE TRANSLATOR
 * Parses raw AutoEq / Parametric EQ text data into strongly typed models.
 */
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
                } catch (_: Exception) {
                    Log.w("AutoEqParser", "Failed to decode line: $line")
                }
            }
        }
        return PeqProfile(profileName, preamp, bands, rawText)
    }
}
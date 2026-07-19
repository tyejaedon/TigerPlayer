package com.example.tigerplayer.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
import com.example.tigerplayer.BuildConfig
import com.example.tigerplayer.data.local.AudioReactiveHapticsProfile
import com.example.tigerplayer.data.local.PlaybackPrefs
import com.example.tigerplayer.data.local.SettingsDataStore
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds

data class PeqBand(val type: String, val frequency: Float, val gain: Float, val q: Float)
data class PeqProfile(val name: String, val preamp: Float, val bands: List<PeqBand>, val rawText: String = "")

@Suppress("DEPRECATION")
@UnstableApi
@AndroidEntryPoint
class AudioPlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer

    @Inject lateinit var adaptiveDspEngine: AdaptiveDspEngine
    @Inject lateinit var fftProcessor: FftProcessor
    @Inject lateinit var waveformCaptureProcessor: WaveformCaptureProcessor
    @Inject lateinit var playbackPrefs: PlaybackPrefs
    @Inject lateinit var settingsDataStore: SettingsDataStore
    @Inject lateinit var hapticsDebugMonitor: HapticsDebugMonitor

    private var isBitPerfectMode = false // Initial state to ensure first call triggers
    private var routeToSystemDecoderDsp = false
    private var audioReactiveHapticsEnabled = false
    private var fullPlayerActive = false
    private var audioReactiveHapticsProfile = AudioReactiveHapticsProfile.BALANCED
    private var lastAudioReactiveHapticAtMs = 0L
    private var hapticScoreSmoothed = 0f
    private var lastHapticSkipReason: String? = null
    private var lastHapticSkipLogAtMs = 0L
    private var lastHapticSkipPublishAtMs = 0L
    private var hapticPulseCount = 0L
    private var currentProfile: PeqProfile? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    companion object {
        private const val TAG = "AudioPlayerService"
        private const val CUSTOM_COMMAND_SHUFFLE = "ACTION_SHUFFLE"
        private const val CUSTOM_COMMAND_REPEAT = "ACTION_REPEAT"
        const val ACTION_TOGGLE_DSP = "ACTION_TOGGLE_DSP"
        const val ACTION_LOAD_PEQ = "ACTION_LOAD_PEQ"
        const val ACTION_SET_ACOUSTIC_ENV = "ACTION_SET_ACOUSTIC_ENV"
        const val EXTRA_ACOUSTIC_ENV_MODE = "EXTRA_ACOUSTIC_ENV_MODE"
        private const val AUDIO_REACTIVE_HAPTIC_COOLDOWN_MS = 90L
        private const val AUDIO_REACTIVE_HAPTIC_MIN_SCORE = 0.46f
        private const val AUDIO_REACTIVE_HAPTIC_MIN_DURATION_MS = 10L
        private const val AUDIO_REACTIVE_HAPTIC_MAX_DURATION_MS = 20L
        private const val AUDIO_REACTIVE_HAPTIC_MIN_AMPLITUDE = 40
        private const val AUDIO_REACTIVE_HAPTIC_MAX_AMPLITUDE = 255
    }

    private data class AudioReactiveHapticProfileConfig(
        val cooldownMs: Long,
        val minScore: Float,
        val minDurationMs: Long,
        val maxDurationMs: Long,
        val minAmplitude: Int,
        val maxAmplitude: Int,
        val bassWeight: Float,
        val energyWeight: Float,
        val fluxWeight: Float,
        val riseSmoothing: Float,
        val fallSmoothing: Float
    )

    private val isSamsungDevice: Boolean by lazy {
        Build.MANUFACTURER.equals("samsung", ignoreCase = true) ||
            Build.BRAND.equals("samsung", ignoreCase = true)
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // Samsung devices can still exhibit HAL instability in float output mode.
        // Keep float enabled on other OEMs for high-resolution paths.
        val enableFloatOutput = !isSamsungDevice
        if (!enableFloatOutput) {
            Log.w(TAG, "Samsung compatibility mode active: forcing PCM output (float disabled)")
        }

        val audioSink: AudioSink = DefaultAudioSink.Builder(this)
            .setAudioProcessors(arrayOf(
                adaptiveDspEngine,
                fftProcessor,
                waveformCaptureProcessor
            ))
            .setEnableFloatOutput(enableFloatOutput)
            .build()

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioOutputPlaybackParams: Boolean
            ): AudioSink = audioSink
        }.apply {
            setExtensionRendererMode(EXTENSION_RENDERER_MODE_PREFER)
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            // AUDIOPHILE COMPATIBILITY: Explicitly flag as high-priority music 
            // to improve Samsung's "Separate App Sound" routing accuracy.
            .setAllowedCapturePolicy(C.ALLOW_CAPTURE_BY_NONE)
            .build()

        player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        serviceScope.launch {
            val savedModeName = playbackPrefs.acousticEnvironmentMode.firstOrNull() ?: AcousticEnvironmentMode.STUDIO.name
            val savedMode = runCatching { AcousticEnvironmentMode.valueOf(savedModeName) }
                .getOrDefault(AcousticEnvironmentMode.STUDIO)
            adaptiveDspEngine.setAcousticEnvironmentMode(savedMode)
        }

        serviceScope.launch {
            settingsDataStore.settingsFlow
                .map { it.routeToSystemDecoderDsp }
                .distinctUntilChanged()
                .collect { routeToSystem ->
                    routeToSystemDecoderDsp = routeToSystem
                    logHapticDebug("route_to_system_decoder_dsp=$routeToSystem")
                    publishHapticsStatus(reason = "route_change")
                    setAudioOffloadEnabled(routeToSystem)
                    invalidateCustomLayout()
                }
        }

        serviceScope.launch {
            settingsDataStore.settingsFlow
                .map { it.audioReactiveHaptics }
                .distinctUntilChanged()
                .collect { enabled ->
                    audioReactiveHapticsEnabled = enabled
                    logHapticDebug("audio_reactive_haptics_enabled=$enabled")
                    publishHapticsStatus(reason = "toggle_change")
                    if (!enabled) {
                        lastAudioReactiveHapticAtMs = 0L
                        hapticScoreSmoothed = 0f
                    }
                }
        }

        serviceScope.launch {
            settingsDataStore.settingsFlow
                .map { it.audioReactiveHapticsProfile }
                .distinctUntilChanged()
                .collect { profile ->
                    audioReactiveHapticsProfile = profile
                    logHapticDebug("audio_reactive_haptics_profile=$profile")
                    publishHapticsStatus(reason = "profile_change")
                    lastAudioReactiveHapticAtMs = 0L
                    hapticScoreSmoothed = 0f
                }
        }

        serviceScope.launch {
            playbackPrefs.fullPlayerActive
                .distinctUntilChanged()
                .collect { active ->
                    fullPlayerActive = active
                    logHapticDebug("full_player_active=$active")
                    publishHapticsStatus(reason = "full_player_visibility_change")
                    if (!active) {
                        lastAudioReactiveHapticAtMs = 0L
                        hapticScoreSmoothed = 0f
                    }
                }
        }

        serviceScope.launch {
            adaptiveDspEngine.audioReactiveFrame.collect { frame ->
                maybeEmitAudioReactiveHaptic(
                    bass = frame.bass,
                    energy = frame.energy,
                    flux = frame.flux
                )
            }
        }

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
        // AUDIOPHILE COMPATIBILITY: On Samsung, we ONLY allow Offload in Bit-Perfect mode.
        // If we are in "Aural Nexus" (DSP) mode, we must use the standard path to avoid HAL Float glitches.
        val effectiveEnabled = if (isSamsungDevice) {
            enabled // Allow offload on Samsung if requested (Bit-Perfect)
        } else {
            enabled
        }

        if (isBitPerfectMode == effectiveEnabled) return
        isBitPerfectMode = effectiveEnabled

        val offloadMode = if (effectiveEnabled) {
            AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
        } else {
            AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
        }

        serviceScope.launch {
            val wasPlaying = player.isPlaying
            val currentPosition = player.currentPosition
            val startVolume = player.volume

            // FADED SWAP: Ramp down before hardware reset to hide the click/pop
            if (wasPlaying) {
                val steps = 8
                for (i in 1..steps) {
                    player.volume = startVolume * (1f - i.toFloat() / steps)
                    delay(20.milliseconds) // Quicker UI increments
                }
                // FIX: Add generous delay to allow A2DP/Hardware buffers to actually drain
                delay(350.milliseconds)
            }

            player.stop()

            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setAudioOffloadPreferences(
                    AudioOffloadPreferences.Builder()
                        .setAudioOffloadMode(offloadMode)
                        .build()
                )
                .build()

            if (effectiveEnabled) {
                player.volume = 1f
            } else {
                currentProfile?.let { applyProfileToDsp(it) }
            }

            player.seekTo(currentPosition)
            player.prepare()
            
            if (wasPlaying) {
                player.play()
                // Ramp back up smoothly
                val targetVolume = player.volume
                player.volume = 0f
                val steps = 8
                for (i in 1..steps) {
                    player.volume = targetVolume * (i.toFloat() / steps)
                    delay(40.milliseconds)
                }
                player.volume = targetVolume
            }
        }
    }

    private fun applyProfileToDsp(profile: PeqProfile) {
        if (isBitPerfectMode) return

        val safePreampDb = profile.preamp.coerceAtMost(0f)
        player.volume = 10.0.pow(safePreampDb / 20.0).toFloat().coerceIn(0.05f, 1.0f)

        val acousticNodes = profile.bands.mapIndexed { index, band ->
            val type = when (band.type.uppercase()) {
                "LS" -> FilterType.LOW_SHELF
                "HS" -> FilterType.HIGH_SHELF
                else -> FilterType.PEAKING
            }

            AcousticNode(
                id = "band_$index", label = "Band $index", filterType = type,
                frequency = band.frequency.coerceIn(20f, 20_000f),
                gainDb = band.gain.coerceIn(-12f, 12f),
                qFactor = band.q.coerceIn(0.2f, 8f)
            )
        }

        adaptiveDspEngine.updateAcousticNodes(acousticNodes)
    }

    private fun maybeEmitAudioReactiveHaptic(bass: Float, energy: Float, flux: Float) {
        if (!audioReactiveHapticsEnabled) {
            publishHapticSkip(
                reason = "disabled",
                bass = bass,
                energy = energy,
                flux = flux
            )
            logHapticSkip("disabled")
            return
        }
        if (!fullPlayerActive) {
            publishHapticSkip(
                reason = "not_fullscreen",
                bass = bass,
                energy = energy,
                flux = flux
            )
            logHapticSkip("not_fullscreen")
            return
        }
        if (!player.isPlaying) {
            publishHapticSkip(
                reason = "not_playing",
                bass = bass,
                energy = energy,
                flux = flux
            )
            logHapticSkip("not_playing")
            return
        }
        // In bit-perfect/offload mode, DSP analysis isn't the source of truth.
        if (isBitPerfectMode || routeToSystemDecoderDsp) {
            publishHapticSkip(
                reason = "offload_route",
                bass = bass,
                energy = energy,
                flux = flux
            )
            logHapticSkip(
                "offload_route bitPerfect=$isBitPerfectMode routeToSystem=$routeToSystemDecoderDsp"
            )
            return
        }

        val profile = resolveAudioReactiveHapticProfile(audioReactiveHapticsProfile)

        val nowMs = SystemClock.elapsedRealtime()
        val elapsedSinceLast = nowMs - lastAudioReactiveHapticAtMs
        if (elapsedSinceLast < profile.cooldownMs) {
            publishHapticSkip(
                reason = "cooldown",
                bass = bass,
                energy = energy,
                flux = flux,
                minScore = profile.minScore,
                cooldownRemainingMs = profile.cooldownMs - elapsedSinceLast
            )
            logHapticSkip("cooldown ${profile.cooldownMs - elapsedSinceLast}ms")
            return
        }

        val scoreRaw = (
            bass * profile.bassWeight +
                energy * profile.energyWeight +
                flux * profile.fluxWeight
            ).coerceIn(0f, 1f)
        val smoothing = if (scoreRaw > hapticScoreSmoothed) profile.riseSmoothing else profile.fallSmoothing
        hapticScoreSmoothed += (scoreRaw - hapticScoreSmoothed) * smoothing

        if (hapticScoreSmoothed < profile.minScore) {
            publishHapticSkip(
                reason = "below_threshold",
                bass = bass,
                energy = energy,
                flux = flux,
                rawScore = scoreRaw,
                smoothedScore = hapticScoreSmoothed,
                minScore = profile.minScore
            )
            logHapticSkip(
                "below_threshold score=${"%.3f".format(hapticScoreSmoothed)} min=${profile.minScore} " +
                    "bass=${"%.3f".format(bass)} energy=${"%.3f".format(energy)} flux=${"%.3f".format(flux)}"
            )
            return
        }

        val durationMs = (
            profile.minDurationMs +
                (profile.maxDurationMs - profile.minDurationMs) * hapticScoreSmoothed
            )
            .toLong()
            .coerceIn(profile.minDurationMs, profile.maxDurationMs)
        val amplitude = (
            profile.minAmplitude + (profile.maxAmplitude - profile.minAmplitude) * hapticScoreSmoothed
            )
            .toInt()
            .coerceIn(profile.minAmplitude, profile.maxAmplitude)

        emitHapticPulse(durationMs = durationMs, amplitude = amplitude)
        hapticPulseCount += 1L
        publishHapticFire(
            bass = bass,
            energy = energy,
            flux = flux,
            rawScore = scoreRaw,
            smoothedScore = hapticScoreSmoothed,
            minScore = profile.minScore,
            amplitude = amplitude,
            durationMs = durationMs
        )
        logHapticDebug(
            "fire#$hapticPulseCount amp=$amplitude dur=${durationMs}ms " +
                "score=${"%.3f".format(hapticScoreSmoothed)} raw=${"%.3f".format(scoreRaw)}"
        )
        lastAudioReactiveHapticAtMs = nowMs
    }

    private fun resolveAudioReactiveHapticProfile(
        profile: AudioReactiveHapticsProfile
    ): AudioReactiveHapticProfileConfig {
        return when (profile) {
            AudioReactiveHapticsProfile.SUBTLE -> AudioReactiveHapticProfileConfig(
                cooldownMs = 130L,
                minScore = 0.62f,
                minDurationMs = 8L,
                maxDurationMs = 14L,
                minAmplitude = 30,
                maxAmplitude = 150,
                bassWeight = 0.54f,
                energyWeight = 0.24f,
                fluxWeight = 0.48f,
                riseSmoothing = 0.24f,
                fallSmoothing = 0.10f
            )

            AudioReactiveHapticsProfile.BALANCED -> AudioReactiveHapticProfileConfig(
                cooldownMs = AUDIO_REACTIVE_HAPTIC_COOLDOWN_MS,
                minScore = AUDIO_REACTIVE_HAPTIC_MIN_SCORE,
                minDurationMs = AUDIO_REACTIVE_HAPTIC_MIN_DURATION_MS,
                maxDurationMs = AUDIO_REACTIVE_HAPTIC_MAX_DURATION_MS,
                minAmplitude = AUDIO_REACTIVE_HAPTIC_MIN_AMPLITUDE,
                maxAmplitude = AUDIO_REACTIVE_HAPTIC_MAX_AMPLITUDE,
                bassWeight = 0.58f,
                energyWeight = 0.30f,
                fluxWeight = 0.70f,
                riseSmoothing = 0.28f,
                fallSmoothing = 0.12f
            )

            AudioReactiveHapticsProfile.AGGRESSIVE -> AudioReactiveHapticProfileConfig(
                cooldownMs = 60L,
                minScore = 0.34f,
                minDurationMs = 12L,
                maxDurationMs = 28L,
                minAmplitude = 60,
                maxAmplitude = 255,
                bassWeight = 0.62f,
                energyWeight = 0.34f,
                fluxWeight = 0.86f,
                riseSmoothing = 0.34f,
                fallSmoothing = 0.16f
            )
        }
    }

    private fun emitHapticPulse(durationMs: Long, amplitude: Int) {
        val deviceVibrator = vibrator ?: run {
            publishHapticSkip(reason = "no_vibrator_service")
            logHapticSkip("no_vibrator_service")
            return
        }
        if (!deviceVibrator.hasVibrator()) {
            publishHapticSkip(reason = "device_has_no_vibrator")
            logHapticSkip("device_has_no_vibrator")
            return
        }

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val safeAmplitude = if (deviceVibrator.hasAmplitudeControl()) {
                    amplitude
                } else {
                    logHapticDebug("amplitude_control_unavailable_using_default")
                    VibrationEffect.DEFAULT_AMPLITUDE
                }
                deviceVibrator.vibrate(VibrationEffect.createOneShot(durationMs, safeAmplitude))
            } else {
                @Suppress("DEPRECATION")
                deviceVibrator.vibrate(durationMs)
            }
        }.onFailure { error ->
            publishHapticSkip(reason = "vibrate_failed")
            Log.e(TAG, "[HAPTIC] vibrate_failed", error)
        }
    }

    private fun publishHapticsStatus(reason: String) {
        if (!BuildConfig.DEBUG) return
        hapticsDebugMonitor.update { previous ->
            previous.copy(
                event = HapticsDebugEvent.STATUS,
                reason = reason,
                enabled = audioReactiveHapticsEnabled,
                profile = audioReactiveHapticsProfile,
                routeToSystemDecoderDsp = routeToSystemDecoderDsp,
                isBitPerfectMode = isBitPerfectMode,
                isPlaying = player.isPlaying,
                smoothedScore = hapticScoreSmoothed,
                pulseCount = hapticPulseCount,
                updatedAtMs = System.currentTimeMillis()
            )
        }
    }

    private fun publishHapticSkip(
        reason: String,
        bass: Float = 0f,
        energy: Float = 0f,
        flux: Float = 0f,
        rawScore: Float = 0f,
        smoothedScore: Float = hapticScoreSmoothed,
        minScore: Float = 0f,
        cooldownRemainingMs: Long = 0L
    ) {
        if (!BuildConfig.DEBUG) return
        val nowMs = SystemClock.elapsedRealtime()
        val shouldPublish = reason != lastHapticSkipReason || (nowMs - lastHapticSkipPublishAtMs) > 250L
        if (!shouldPublish) return
        lastHapticSkipPublishAtMs = nowMs
        hapticsDebugMonitor.update {
            it.copy(
                event = HapticsDebugEvent.SKIP,
                reason = reason,
                enabled = audioReactiveHapticsEnabled,
                profile = audioReactiveHapticsProfile,
                routeToSystemDecoderDsp = routeToSystemDecoderDsp,
                isBitPerfectMode = isBitPerfectMode,
                isPlaying = player.isPlaying,
                rawScore = rawScore,
                smoothedScore = smoothedScore,
                minScore = minScore,
                cooldownRemainingMs = cooldownRemainingMs,
                bass = bass,
                energy = energy,
                flux = flux,
                amplitude = 0,
                durationMs = 0L,
                pulseCount = hapticPulseCount,
                updatedAtMs = System.currentTimeMillis()
            )
        }
    }

    private fun publishHapticFire(
        bass: Float,
        energy: Float,
        flux: Float,
        rawScore: Float,
        smoothedScore: Float,
        minScore: Float,
        amplitude: Int,
        durationMs: Long
    ) {
        if (!BuildConfig.DEBUG) return
        hapticsDebugMonitor.update {
            it.copy(
                event = HapticsDebugEvent.FIRE,
                reason = "pulse",
                enabled = audioReactiveHapticsEnabled,
                profile = audioReactiveHapticsProfile,
                routeToSystemDecoderDsp = routeToSystemDecoderDsp,
                isBitPerfectMode = isBitPerfectMode,
                isPlaying = player.isPlaying,
                rawScore = rawScore,
                smoothedScore = smoothedScore,
                minScore = minScore,
                cooldownRemainingMs = 0L,
                bass = bass,
                energy = energy,
                flux = flux,
                amplitude = amplitude,
                durationMs = durationMs,
                pulseCount = hapticPulseCount,
                updatedAtMs = System.currentTimeMillis()
            )
        }
    }

    private fun logHapticDebug(message: String) {
        if (!BuildConfig.DEBUG) return
        Log.d(TAG, "[HAPTIC] $message")
    }

    private fun logHapticSkip(reason: String) {
        if (!BuildConfig.DEBUG) return
        val nowMs = SystemClock.elapsedRealtime()
        val shouldLog = reason != lastHapticSkipReason || (nowMs - lastHapticSkipLogAtMs) > 1_200L
        if (shouldLog) {
            lastHapticSkipReason = reason
            lastHapticSkipLogAtMs = nowMs
            Log.d(TAG, "[HAPTIC] skip: $reason")
        }
    }

    fun loadAutoEqPreset(rawText: String, profileName: String) {
        currentProfile = AutoEqParser.parse(rawText, profileName)
        if (isBitPerfectMode) {
            Log.i(TAG, "Bit-perfect route enabled; storing PEQ profile without activating app DSP")
            return
        }
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
        serviceScope.cancel()
        super.onDestroy()
    }

    private inner class CustomMediaSessionCallback : MediaSession.Callback {
        @OptIn(UnstableApi::class)
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(CUSTOM_COMMAND_SHUFFLE, Bundle.EMPTY)).add(SessionCommand(CUSTOM_COMMAND_REPEAT, Bundle.EMPTY))
                .add(SessionCommand(ACTION_TOGGLE_DSP, Bundle.EMPTY))
                .add(SessionCommand(ACTION_LOAD_PEQ, Bundle.EMPTY))
                .add(SessionCommand(ACTION_SET_ACOUSTIC_ENV, Bundle.EMPTY))
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
                    serviceScope.launch {
                        settingsDataStore.setRouteToSystemDecoderDsp(!routeToSystemDecoderDsp)
                    }
                }
                ACTION_LOAD_PEQ -> {
                    val rawText = args.getString("peq_raw_text") ?: ""
                    val profileName = args.getString("peq_profile_name") ?: "Custom Nexus Shape"
                    loadAutoEqPreset(rawText, profileName)
                    invalidateCustomLayout()
                }
                ACTION_SET_ACOUSTIC_ENV -> {
                    val modeName = args.getString(EXTRA_ACOUSTIC_ENV_MODE) ?: AcousticEnvironmentMode.STUDIO.name
                    val mode = runCatching { AcousticEnvironmentMode.valueOf(modeName) }
                        .getOrDefault(AcousticEnvironmentMode.STUDIO)
                    adaptiveDspEngine.setAcousticEnvironmentMode(mode)
                    serviceScope.launch {
                        playbackPrefs.saveAcousticEnvironmentMode(mode.name)
                    }
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
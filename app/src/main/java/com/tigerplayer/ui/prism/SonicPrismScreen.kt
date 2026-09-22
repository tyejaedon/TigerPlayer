package com.tigerplayer.ui.prism

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tigerplayer.BuildConfig
import com.tigerplayer.data.local.PrismSpectralAnalysis
import com.tigerplayer.ui.theme.TigerCyberCyan
import com.tigerplayer.ui.theme.WitcherIcons
import com.tigerplayer.ui.theme.rememberTigerAmbientGradient

/**
 * Binds [PrismViewModel] to [SonicPrismScreen].
 *
 * The caller passes the `PrismViewModel` that `MainScreen` owns rather than resolving a new one
 * here, so this screen and the Home entry card stay on a single instance.
 */
@Composable
fun SonicPrismRoute(
    viewModel: PrismViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    SonicPrismScreen(
        state = state,
        onBackClick = onBackClick,
        onVocalsChange = viewModel::updateVocals,
        onBeatsChange = viewModel::updateBeats,
        onInstrumentsChange = viewModel::updateInstruments,
        onEnabledChange = viewModel::setPrismEnabled,
        onPresetSelected = viewModel::applyPreset,
        onResetRequested = viewModel::resetMixToBalanced,
        // The FFT/Bandpass A/B switch is a DSP profiling tool, not a listener-facing control.
        onSpectralAnalysisChange = if (BuildConfig.DEBUG) viewModel::setSpectralAnalysis else null,
        modifier = modifier
    )
}

/**
 * Full-screen Sonic Prism mixer.
 *
 * Replaces the expandable Home dashboard card, which had to shrink the faders to `180.dp` to fit a
 * feed item and competed with the feed's own scrolling for vertical drags (issue #173).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SonicPrismScreen(
    state: PrismUiState,
    onBackClick: () -> Unit,
    onVocalsChange: (Float) -> Unit,
    onBeatsChange: (Float) -> Unit,
    onInstrumentsChange: (Float) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onPresetSelected: (PrismPreset) -> Unit,
    onResetRequested: () -> Unit,
    onSpectralAnalysisChange: ((PrismSpectralAnalysis) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val ambientBrush = rememberTigerAmbientGradient(TigerCyberCyan, baseTopAlpha = 0.18f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag(PrismTestTags.SCREEN_ROOT)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ambientBrush)
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "SONIC PRISM",
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.testTag(PrismTestTags.SCREEN_BACK_BUTTON)
                        ) {
                            Icon(WitcherIcons.Back, contentDescription = "Back")
                        }
                    },
                    actions = {
                        Switch(
                            checked = state.isPrismEnabled,
                            onCheckedChange = onEnabledChange,
                            modifier = Modifier.testTag(PrismTestTags.ENABLE_SWITCH),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TigerCyberCyan,
                                checkedTrackColor = TigerCyberCyan.copy(alpha = 0.35f)
                            )
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        ) { padding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // The whole point of the migration: size the faders to the screen instead of
                // clamping them to what a dashboard card could spare.
                val faderHeight: Dp = (maxHeight * FADER_HEIGHT_FRACTION)
                    .coerceIn(MIN_FADER_HEIGHT, MAX_FADER_HEIGHT)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (state.isPrismEnabled) {
                            "Isolating vocals, beats, and melody in real time."
                        } else {
                            "Prism is bypassed. Enable it to isolate stems live."
                        },
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )

                    PrismInlineMixer(
                        state = state,
                        onVocalsChange = onVocalsChange,
                        onBeatsChange = onBeatsChange,
                        onInstrumentsChange = onInstrumentsChange,
                        // The enable switch lives in the app bar here; passing it again would put
                        // two switches carrying the same test tag on screen.
                        onEnabledChange = null,
                        onPresetSelected = onPresetSelected,
                        onResetRequested = onResetRequested,
                        onSpectralAnalysisChange = onSpectralAnalysisChange,
                        faderHeight = faderHeight,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag(PrismTestTags.SCREEN_MIXER)
                            .padding(bottom = 16.dp)
                    )
                }
            }
        }
    }
}

private const val FADER_HEIGHT_FRACTION = 0.46f
private val MIN_FADER_HEIGHT = 180.dp
private val MAX_FADER_HEIGHT = 360.dp

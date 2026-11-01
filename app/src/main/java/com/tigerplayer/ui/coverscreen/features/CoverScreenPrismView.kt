package com.tigerplayer.ui.coverscreen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tigerplayer.ui.prism.PrismViewModel
import com.tigerplayer.ui.prism.PrismFader
import com.tigerplayer.ui.theme.TigerCyberCyan
import com.tigerplayer.ui.theme.TigerToxicLime
import com.tigerplayer.ui.theme.TigerNeonOrange
import com.tigerplayer.ui.theme.glassEffect

@Composable
fun CoverScreenPrismView(
    prismViewModel: PrismViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by prismViewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(onClick = onClose)
            .testTag("CoverScreenPrismView")
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(8.dp)
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                .glassEffect(RoundedCornerShape(24.dp))
                .padding(bottom = 12.dp)
        ) {
            CompositionLocalProvider(LocalContentColor provides Color.White) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Sonic Prism",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Switch(
                        checked = state.isPrismEnabled,
                        onCheckedChange = { prismViewModel.setPrismEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TigerCyberCyan,
                            checkedTrackColor = TigerCyberCyan.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.scale(0.8f)
                    )

                    IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close Prism", tint = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Box(modifier = Modifier.width(60.dp)) {
                        PrismFader(
                            label = "VOCALS",
                            value = state.vocals,
                            onValueChange = prismViewModel::updateVocals,
                            activeColor = TigerCyberCyan,
                            faderHeight = 160.dp
                        )
                    }

                    Box(modifier = Modifier.width(60.dp)) {
                        PrismFader(
                            label = "BEATS",
                            value = state.beats,
                            onValueChange = prismViewModel::updateBeats,
                            activeColor = TigerNeonOrange,
                            faderHeight = 160.dp
                        )
                    }

                    Box(modifier = Modifier.width(60.dp)) {
                        PrismFader(
                            label = "INST",
                            value = state.instruments,
                            onValueChange = prismViewModel::updateInstruments,
                            activeColor = TigerToxicLime,
                            faderHeight = 160.dp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            }
        }
    }
}

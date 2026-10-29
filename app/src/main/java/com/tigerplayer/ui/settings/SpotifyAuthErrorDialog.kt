package com.tigerplayer.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tigerplayer.data.repository.SpotifyAuthError
import com.tigerplayer.ui.theme.SpotifyGreen

/**
 * Intuitive pop-up dialog presenting Spotify connection and authentication errors.
 * Replaces fleeting toasts with user-understandable diagnostics, repo setup steps,
 * and an actionable dismissal/retry workflow in both regular and Cover Screen modes.
 */
@Composable
fun SpotifyAuthErrorDialog(
    error: SpotifyAuthError,
    onDismiss: () -> Unit,
    onRetry: (() -> Unit)? = null,
    isCoverScreen: Boolean = false,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = true, onBack = onDismiss)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        val scrollState = rememberScrollState()

        val cardModifier = if (isCoverScreen) {
            Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.94f)
                .clip(RoundedCornerShape(16.dp))
        } else {
            Modifier
                .fillMaxWidth(0.90f)
                .widthIn(max = 520.dp)
                .wrapContentHeight()
                .clip(RoundedCornerShape(22.dp))
        }

        Surface(
            modifier = modifier.then(cardModifier),
            shape = RoundedCornerShape(if (isCoverScreen) 16.dp else 22.dp),
            color = Color(0xFF10131A),
            tonalElevation = 8.dp,
            border = BorderStroke(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        SpotifyGreen.copy(alpha = 0.5f),
                        Color.White.copy(alpha = 0.12f),
                        SpotifyGreen.copy(alpha = 0.2f)
                    )
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(if (isCoverScreen) 12.dp else 20.dp)
            ) {
                // --- HEADER ---
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isCoverScreen) 34.dp else 42.dp)
                            .clip(CircleShape)
                            .background(SpotifyGreen.copy(alpha = 0.18f))
                            .border(1.dp, SpotifyGreen.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CloudOff,
                            contentDescription = null,
                            tint = SpotifyGreen,
                            modifier = Modifier.size(if (isCoverScreen) 20.dp else 24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(if (isCoverScreen) 10.dp else 14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SPOTIFY CONNECTION",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            fontSize = if (isCoverScreen) 9.sp else 11.sp,
                            color = SpotifyGreen,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = error.title,
                            style = if (isCoverScreen) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(if (isCoverScreen) 10.dp else 16.dp))

                // --- SCROLLABLE BODY ---
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState)
                ) {
                    // Friendly description
                    Text(
                        text = error.description,
                        style = if (isCoverScreen) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.88f),
                        lineHeight = if (isCoverScreen) 16.sp else 22.sp
                    )

                    // Setup instructions / How to resolve
                    if (error.setupSteps.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(if (isCoverScreen) 10.dp else 16.dp))

                        Text(
                            text = "HOW TO RESOLVE:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            fontSize = if (isCoverScreen) 9.sp else 11.sp,
                            color = Color(0xFF64B5F6),
                            letterSpacing = 0.8.sp
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        error.setupSteps.forEachIndexed { index, step ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = if (isCoverScreen) 3.dp else 4.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(if (isCoverScreen) 18.dp else 22.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        fontSize = if (isCoverScreen) 10.sp else 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = SpotifyGreen
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = step,
                                    style = if (isCoverScreen) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                                    fontSize = if (isCoverScreen) 11.sp else 13.sp,
                                    color = Color.White.copy(alpha = 0.82f),
                                    lineHeight = if (isCoverScreen) 15.sp else 19.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Raw technical error code badge
                    if (!error.rawErrorCode.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(if (isCoverScreen) 8.dp else 14.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0A0D12))
                                .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Code: ${error.rawErrorCode}",
                                fontFamily = FontFamily.Monospace,
                                fontSize = if (isCoverScreen) 9.sp else 11.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(if (isCoverScreen) 10.dp else 18.dp))

                // --- ACTION BUTTONS ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onRetry != null) {
                        OutlinedButton(
                            onClick = onRetry,
                            modifier = Modifier
                                .weight(1f)
                                .defaultMinSize(minHeight = 48.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, SpotifyGreen.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SpotifyGreen),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "RETRY",
                                fontWeight = FontWeight.Black,
                                fontSize = if (isCoverScreen) 11.sp else 12.sp
                            )
                        }
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SpotifyGreen,
                            contentColor = Color.Black
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "DISMISS",
                            fontWeight = FontWeight.Black,
                            fontSize = if (isCoverScreen) 11.sp else 12.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}

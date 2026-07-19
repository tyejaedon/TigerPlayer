package com.example.tigerplayer.ui.permissions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.tigerplayer.ui.theme.WitcherIcons
import com.example.tigerplayer.ui.theme.bounceClick

private data class PermissionRequirement(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val permissions: List<String>
)

/**
 * THE SYSTEM OVERRIDE
 * A dedicated tactical screen for requesting OS-level permissions.
 * Now encapsulated using Compose-native activity result launchers.
 */
@Composable
fun PermissionScreen(onPermissionGranted: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    // 1. THE PERMISSION ARRAY (OS VERSION SAFE)
    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val runtimePermissions = mutableListOf(
        audioPermission,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
        }
    }

    val missingPermissions = runtimePermissions.filter { permission ->
        ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
    }

    val permissionRequirements = buildList {
        add(
            PermissionRequirement(
                icon = WitcherIcons.Library,
                title = "LOCAL ARCHIVES",
                description = "Required to scan and play high-fidelity FLAC and MP3 files.",
                permissions = listOf(audioPermission)
            )
        )
        add(
            PermissionRequirement(
                icon = Icons.Rounded.LocationOn,
                title = "ATMOSPHERIC INTEL",
                description = "Required to sync live weather and wind data to your location.",
                permissions = listOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(
                PermissionRequirement(
                    icon = Icons.Rounded.Notifications,
                    title = "MISSION ALERTS",
                    description = "Needed for playback notifications and lock-screen controls.",
                    permissions = listOf(Manifest.permission.POST_NOTIFICATIONS)
                )
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(
                PermissionRequirement(
                    icon = Icons.Rounded.Bluetooth,
                    title = "WIRELESS LINK",
                    description = "Needed to detect and manage Bluetooth playback devices.",
                    permissions = listOf(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                    )
                )
            )
        }
    }

    // 2. THE MULTI-LAUNCHER
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val isAudioGranted = permissions[audioPermission] == true ||
            ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
        val isBluetoothGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val connectGranted = permissions[Manifest.permission.BLUETOOTH_CONNECT] == true ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            val scanGranted = permissions[Manifest.permission.BLUETOOTH_SCAN] == true ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            connectGranted && scanGranted
        } else {
            true
        }

        if (isAudioGranted && isBluetoothGranted) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onPermissionGranted()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon inside a Medallion-gold circle
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f), MaterialTheme.shapes.medium)
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = WitcherIcons.Library,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "SYSTEM OVERRIDE",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "To initialize the audio engine and sync the atmospheric intel, TigerPlayer requires localized access.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        // --- THE TACTICAL BRIEFING ---
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            permissionRequirements.forEachIndexed { index, requirement ->
                val isGranted = requirement.permissions.all { permission ->
                    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
                }
                PermissionRequirementRow(
                    icon = requirement.icon,
                    title = requirement.title,
                    description = requirement.description,
                    isGranted = isGranted
                )
                if (index != permissionRequirements.lastIndex) {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // FORGED STEEL BUTTON
        Button(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                val hasAllCriticalPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val btConnectGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                    val btScanGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                    val audioGranted = ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
                    audioGranted && btConnectGranted && btScanGranted
                } else {
                    ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED
                }

                if (missingPermissions.isEmpty() && hasAllCriticalPermissions) {
                    onPermissionGranted()
                } else {
                    permissionLauncher.launch(missingPermissions.toTypedArray())
                }
            },
            shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .bounceClick { }
        ) {
            Text(
                text = if (missingPermissions.isEmpty()) "CONTINUE" else "GRANT ACCESS",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PermissionRequirementRow(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean
) {
    val statusText = if (isGranted) "Granted" else "Missing"
    val statusContainerColor = if (isGranted) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val statusContentColor = if (isGranted) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp).padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = statusContainerColor,
                    contentColor = statusContentColor,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                lineHeight = 20.sp
            )
        }
    }
}
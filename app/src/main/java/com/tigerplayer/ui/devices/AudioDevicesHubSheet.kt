package com.tigerplayer.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.tigerplayer.data.model.AudioDeviceType
import com.tigerplayer.data.model.AudioOutputDevice
import com.tigerplayer.ui.theme.TigerCyberCyan
import com.tigerplayer.ui.theme.TigerNeonOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioDevicesHubSheet(
    viewModel: AudioDevicesViewModel = hiltViewModel(),
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Audio Devices",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Routing, AutoEq tuning, and device presets",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Active Route Hero Card
                item {
                    ActiveRouteHeroCard(
                        activeDevice = uiState.activeDevice,
                        assignedPreset = uiState.activePreset,
                        onOpenAutoEq = { viewModel.openAutoEqDialog(uiState.activeDevice) }
                    )
                }

                // Smart Settings Section
                item {
                    SmartDeviceSettingsCard(
                        autoApplyEnabled = uiState.autoApplyEnabled,
                        autoDetectEnabled = uiState.autoDetectEnabled,
                        preferredTarget = uiState.preferredTargetCurve,
                        targetCurveOptions = uiState.targetCurveOptions,
                        onAutoApplyToggled = viewModel::onAutoApplyToggled,
                        onAutoDetectToggled = viewModel::onAutoDetectToggled,
                        onTargetSelected = viewModel::onPreferredTargetCurveSelected
                    )
                }

                // Wired Profiles Section Header & Action
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Known Devices & History",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        TextButton(
                            onClick = { viewModel.openCreateWiredDialog() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = TigerCyberCyan
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Add Wired Profile",
                                color = TigerCyberCyan,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Device Items
                items(uiState.knownDevices, key = { it.deviceId }) { device ->
                    DeviceHistoryItem(
                        device = device,
                        isActive = device.deviceId == uiState.activeDevice.deviceId,
                        onOpenAutoEq = { viewModel.openAutoEqDialog(device) },
                        onDelete = { viewModel.deleteDevice(device.deviceId) }
                    )
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }

    // AutoEq Headphone Selection Dialog
    if (uiState.showAutoEqDialog) {
        AutoEqHeadphoneSelectorDialog(
            targetDevice = uiState.selectedDeviceForAutoEq,
            searchQuery = uiState.searchQuery,
            searchResults = uiState.searchResults,
            isSearching = uiState.isSearching,
            onSearchQueryChanged = viewModel::onSearchQueryChanged,
            onModelSelected = viewModel::selectAutoEqModel,
            onDismiss = viewModel::closeAutoEqDialog
        )
    }

    // Create Wired Profile Dialog
    if (uiState.showCreateWiredDialog) {
        CreateWiredProfileDialog(
            onConfirm = { name, form ->
                viewModel.createNamedWiredProfile(name, formFactor = form)
            },
            onDismiss = viewModel::closeCreateWiredDialog
        )
    }
}

@Composable
private fun ActiveRouteHeroCard(
    activeDevice: AudioOutputDevice,
    assignedPreset: com.tigerplayer.data.local.entity.DevicePresetEntity?,
    onOpenAutoEq: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, TigerCyberCyan.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(TigerCyberCyan.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val icon = when (activeDevice.type) {
                        AudioDeviceType.PHONE_SPEAKER -> Icons.Default.Speaker
                        AudioDeviceType.WIRED_HEADPHONES -> Icons.Default.Headphones
                        else -> Icons.Default.Headphones
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = TigerCyberCyan,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(TigerCyberCyan, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ACTIVE OUTPUT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = TigerCyberCyan,
                            fontSize = 10.sp
                        )
                    }

                    Text(
                        text = activeDevice.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (activeDevice.batteryLevel >= 0) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "${activeDevice.batteryLevel}%",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (activeDevice.batteryLevel < 20) TigerNeonOrange else TigerCyberCyan
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // AutoEq Status & Action
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenAutoEq),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Equalizer,
                            contentDescription = null,
                            tint = TigerCyberCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            val curveName = activeDevice.matchedAutoEqModel ?: assignedPreset?.name ?: "No EQ Bound"
                            Text(
                                text = "Profile: $curveName",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (activeDevice.targetCurve != null) {
                                Text(
                                    text = "${activeDevice.targetCurve} • ${activeDevice.formFactor ?: "Generic"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    Text(
                        text = "CHANGE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = TigerCyberCyan
                    )
                }
            }
        }
    }
}

@Composable
private fun SmartDeviceSettingsCard(
    autoApplyEnabled: Boolean,
    autoDetectEnabled: Boolean,
    preferredTarget: String,
    targetCurveOptions: List<String>,
    onAutoApplyToggled: (Boolean) -> Unit,
    onAutoDetectToggled: (Boolean) -> Unit,
    onTargetSelected: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Preferences",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Auto-Apply Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-Apply Device EQ",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Automatically load per-device EQ curve on connection",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoApplyEnabled,
                    onCheckedChange = onAutoApplyToggled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TigerCyberCyan,
                        checkedTrackColor = TigerCyberCyan.copy(alpha = 0.3f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Auto-Detect Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-Detect Headphone Models",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Match connected headphones with AutoEq database automatically",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoDetectEnabled,
                    onCheckedChange = onAutoDetectToggled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TigerCyberCyan,
                        checkedTrackColor = TigerCyberCyan.copy(alpha = 0.3f)
                    )
                )
            }
        }
    }
}

@Composable
private fun DeviceHistoryItem(
    device: AudioOutputDevice,
    isActive: Boolean,
    onOpenAutoEq: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                1.dp,
                if (isActive) TigerCyberCyan.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                RoundedCornerShape(14.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isActive) 0.5f else 0.25f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = device.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isActive) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = CircleShape,
                            color = TigerCyberCyan.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "ACTIVE",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = TigerCyberCyan,
                                fontSize = 9.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                val subtitle = device.matchedAutoEqModel?.let { model ->
                    "$model • ${device.targetCurve ?: "Harman"}"
                } ?: "${device.type.name} • ${device.codec}"

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenAutoEq) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search AutoEq Model",
                        tint = TigerCyberCyan
                    )
                }

                // Delete only if not canonical built-in speaker
                if (device.deviceId != AudioOutputDevice.DEVICE_ID_SPEAKER && device.deviceId != AudioOutputDevice.DEVICE_ID_WIRED) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

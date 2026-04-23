package com.example.tigerplayer.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.tigerplayer.service.EqMode
import com.example.tigerplayer.ui.theme.glassEffect

// --- Thematic Colors ---
private val IgniRed = Color(0xFFF11F1A)
private val AardBlue = Color(0xFF4FC3F7)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val themeMode by viewModel.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val cacheSize by viewModel.cacheSizeFormatted.collectAsState()
    var cacheClearedMessage by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "ARCHIVE PREFERENCES",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp // THE FIX: sp for text
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // --- SECTION: APPEARANCE ---
            SettingsSection(
                title = "THE LOOK",
                icon = Icons.Rounded.ColorLens
            ) {
                ThemePreferenceSelector(
                    currentMode = themeMode,
                    onModeSelected = { viewModel.setThemeMode(it) }
                )
            }
            SettingsSection(
                title = "ACOUSTIC RESONANCE",
                icon = Icons.Rounded.Audiotrack // You might need to import this
            ) {
                val currentEq by viewModel.currentEqMode.collectAsState(initial = EqMode.BALANCE)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassEffect(shape = MaterialTheme.shapes.large)
                        .padding(20.dp)
                ) {
                    Text("Environmental Profiles", fontWeight = FontWeight.Bold)
                    Text("Calibrate the output for your SpaceBuds Z",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EqOption("BALANCE", currentEq == EqMode.BALANCE, { viewModel.setEqMode(EqMode.BALANCE) }, Modifier.weight(1f))
                        EqOption("TRANSPARENT", currentEq == EqMode.TRANSPARENCY, { viewModel.setEqMode(EqMode.TRANSPARENCY) }, Modifier.weight(1f))
                        EqOption("ISOLATION", currentEq == EqMode.ISOLATION, { viewModel.setEqMode(EqMode.ISOLATION) }, Modifier.weight(1f))
                    }
                }
            }

            // --- SECTION: REMOTE SERVICES ---
            SettingsSection(
                title = "CONTRACTS",
                icon = Icons.Rounded.CloudOff
            ) {
                PreferenceRow(
                    title = "Spotify Connection",
                    subtitle = "Sever the link with the cloud oracle",
                    action = {
                        TextButton(onClick = { viewModel.logoutSpotify() }) {
                            Text("LOGOUT", color = IgniRed, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            // --- SECTION: LOCAL STORAGE ---
            SettingsSection(
                title = "STORAGE",
                icon = Icons.Rounded.DeleteSweep
            ) {
                PreferenceRow(
                    title = "Purge Temporary Archives",
                    subtitle = cacheClearedMessage ?: "Currently utilizing $cacheSize of space",
                    action = {
                        OutlinedButton(
                            onClick = {
                                viewModel.clearTotalCache {
                                    cacheClearedMessage = "Archives purged successfully."
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("PURGE", fontWeight = FontWeight.Black)
                        }
                    }
                )
            }

            // --- SECTION: THE CREATOR ---
            SettingsSection(
                title = "THE ARCHIVIST",
                icon = Icons.Rounded.Code
            ) {
                AboutMeSection()
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
@Composable
fun EqOption(label: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) AardBlue.copy(alpha = 0.2f) else Color.Transparent,
        border = BorderStroke(1.dp, if (isSelected) AardBlue else Color.Gray.copy(alpha = 0.3f)),
        modifier = modifier.height(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.Black, color = if (isSelected) AardBlue else Color.Gray)
        }
    }
}
@Composable
fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
        }
        content()
    }
}

@Composable
fun AboutMeSection() {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassEffect(shape = MaterialTheme.shapes.extraLarge)
            .padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text("TY", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = "Jaedon",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Forged in Nairobi, Kenya",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Strathmore University student, Android alchemist, and high-fidelity audio archivist. Passionate about crafting clean architectures, exploring cybersecurity, and ensuring every FLAC track plays with absolute precision.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            SocialLink(label = "GitHub", onClick = { uriHandler.openUri("https://github.com/tyejaedon") })
            SocialLink(label = "LinkedIn", onClick = { uriHandler.openUri("https://linkedin.com/in/tyejaedon") })
            SocialLink(label = "Instagram", onClick = { uriHandler.openUri("https://instagram.com/tyjaedon") })
        }
    }
}

@Composable
fun SocialLink(label: String, onClick: () -> Unit) {
    Surface(
        color = Color.Transparent,
        shape = CircleShape,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label.uppercase(),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun ThemePreferenceSelector(
    currentMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassEffect(shape = MaterialTheme.shapes.large)
            .padding(20.dp)
    ) {
        Text(
            "Visual Resonance",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Dictate the application's ambient lighting",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeOption(
                label = "Light",
                isSelected = currentMode == ThemeMode.LIGHT,
                onClick = { onModeSelected(ThemeMode.LIGHT) },
                modifier = Modifier.weight(1f)
            )
            ThemeOption(
                label = "Dark",
                isSelected = currentMode == ThemeMode.DARK,
                onClick = { onModeSelected(ThemeMode.DARK) },
                modifier = Modifier.weight(1f)
            )
            ThemeOption(
                label = "System",
                isSelected = currentMode == ThemeMode.SYSTEM,
                onClick = { onModeSelected(ThemeMode.SYSTEM) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun ThemeOption(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = modifier.height(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold
            )
        }
    }
}

@Composable
fun PreferenceRow(title: String, subtitle: String, action: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassEffect(shape = MaterialTheme.shapes.large)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // THE FIX: "end" instead of "right" for RTL support
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
        }
        action()
    }
}
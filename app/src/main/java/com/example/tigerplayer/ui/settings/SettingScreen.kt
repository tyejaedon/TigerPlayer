package com.example.tigerplayer.ui.settings

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.example.tigerplayer.ui.equalizer.AuralNexusScreen
import com.example.tigerplayer.ui.equalizer.AuralNexusViewModel
import com.example.tigerplayer.ui.theme.glassEffect
import com.example.tigerplayer.ui.theme.bounceClick

// --- Thematic Colors ---
private val IgniRed = Color(0xFFF11F1A)
private val AardBlue = Color(0xFF4FC3F7)
private val BitPerfectGold = Color(0xFFFFD700)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val themeMode by viewModel.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
    val cacheSize by viewModel.cacheSizeFormatted.collectAsState()

    var cacheClearedMessage by remember { mutableStateOf<String?>(null) }
    var showAuralNexusScreen by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = "ARCHIVE PREFERENCES",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
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
                SettingsSection(title = "THE LOOK", icon = Icons.Rounded.ColorLens) {
                    ThemePreferenceSelector(
                        currentMode = themeMode,
                        onModeSelected = { viewModel.setThemeMode(it) }
                    )
                }

                // --- SECTION: ACOUSTIC RESONANCE (UPGRADED) ---
                SettingsSection(title = "ACOUSTIC RESONANCE", icon = Icons.Rounded.Audiotrack) {
                    val isBp by viewModel.isBitPerfect.collectAsState()
                    val primaryColor = if (isBp) BitPerfectGold else AardBlue

                    // The Gateway Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .background(Brush.linearGradient(listOf(primaryColor.copy(alpha = 0.1f), Color.Transparent)))
                            .border(1.dp, primaryColor.copy(alpha = 0.2f), MaterialTheme.shapes.large)
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                Text(
                                    text = "AUDIO ENGINE",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black,
                                    color = primaryColor
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isBp) "Bit-Perfect Output Active\nBypassing Android Mixer"
                                    else "Aural Nexus Active\nSpatial DSP Engine",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )
                            }

                            // Dynamic UI Swap: Switch vs Switch + Tune Button
                            Switch(
                                checked = isBp,
                                onCheckedChange = { viewModel.toggleBitPerfect() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.Black,
                                    checkedTrackColor = BitPerfectGold,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = Color.White.copy(alpha = 0.2f)
                                )
                            )

                            // The Tune Button reveals itself smoothly when DSP is active
                            AnimatedVisibility(visible = !isBp) {
                                Row {
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(primaryColor.copy(alpha = 0.15f))
                                            .bounceClick { showAuralNexusScreen = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Rounded.Tune, contentDescription = "Tune", tint = primaryColor)
                                    }
                                }
                            }
                        }
                    }
                }

                // --- SECTION: REMOTE SERVICES ---
                SettingsSection(title = "CONTRACTS", icon = Icons.Rounded.CloudOff) {
                    PreferenceRow(
                        title = "Spotify Connection",
                        subtitle = "Sever the link with the cloud oracle",
                        action = {
                            TextButton(onClick = { viewModel.logoutSpotify() }) {
                                Text("LOGOUT", color = IgniRed, fontWeight = FontWeight.Black)
                            }
                        }
                    )
                }

                // --- SECTION: LOCAL STORAGE ---
                SettingsSection(title = "STORAGE", icon = Icons.Rounded.DeleteSweep) {
                    PreferenceRow(
                        title = "Purge Temporary Archives",
                        subtitle = cacheClearedMessage ?: "Currently utilizing $cacheSize of space",
                        action = {
                            OutlinedButton(
                                onClick = { viewModel.clearTotalCache { cacheClearedMessage = "Archives purged successfully." } },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("PURGE", fontWeight = FontWeight.Black)
                            }
                        }
                    )
                }

                // --- SECTION: THE CREATOR ---
                SettingsSection(title = "THE ARCHIVIST", icon = Icons.Rounded.Code) {
                    AboutMeSection()
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        // --- FULL SCREEN OVERLAY: AURAL NEXUS ---
        AnimatedVisibility(
            visible = showAuralNexusScreen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            val auralNexusViewModel: AuralNexusViewModel = hiltViewModel()
            AuralNexusScreen(
                viewModel = auralNexusViewModel,
                onClose = { showAuralNexusScreen = false }
            )
        }
    }
}

// ==========================================
// --- REUSABLE COMPONENTS ---
// ==========================================

@Composable
fun SettingsSection(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }
        content()
    }
}

@Composable
fun ThemePreferenceSelector(currentMode: ThemeMode, onModeSelected: (ThemeMode) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassEffect(shape = MaterialTheme.shapes.large)
            .padding(20.dp)
    ) {
        Text("Visual Resonance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("Dictate the application's ambient lighting", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeOption("Light", currentMode == ThemeMode.LIGHT, { onModeSelected(ThemeMode.LIGHT) }, Modifier.weight(1f))
            ThemeOption("Dark", currentMode == ThemeMode.DARK, { onModeSelected(ThemeMode.DARK) }, Modifier.weight(1f))
            ThemeOption("System", currentMode == ThemeMode.SYSTEM, { onModeSelected(ThemeMode.SYSTEM) }, Modifier.weight(1f))
        }
    }
}

@Composable
fun ThemeOption(label: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = modifier.height(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold)
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
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
        }
        action()
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
                modifier = Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text("TY", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("Jaedon", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text("Forged in Nairobi, Kenya", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Strathmore University student, Android alchemist, and high-fidelity audio archivist. Passionate about crafting clean architectures, exploring cybersecurity, and ensuring every FLAC track plays with absolute precision.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 22.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            SocialLink("GitHub") { uriHandler.openUri("https://github.com/tyejaedon") }
            SocialLink("LinkedIn") { uriHandler.openUri("https://linkedin.com/in/tyejaedon") }
            SocialLink("Instagram") { uriHandler.openUri("https://instagram.com/tyjaedon") }
        }
    }
}

@Composable
fun SocialLink(label: String, onClick: () -> Unit) {
    Surface(color = Color.Transparent, shape = CircleShape, modifier = Modifier.clickable { onClick() }) {
        Text(label.uppercase(), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}
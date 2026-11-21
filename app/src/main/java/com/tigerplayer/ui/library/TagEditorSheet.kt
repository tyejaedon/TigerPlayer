package com.tigerplayer.ui.library

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tigerplayer.data.model.ArtworkEditAction
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.ui.theme.TigerPlayerTheme
import com.tigerplayer.ui.theme.aardBlue
import com.tigerplayer.ui.theme.igniRed
import java.io.ByteArrayOutputStream

object TagEditorTestTags {
    const val SHEET = "tag_editor_sheet"
    const val SAVE_BUTTON = "tag_editor_save_button"
    const val CONFIRM_DIALOG = "tag_editor_confirm_dialog"
    const val ERROR_BANNER = "tag_editor_error_banner"
    const val BACKUP_SWITCH = "tag_editor_backup_switch"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagEditorSheet(
    tracks: List<AudioTrack>,
    isCoverScreen: Boolean = false,
    onDismiss: () -> Unit,
    onSaved: () -> Unit = {},
    viewModel: TagEditorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(tracks) {
        viewModel.openForTracks(tracks)
    }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            onSaved()
            viewModel.reset()
            onDismiss()
        }
    }

    val writePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onWritePermissionResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(uiState.pendingIntentSender) {
        val sender = uiState.pendingIntentSender ?: return@LaunchedEffect
        writePermissionLauncher.launch(IntentSenderRequest.Builder(sender).build())
    }

    val artworkPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { stream ->
                val raw = stream.readBytes()
                normalizeArtworkBytes(raw)
            }
            if (bytes != null) {
                viewModel.replaceArtwork(bytes, "image/jpeg")
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (!uiState.isSaving) {
                viewModel.reset()
                onDismiss()
            }
        },
        modifier = Modifier.testTag(TagEditorTestTags.SHEET)
    ) {
        TagEditorSheetContent(
            uiState = uiState,
            isCoverScreen = isCoverScreen,
            onFieldChange = viewModel::updateField,
            onToggleField = viewModel::toggleFieldEnabled,
            onPickArtwork = {
                artworkPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onRemoveArtwork = viewModel::removeArtwork,
            onKeepArtwork = viewModel::keepArtwork,
            onToggleBackup = viewModel::setCreateBackup,
            onRequestSave = viewModel::requestSave,
            onDismiss = {
                viewModel.reset()
                onDismiss()
            },
            onDismissError = viewModel::dismissError
        )
    }

    if (uiState.showConfirmDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissConfirmDialog,
            modifier = Modifier.testTag(TagEditorTestTags.CONFIRM_DIALOG),
            title = { Text("Save Tag Changes?") },
            text = {
                val count = uiState.tracks.size
                val backupText = if (uiState.createBackup) {
                    " Original file(s) will be backed up first."
                } else {
                    " Backup is disabled."
                }
                Text(
                    "This will modify metadata on $count local audio file${if (count == 1) "" else "s"} on disk.$backupText"
                )
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmAndSave,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.aardBlue)
                ) {
                    Text("Write Tags")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissConfirmDialog) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
internal fun TagEditorSheetContent(
    uiState: TagEditorUiState,
    isCoverScreen: Boolean,
    onFieldChange: (TagField, String) -> Unit,
    onToggleField: (TagField) -> Unit,
    onPickArtwork: () -> Unit,
    onRemoveArtwork: () -> Unit,
    onKeepArtwork: () -> Unit,
    onToggleBackup: (Boolean) -> Unit,
    onRequestSave: () -> Unit,
    onDismiss: () -> Unit,
    onDismissError: () -> Unit
) {
    val padding = if (isCoverScreen) 12.dp else 20.dp
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = padding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(if (isCoverScreen) 8.dp else 12.dp)
    ) {
        Text(
            text = if (uiState.isBatchMode) {
                "Batch Edit Tags (${uiState.tracks.size} tracks)"
            } else {
                "Edit Tags"
            },
            style = if (isCoverScreen) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        if (uiState.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        uiState.errorMessage?.let { message ->
            Surface(
                color = MaterialTheme.igniRed.copy(alpha = 0.14f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TagEditorTestTags.ERROR_BANNER)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.igniRed)
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onDismissError) {
                        Text("Dismiss")
                    }
                }
            }
        }

        ArtworkSection(
            artworkBytes = uiState.currentArtworkBytes,
            artworkAction = uiState.artworkAction,
            isBatchMode = uiState.isBatchMode,
            isCoverScreen = isCoverScreen,
            onPickArtwork = onPickArtwork,
            onRemoveArtwork = onRemoveArtwork,
            onKeepArtwork = onKeepArtwork
        )

        TagTextField(
            label = "Title",
            value = uiState.title,
            field = TagField.TITLE,
            isBatchMode = uiState.isBatchMode,
            enabled = TagField.TITLE in uiState.enabledFields,
            onValueChange = { onFieldChange(TagField.TITLE, it) },
            onToggle = { onToggleField(TagField.TITLE) }
        )

        TagTextField(
            label = "Artist",
            value = uiState.artist,
            field = TagField.ARTIST,
            isBatchMode = uiState.isBatchMode,
            enabled = TagField.ARTIST in uiState.enabledFields,
            onValueChange = { onFieldChange(TagField.ARTIST, it) },
            onToggle = { onToggleField(TagField.ARTIST) }
        )

        TagTextField(
            label = "Album",
            value = uiState.album,
            field = TagField.ALBUM,
            isBatchMode = uiState.isBatchMode,
            enabled = TagField.ALBUM in uiState.enabledFields,
            onValueChange = { onFieldChange(TagField.ALBUM, it) },
            onToggle = { onToggleField(TagField.ALBUM) }
        )

        TagTextField(
            label = "Album Artist",
            value = uiState.albumArtist,
            field = TagField.ALBUM_ARTIST,
            isBatchMode = uiState.isBatchMode,
            enabled = TagField.ALBUM_ARTIST in uiState.enabledFields,
            onValueChange = { onFieldChange(TagField.ALBUM_ARTIST, it) },
            onToggle = { onToggleField(TagField.ALBUM_ARTIST) }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TagTextField(
                label = "Track #",
                value = uiState.trackNumber,
                field = TagField.TRACK_NUMBER,
                isBatchMode = uiState.isBatchMode,
                enabled = TagField.TRACK_NUMBER in uiState.enabledFields,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
                onValueChange = { onFieldChange(TagField.TRACK_NUMBER, it) },
                onToggle = { onToggleField(TagField.TRACK_NUMBER) }
            )
            TagTextField(
                label = "Disc #",
                value = uiState.discNumber,
                field = TagField.DISC_NUMBER,
                isBatchMode = uiState.isBatchMode,
                enabled = TagField.DISC_NUMBER in uiState.enabledFields,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
                onValueChange = { onFieldChange(TagField.DISC_NUMBER, it) },
                onToggle = { onToggleField(TagField.DISC_NUMBER) }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TagTextField(
                label = "Year",
                value = uiState.year,
                field = TagField.YEAR,
                isBatchMode = uiState.isBatchMode,
                enabled = TagField.YEAR in uiState.enabledFields,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f),
                onValueChange = { onFieldChange(TagField.YEAR, it) },
                onToggle = { onToggleField(TagField.YEAR) }
            )
            TagTextField(
                label = "Genre",
                value = uiState.genre,
                field = TagField.GENRE,
                isBatchMode = uiState.isBatchMode,
                enabled = TagField.GENRE in uiState.enabledFields,
                modifier = Modifier.weight(1f),
                onValueChange = { onFieldChange(TagField.GENRE, it) },
                onToggle = { onToggleField(TagField.GENRE) }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleBackup(!uiState.createBackup) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Backup original file",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Saves a copy in app storage before writing",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Switch(
                checked = uiState.createBackup,
                onCheckedChange = onToggleBackup,
                modifier = Modifier.testTag(TagEditorTestTags.BACKUP_SWITCH)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !uiState.isSaving,
                modifier = Modifier.weight(1f)
            ) {
                Text("Cancel")
            }
            Button(
                onClick = onRequestSave,
                enabled = !uiState.isSaving && !uiState.isLoading && uiState.tracks.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.aardBlue),
                modifier = Modifier
                    .weight(1f)
                    .testTag(TagEditorTestTags.SAVE_BUTTON)
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text("Save Tags")
                }
            }
        }
    }
}

@Composable
private fun ArtworkSection(
    artworkBytes: ByteArray?,
    artworkAction: ArtworkEditAction,
    isBatchMode: Boolean,
    isCoverScreen: Boolean,
    onPickArtwork: () -> Unit,
    onRemoveArtwork: () -> Unit,
    onKeepArtwork: () -> Unit
) {
    val artSize = if (isCoverScreen) 56.dp else 72.dp
    val bitmap = remember(artworkBytes) {
        artworkBytes?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(artSize)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Embedded artwork",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Album,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onPickArtwork,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Replace Art", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onRemoveArtwork,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Remove", style = MaterialTheme.typography.labelMedium)
                }
            }
            if (isBatchMode && artworkAction != ArtworkEditAction.Keep) {
                TextButton(
                    onClick = onKeepArtwork,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Keep existing per track", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun TagTextField(
    label: String,
    value: String,
    @Suppress("UNUSED_PARAMETER") field: TagField,
    isBatchMode: Boolean,
    enabled: Boolean,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
    onToggle: () -> Unit
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isBatchMode) {
            Checkbox(
                checked = enabled,
                onCheckedChange = { onToggle() }
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            placeholder = if (isBatchMode && !enabled) {
                { Text("Keep existing") }
            } else null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun normalizeArtworkBytes(raw: ByteArray): ByteArray {
    val decoded = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return raw
    val maxDim = 1000
    val scaled = if (decoded.width > maxDim || decoded.height > maxDim) {
        val ratio = minOf(maxDim.toFloat() / decoded.width, maxDim.toFloat() / decoded.height)
        Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * ratio).toInt().coerceAtLeast(1),
            (decoded.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    } else {
        decoded
    }
    return ByteArrayOutputStream().use { out ->
        scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
        out.toByteArray()
    }
}

@Preview(showBackground = true)
@Composable
private fun TagEditorSheetPreview() {
    TigerPlayerTheme {
        Surface {
            TagEditorSheetContent(
                uiState = TagEditorUiState(
                    tracks = listOf(
                        AudioTrack(
                            id = "1",
                            title = "Silver for Monsters",
                            artist = "Marcin Przybylowicz",
                            album = "The Witcher 3",
                            durationMs = 139_000L,
                            artworkUri = null
                        )
                    ),
                    title = "Silver for Monsters",
                    artist = "Marcin Przybylowicz",
                    album = "The Witcher 3",
                    albumArtist = "Marcin Przybylowicz",
                    trackNumber = "1",
                    discNumber = "1",
                    year = "2015",
                    genre = "Soundtrack"
                ),
                isCoverScreen = false,
                onFieldChange = { _, _ -> },
                onToggleField = {},
                onPickArtwork = {},
                onRemoveArtwork = {},
                onKeepArtwork = {},
                onToggleBackup = {},
                onRequestSave = {},
                onDismiss = {},
                onDismissError = {}
            )
        }
    }
}

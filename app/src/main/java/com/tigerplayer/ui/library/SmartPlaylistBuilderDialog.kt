package com.tigerplayer.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tigerplayer.data.local.MediaSource
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.engine.library.DateOp
import com.tigerplayer.engine.library.NumberOp
import com.tigerplayer.engine.library.SmartPlaylist
import com.tigerplayer.engine.library.SmartPlaylistPreset
import com.tigerplayer.engine.library.SmartPlaylistSortBy
import com.tigerplayer.engine.library.SmartPredicate
import com.tigerplayer.engine.library.TextOp
import com.tigerplayer.ui.theme.TigerPlayerTheme
import com.tigerplayer.ui.theme.aardBlue

object SmartPlaylistTestTags {
    const val CREATE_BUTTON = "smart_playlist_create_button"
    const val BUILDER_DIALOG = "smart_playlist_builder_dialog"
    const val NAME_INPUT = "smart_playlist_name_input"
    const val MATCH_MODE_CHIP = "smart_playlist_match_mode_chip"
    const val ADD_RULE_BUTTON = "smart_playlist_add_rule_button"
    const val SAVE_BUTTON = "smart_playlist_save_button"
    const val DETAIL_SHEET = "smart_playlist_detail_sheet"
    const val PLAY_ALL_BUTTON = "smart_playlist_play_all_button"
    const val SHUFFLE_BUTTON = "smart_playlist_shuffle_button"
    const val QUEUE_ALL_BUTTON = "smart_playlist_queue_all_button"
    const val EDIT_BUTTON = "smart_playlist_edit_button"
    const val DELETE_BUTTON = "smart_playlist_delete_button"
    fun card(id: Long): String = "smart_playlist_card_$id"
}

@Composable
fun SmartPlaylistBuilderDialog(
    initialState: SmartRuleEditorState,
    presets: List<SmartPlaylistPreset>,
    isEditing: Boolean,
    onDismiss: () -> Unit,
    onSave: (SmartRuleEditorState) -> Unit,
    modifier: Modifier = Modifier
) {
    var state by remember(initialState) { mutableStateOf(initialState) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag(SmartPlaylistTestTags.BUILDER_DIALOG),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.aardBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isEditing) "EDIT SMART PLAYLIST" else "NEW SMART PLAYLIST",
                    fontWeight = FontWeight.Black
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!isEditing && presets.isNotEmpty()) {
                    Text(
                        text = "STARTER PRESETS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        presets.forEach { preset ->
                            FilterChip(
                                selected = state.name == preset.name,
                                onClick = { state = preset.toEditorState() },
                                label = { Text(preset.name, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = state.name,
                    onValueChange = { state = state.copy(name = it) },
                    label = { Text("Smart Playlist Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(SmartPlaylistTestTags.NAME_INPUT),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.aardBlue,
                        cursorColor = MaterialTheme.aardBlue
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Rules:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    FilterChip(
                        selected = true,
                        onClick = {
                            val next = if (state.matchMode == SmartRuleMatchMode.ALL) {
                                SmartRuleMatchMode.ANY
                            } else {
                                SmartRuleMatchMode.ALL
                            }
                            state = state.copy(matchMode = next)
                        },
                        label = { Text(state.matchMode.label, fontSize = 11.sp) },
                        modifier = Modifier.testTag(SmartPlaylistTestTags.MATCH_MODE_CHIP)
                    )
                }

                state.rules.forEachIndexed { index, draft ->
                    SmartRuleDraftRow(
                        draft = draft,
                        canRemove = state.rules.size > 1,
                        onChange = { updated ->
                            state = state.copy(
                                rules = state.rules.toMutableList().apply { set(index, updated) }
                            )
                        },
                        onRemove = {
                            if (state.rules.size > 1) {
                                state = state.copy(
                                    rules = state.rules.toMutableList().apply { removeAt(index) }
                                )
                            }
                        }
                    )
                }

                OutlinedButton(
                    onClick = {
                        state = state.copy(rules = state.rules + SmartRuleDraft())
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(SmartPlaylistTestTags.ADD_RULE_BUTTON)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Rule")
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SortSelectorChip(
                        selected = state.sortBy,
                        onSelected = { state = state.copy(sortBy = it) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = state.sortDescending,
                        onClick = { state = state.copy(sortDescending = !state.sortDescending) },
                        label = { Text(if (state.sortDescending) "DESC" else "ASC", fontSize = 11.sp) }
                    )
                }

                OutlinedTextField(
                    value = state.limitText,
                    onValueChange = { text ->
                        state = state.copy(limitText = text.filter { it.isDigit() })
                    },
                    label = { Text("Track Limit (optional, e.g. 50)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(state) },
                enabled = state.name.isNotBlank() && state.rules.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.aardBlue),
                modifier = Modifier.testTag(SmartPlaylistTestTags.SAVE_BUTTON)
            ) {
                Text("SAVE", fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun SmartRuleDraftRow(
    draft: SmartRuleDraft,
    canRemove: Boolean,
    onChange: (SmartRuleDraft) -> Unit,
    onRemove: () -> Unit
) {
    var showFieldMenu by remember { mutableStateOf(false) }
    var showOpMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .border(1.dp, MaterialTheme.aardBlue.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box {
                FilterChip(
                    selected = true,
                    onClick = { showFieldMenu = true },
                    label = { Text(draft.field.label, fontSize = 11.sp) }
                )
                DropdownMenu(
                    expanded = showFieldMenu,
                    onDismissRequest = { showFieldMenu = false }
                ) {
                    SmartRuleField.entries.forEach { field ->
                        DropdownMenuItem(
                            text = { Text(field.label) },
                            onClick = {
                                showFieldMenu = false
                                onChange(draft.copy(field = field))
                            }
                        )
                    }
                }
            }

            if (canRemove) {
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Remove rule",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        when (draft.field) {
            SmartRuleField.LIKED -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = draft.boolValue,
                        onClick = { onChange(draft.copy(boolValue = true)) },
                        label = { Text("Is Liked", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = !draft.boolValue,
                        onClick = { onChange(draft.copy(boolValue = false)) },
                        label = { Text("Not Liked", fontSize = 11.sp) }
                    )
                }
            }
            SmartRuleField.SOURCE -> {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MediaSource.entries.forEach { source ->
                        FilterChip(
                            selected = draft.sourceValue == source,
                            onClick = { onChange(draft.copy(sourceValue = source)) },
                            label = { Text(source.name, fontSize = 11.sp) }
                        )
                    }
                }
            }
            SmartRuleField.PLAY_COUNT, SmartRuleField.YEAR, SmartRuleField.DURATION_SEC -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        FilterChip(
                            selected = false,
                            onClick = { showOpMenu = true },
                            label = { Text(draft.numberOp.label, fontSize = 11.sp) }
                        )
                        DropdownMenu(
                            expanded = showOpMenu,
                            onDismissRequest = { showOpMenu = false }
                        ) {
                            NumberOp.entries.forEach { op ->
                                DropdownMenuItem(
                                    text = { Text(op.label) },
                                    onClick = {
                                        showOpMenu = false
                                        onChange(draft.copy(numberOp = op))
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = draft.valueText,
                        onValueChange = { text ->
                            onChange(draft.copy(valueText = text.filter { it.isDigit() }))
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            SmartRuleField.LAST_PLAYED, SmartRuleField.DATE_ADDED -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        FilterChip(
                            selected = false,
                            onClick = { showOpMenu = true },
                            label = { Text(draft.dateOp.label, fontSize = 11.sp) }
                        )
                        DropdownMenu(
                            expanded = showOpMenu,
                            onDismissRequest = { showOpMenu = false }
                        ) {
                            val ops = if (draft.field == SmartRuleField.LAST_PLAYED) {
                                listOf(DateOp.WITHIN_LAST_DAYS, DateOp.NOT_IN_LAST_DAYS, DateOp.NEVER)
                            } else {
                                listOf(DateOp.WITHIN_LAST_DAYS, DateOp.NOT_IN_LAST_DAYS)
                            }
                            ops.forEach { op ->
                                DropdownMenuItem(
                                    text = { Text(op.label) },
                                    onClick = {
                                        showOpMenu = false
                                        onChange(draft.copy(dateOp = op))
                                    }
                                )
                            }
                        }
                    }
                    if (draft.dateOp != DateOp.NEVER) {
                        OutlinedTextField(
                            value = draft.valueText,
                            onValueChange = { text ->
                                onChange(draft.copy(valueText = text.filter { it.isDigit() }))
                            },
                            label = { Text("Days") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            SmartRuleField.ARTIST, SmartRuleField.ALBUM, SmartRuleField.GENRE, SmartRuleField.TITLE -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        FilterChip(
                            selected = false,
                            onClick = { showOpMenu = true },
                            label = { Text(draft.textOp.label, fontSize = 11.sp) }
                        )
                        DropdownMenu(
                            expanded = showOpMenu,
                            onDismissRequest = { showOpMenu = false }
                        ) {
                            TextOp.entries.forEach { op ->
                                DropdownMenuItem(
                                    text = { Text(op.label) },
                                    onClick = {
                                        showOpMenu = false
                                        onChange(draft.copy(textOp = op))
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = draft.valueText,
                        onValueChange = { onChange(draft.copy(valueText = it)) },
                        label = { Text("Value") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SortSelectorChip(
    selected: SmartPlaylistSortBy,
    onSelected: (SmartPlaylistSortBy) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        FilterChip(
            selected = false,
            onClick = { expanded = true },
            label = { Text("Sort: ${selected.label}", fontSize = 11.sp, maxLines = 1) },
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SmartPlaylistSortBy.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(sort.label) },
                    onClick = {
                        expanded = false
                        onSelected(sort)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartPlaylistDetailSheet(
    smartPlaylist: SmartPlaylist,
    tracks: List<AudioTrack>,
    onDismiss: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onQueueAll: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPlayTrack: (AudioTrack) -> Unit,
    onQueueTrack: (AudioTrack) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(SmartPlaylistTestTags.DETAIL_SHEET)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = smartPlaylist.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${tracks.size} tracks • ${smartPlaylist.predicate.summarize()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag(SmartPlaylistTestTags.EDIT_BUTTON)
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit rules")
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.testTag(SmartPlaylistTestTags.DELETE_BUTTON)
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete smart playlist")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPlayAll,
                    enabled = tracks.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.aardBlue),
                    modifier = Modifier
                        .weight(1f)
                        .testTag(SmartPlaylistTestTags.PLAY_ALL_BUTTON)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Play", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onShuffle,
                    enabled = tracks.isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .testTag(SmartPlaylistTestTags.SHUFFLE_BUTTON)
                ) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Shuffle")
                }
                OutlinedButton(
                    onClick = onQueueAll,
                    enabled = tracks.isNotEmpty(),
                    modifier = Modifier.testTag(SmartPlaylistTestTags.QUEUE_ALL_BUTTON)
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = "Queue all",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (tracks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No matching tracks right now. Listening history updates this list live.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(tracks, key = { it.id }) { track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                .clickable { onPlayTrack(track) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                                contentDescription = null,
                                tint = MaterialTheme.aardBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${track.artist} • ${track.album}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { onQueueTrack(track) }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                    contentDescription = "Add to queue",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SmartPlaylistBuilderDialogPreview() {
    TigerPlayerTheme {
        SmartPlaylistBuilderDialog(
            initialState = SmartRuleEditorState(
                name = "Liked & Forgotten",
                matchMode = SmartRuleMatchMode.ALL,
                rules = listOf(
                    SmartRuleDraft(field = SmartRuleField.LIKED, boolValue = true),
                    SmartRuleDraft(
                        field = SmartRuleField.LAST_PLAYED,
                        dateOp = DateOp.NOT_IN_LAST_DAYS,
                        valueText = "90"
                    )
                )
            ),
            presets = emptyList(),
            isEditing = false,
            onDismiss = {},
            onSave = {}
        )
    }
}

package com.tigerplayer.ui.coverscreen.library

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.tigerplayer.engine.library.TextOp
import com.tigerplayer.ui.coverscreen.CoverScreenTestTags
import com.tigerplayer.ui.library.SmartRuleDraft
import com.tigerplayer.ui.library.SmartRuleEditorState
import com.tigerplayer.ui.library.SmartRuleField
import com.tigerplayer.ui.library.SmartRuleMatchMode
import com.tigerplayer.ui.library.summarize
import com.tigerplayer.ui.library.toEditorState
import com.tigerplayer.ui.theme.TigerPlayerTheme
import com.tigerplayer.ui.theme.aardBlue

@Composable
fun CoverScreenSmartPlaylistBuilderView(
    initialState: SmartRuleEditorState,
    presets: List<SmartPlaylistPreset>,
    isEditing: Boolean,
    onBack: () -> Unit,
    onSave: (SmartRuleEditorState) -> Unit,
    modifier: Modifier = Modifier
) {
    var state by remember(initialState) { mutableStateOf(initialState) }

    BackHandler(enabled = true) { onBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(CoverScreenTestTags.SMART_PLAYLIST_BUILDER)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.aardBlue,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isEditing) "Edit Smart Rules" else "New Smart Playlist",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!isEditing && presets.isNotEmpty()) {
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
                            label = { Text(preset.name, fontSize = 10.sp) }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = state.name,
                onValueChange = { state = state.copy(name = it) },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(CoverScreenTestTags.SMART_PLAYLIST_NAME_INPUT)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                    label = { Text(state.matchMode.label, fontSize = 10.sp) },
                    modifier = Modifier.testTag(CoverScreenTestTags.SMART_PLAYLIST_MATCH_MODE)
                )
                FilterChip(
                    selected = state.sortDescending,
                    onClick = { state = state.copy(sortDescending = !state.sortDescending) },
                    label = {
                        Text(
                            "${state.sortBy.label} (${if (state.sortDescending) "DESC" else "ASC"})",
                            fontSize = 10.sp
                        )
                    }
                )
            }

            state.rules.forEachIndexed { index, draft ->
                CoverScreenRuleCard(
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { state = state.copy(rules = state.rules + SmartRuleDraft()) },
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag(CoverScreenTestTags.SMART_PLAYLIST_ADD_RULE)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Rule", fontSize = 11.sp)
                }

                OutlinedTextField(
                    value = state.limitText,
                    onValueChange = { text ->
                        state = state.copy(limitText = text.filter { it.isDigit() })
                    },
                    label = { Text("Limit") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(92.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
            ) {
                Text("Cancel")
            }
            Button(
                onClick = { onSave(state) },
                enabled = state.name.isNotBlank() && state.rules.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.aardBlue),
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .testTag(CoverScreenTestTags.SMART_PLAYLIST_SAVE)
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CoverScreenRuleCard(
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
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF161A24))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                FilterChip(
                    selected = true,
                    onClick = { showFieldMenu = true },
                    label = { Text(draft.field.label, fontSize = 10.sp) }
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
                IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Remove rule",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        when (draft.field) {
            SmartRuleField.LIKED -> {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = draft.boolValue,
                        onClick = { onChange(draft.copy(boolValue = true)) },
                        label = { Text("Liked", fontSize = 10.sp) }
                    )
                    FilterChip(
                        selected = !draft.boolValue,
                        onClick = { onChange(draft.copy(boolValue = false)) },
                        label = { Text("Not Liked", fontSize = 10.sp) }
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
                            label = { Text(source.name, fontSize = 10.sp) }
                        )
                    }
                }
            }
            SmartRuleField.PLAY_COUNT, SmartRuleField.YEAR, SmartRuleField.DURATION_SEC -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        FilterChip(
                            selected = false,
                            onClick = { showOpMenu = true },
                            label = { Text(draft.numberOp.sqlOperator, fontSize = 11.sp) }
                        )
                        DropdownMenu(expanded = showOpMenu, onDismissRequest = { showOpMenu = false }) {
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        FilterChip(
                            selected = false,
                            onClick = { showOpMenu = true },
                            label = { Text(draft.dateOp.label, fontSize = 10.sp) }
                        )
                        DropdownMenu(expanded = showOpMenu, onDismissRequest = { showOpMenu = false }) {
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
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        FilterChip(
                            selected = false,
                            onClick = { showOpMenu = true },
                            label = { Text(draft.textOp.label, fontSize = 10.sp) }
                        )
                        DropdownMenu(expanded = showOpMenu, onDismissRequest = { showOpMenu = false }) {
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
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun CoverScreenSmartPlaylistDetailView(
    smartPlaylist: SmartPlaylist,
    tracks: List<AudioTrack>,
    onBack: () -> Unit,
    onPlayAll: (List<AudioTrack>) -> Unit,
    onShufflePlay: (List<AudioTrack>) -> Unit,
    onQueueAll: (List<AudioTrack>) -> Unit,
    onPlayTrack: (AudioTrack) -> Unit,
    onQueueTrack: (AudioTrack) -> Unit,
    onEditRules: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = true) { onBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag(CoverScreenTestTags.SMART_PLAYLIST_DETAIL)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                    .testTag(CoverScreenTestTags.SMART_PLAYLIST_BACK)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = smartPlaylist.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${tracks.size} tracks • ${smartPlaylist.predicate.summarize()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.aardBlue,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onEditRules,
                modifier = Modifier.testTag(CoverScreenTestTags.SMART_PLAYLIST_EDIT)
            ) {
                Icon(Icons.Rounded.Edit, contentDescription = "Edit", tint = Color.White)
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag(CoverScreenTestTags.SMART_PLAYLIST_DELETE)
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = Color.White)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = { onPlayAll(tracks) },
                enabled = tracks.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.aardBlue),
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .testTag(CoverScreenTestTags.SMART_PLAYLIST_PLAY_ALL)
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Play", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = { onShufflePlay(tracks) },
                enabled = tracks.isNotEmpty(),
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .testTag(CoverScreenTestTags.SMART_PLAYLIST_SHUFFLE)
            ) {
                Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Shuffle", fontSize = 11.sp)
            }
            OutlinedButton(
                onClick = { onQueueAll(tracks) },
                enabled = tracks.isNotEmpty(),
                modifier = Modifier
                    .height(42.dp)
                    .testTag(CoverScreenTestTags.SMART_PLAYLIST_QUEUE_ALL)
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = "Queue all",
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No matching tracks yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.65f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(tracks, key = { it.id }) { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF151923))
                            .clickable { onPlayTrack(track) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.65f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { onQueueTrack(track) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                                contentDescription = "Add to queue",
                                tint = MaterialTheme.aardBlue,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 360)
@Composable
private fun CoverScreenSmartPlaylistViewPreview() {
    TigerPlayerTheme(darkTheme = true) {
        CoverScreenSmartPlaylistBuilderView(
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
                ),
                sortBy = SmartPlaylistSortBy.DATE_ADDED,
                sortDescending = true,
                limitText = "50"
            ),
            presets = emptyList(),
            isEditing = false,
            onBack = {},
            onSave = {}
        )
    }
}

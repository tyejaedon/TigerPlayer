package com.tigerplayer.ui.coverscreen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun CoverScreenSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onVoiceSearch: () -> Unit,
    onClose: () -> Unit,
    voiceSearchAvailable: Boolean,
    voiceSearchError: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .testTag(CoverScreenTestTags.SEARCH_FIELD),
                singleLine = true,
                label = { Text("Search library") },
                leadingIcon = {
                    Icon(imageVector = Icons.Filled.Search, contentDescription = null)
                }
            )
            IconButton(
                onClick = onVoiceSearch,
                enabled = voiceSearchAvailable,
                modifier = Modifier
                    .testTag(CoverScreenTestTags.SEARCH_VOICE)
                    .semantics { contentDescription = "Search by voice" }
            ) {
                Icon(imageVector = Icons.Filled.Mic, contentDescription = null)
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .testTag(CoverScreenTestTags.SEARCH_CLOSE)
                    .semantics { contentDescription = "Close search" }
            ) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = null)
            }
        }
        if (!voiceSearchAvailable || voiceSearchError != null) {
            Text(
                text = voiceSearchError ?: "Voice search is unavailable on this device",
                modifier = Modifier
                    .testTag(CoverScreenTestTags.SEARCH_VOICE_STATUS)
                    .semantics { contentDescription = voiceSearchError ?: "Voice search unavailable" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

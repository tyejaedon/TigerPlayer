package com.tigerplayer.ui.coverscreen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ListItem
import com.tigerplayer.data.model.Artist
import com.tigerplayer.data.model.AudioTrack

enum class CoverScreenSearchResultType(val label: String) {
    ALL("All"),
    ARTISTS("Artists"),
    ALBUMS("Albums"),
    TRACKS("Tracks")
}

@Composable
fun CoverScreenSearchResults(
    query: String,
    artists: List<Artist>,
    albums: List<CoverScreenAlbum>,
    tracks: List<AudioTrack>,
    onArtistSelected: (Artist) -> Unit,
    onAlbumSelected: (CoverScreenAlbum) -> Unit,
    onPlayTrack: (AudioTrack) -> Unit,
    onQueueTrack: (AudioTrack) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedType by remember { mutableStateOf(CoverScreenSearchResultType.ALL) }
    val normalizedQuery = query.trim()
    val matchingTracks = remember(tracks, normalizedQuery) {
        tracks.filter {
            it.title.contains(normalizedQuery, ignoreCase = true) ||
                it.artist.contains(normalizedQuery, ignoreCase = true) ||
                it.album.contains(normalizedQuery, ignoreCase = true)
        }
    }
    val matchingAlbums = remember(albums, artists, matchingTracks, normalizedQuery) {
        albums.filter { album ->
            album.name.contains(normalizedQuery, ignoreCase = true) ||
                matchingTracks.any { it.album == album.name } ||
                artists.any { artist ->
                    artist.id.equals(album.artistId, ignoreCase = true) &&
                        artist.name.contains(normalizedQuery, ignoreCase = true)
                }
        }
    }
    val matchingArtists = remember(artists, matchingAlbums, matchingTracks, normalizedQuery) {
        artists.filter { artist ->
            artist.name.contains(normalizedQuery, ignoreCase = true) ||
                matchingAlbums.any { it.artistId.equals(artist.id, ignoreCase = true) } ||
                matchingTracks.any { it.artist.contains(artist.name, ignoreCase = true) }
        }
    }
    val hasResults = when (selectedType) {
        CoverScreenSearchResultType.ALL ->
            matchingArtists.isNotEmpty() || matchingAlbums.isNotEmpty() || matchingTracks.isNotEmpty()

        CoverScreenSearchResultType.ARTISTS -> matchingArtists.isNotEmpty()
        CoverScreenSearchResultType.ALBUMS -> matchingAlbums.isNotEmpty()
        CoverScreenSearchResultType.TRACKS -> matchingTracks.isNotEmpty()
    }

    LazyColumn(
        modifier = modifier.testTag(CoverScreenTestTags.SEARCH_RESULTS),
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        item(key = "search-result-filters") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CoverScreenSearchResultType.entries.forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = { selectedType = type },
                        label = { Text(type.label) },
                        modifier = Modifier.testTag(CoverScreenTestTags.searchResultType(type.name))
                    )
                }
            }
        }

        if (!hasResults) {
            item(key = "search-result-empty") {
                Text(
                    text = "No results for \"$normalizedQuery\"",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .testTag(CoverScreenTestTags.SEARCH_NO_RESULTS)
                        .semantics {
                            contentDescription = "No results for $normalizedQuery"
                        },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            if (selectedType == CoverScreenSearchResultType.ALL ||
                selectedType == CoverScreenSearchResultType.ARTISTS
            ) {
                if (matchingArtists.isNotEmpty()) {
                    item(key = "search-result-artists-header") {
                        SearchResultHeader("Artists")
                    }
                    items(matchingArtists, key = { "artist:${it.source.name}:${it.id}" }) { artist ->
                        ListItem(
                            headlineContent = { Text(artist.name) },
                            supportingContent = { Text("${artist.albumCount} albums") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(CoverScreenTestTags.searchResultArtist(artist.id))
                                .clickable { onArtistSelected(artist) }
                                .semantics {
                                    contentDescription = "Play ${artist.name}, ${artist.albumCount} albums"
                                }
                        )
                    }
                }
            }

            if (selectedType == CoverScreenSearchResultType.ALL ||
                selectedType == CoverScreenSearchResultType.ALBUMS
            ) {
                if (matchingAlbums.isNotEmpty()) {
                    item(key = "search-result-albums-header") {
                        SearchResultHeader("Albums")
                    }
                    items(matchingAlbums, key = { "album:${it.id}" }) { album ->
                        ListItem(
                            headlineContent = { Text(album.name) },
                            supportingContent = { Text("${album.trackCount} tracks") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(CoverScreenTestTags.searchResultAlbum(album.id))
                                .clickable { onAlbumSelected(album) }
                                .semantics {
                                    contentDescription = "Open album ${album.name}, ${album.trackCount} tracks"
                                }
                        )
                    }
                }
            }

            if (selectedType == CoverScreenSearchResultType.ALL ||
                selectedType == CoverScreenSearchResultType.TRACKS
            ) {
                if (matchingTracks.isNotEmpty()) {
                    item(key = "search-result-tracks-header") {
                        SearchResultHeader("Tracks")
                    }
                    items(matchingTracks, key = { "track:${it.id}" }) { track ->
                        ListItem(
                            headlineContent = { Text(track.title) },
                            supportingContent = { Text("${track.artist} · ${track.album}") },
                            trailingContent = {
                                IconButton(
                                    onClick = { onQueueTrack(track) },
                                    modifier = Modifier.testTag(CoverScreenTestTags.searchResultQueue(track.id))
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = "Add ${track.title} to queue"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(CoverScreenTestTags.searchResultTrack(track.id))
                                .clickable { onPlayTrack(track) }
                                .semantics {
                                    contentDescription = "Play ${track.title} by ${track.artist}"
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
    )
}

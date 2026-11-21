package com.tigerplayer.ui.library

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tigerplayer.data.model.ArtworkEditAction
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.model.BatchTagEditRequest
import com.tigerplayer.data.model.TagWriteResult
import com.tigerplayer.engine.MetadataEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TagField {
    TITLE,
    ARTIST,
    ALBUM,
    ALBUM_ARTIST,
    TRACK_NUMBER,
    DISC_NUMBER,
    YEAR,
    GENRE
}

data class TagEditorUiState(
    val tracks: List<AudioTrack> = emptyList(),
    val isBatchMode: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val trackNumber: String = "",
    val discNumber: String = "",
    val year: String = "",
    val genre: String = "",
    val enabledFields: Set<TagField> = TagField.entries.toSet(),
    val currentArtworkBytes: ByteArray? = null,
    val artworkAction: ArtworkEditAction = ArtworkEditAction.Keep,
    val createBackup: Boolean = true,
    val showConfirmDialog: Boolean = false,
    val pendingIntentSender: IntentSender? = null,
    val errorMessage: String? = null,
    val saveSuccess: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TagEditorUiState) return false
        return tracks == other.tracks &&
            isBatchMode == other.isBatchMode &&
            isLoading == other.isLoading &&
            isSaving == other.isSaving &&
            title == other.title &&
            artist == other.artist &&
            album == other.album &&
            albumArtist == other.albumArtist &&
            trackNumber == other.trackNumber &&
            discNumber == other.discNumber &&
            year == other.year &&
            genre == other.genre &&
            enabledFields == other.enabledFields &&
            currentArtworkBytes.contentEquals(other.currentArtworkBytes) &&
            artworkAction == other.artworkAction &&
            createBackup == other.createBackup &&
            showConfirmDialog == other.showConfirmDialog &&
            pendingIntentSender == other.pendingIntentSender &&
            errorMessage == other.errorMessage &&
            saveSuccess == other.saveSuccess
    }

    override fun hashCode(): Int {
        var result = tracks.hashCode()
        result = 31 * result + isBatchMode.hashCode()
        result = 31 * result + isLoading.hashCode()
        result = 31 * result + isSaving.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + artist.hashCode()
        result = 31 * result + album.hashCode()
        result = 31 * result + albumArtist.hashCode()
        result = 31 * result + trackNumber.hashCode()
        result = 31 * result + discNumber.hashCode()
        result = 31 * result + year.hashCode()
        result = 31 * result + genre.hashCode()
        result = 31 * result + enabledFields.hashCode()
        result = 31 * result + (currentArtworkBytes?.contentHashCode() ?: 0)
        result = 31 * result + artworkAction.hashCode()
        result = 31 * result + createBackup.hashCode()
        result = 31 * result + showConfirmDialog.hashCode()
        result = 31 * result + (pendingIntentSender?.hashCode() ?: 0)
        result = 31 * result + (errorMessage?.hashCode() ?: 0)
        result = 31 * result + saveSuccess.hashCode()
        return result
    }
}

@HiltViewModel
class TagEditorViewModel @Inject constructor(
    private val metadataEngine: MetadataEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(TagEditorUiState())
    val uiState: StateFlow<TagEditorUiState> = _uiState.asStateFlow()

    fun openForTracks(tracks: List<AudioTrack>) {
        val localTracks = tracks.filter { it.isLocal && !it.isRemote }
        if (localTracks.isEmpty()) {
            _uiState.value = TagEditorUiState(
                errorMessage = "Only local audio files can be tag-edited."
            )
            return
        }

        val isBatch = localTracks.size > 1
        if (isBatch) {
            _uiState.value = TagEditorUiState(
                tracks = localTracks,
                isBatchMode = true,
                enabledFields = emptySet()
            )
            return
        }

        val single = localTracks.first()
        _uiState.value = TagEditorUiState(
            tracks = localTracks,
            isBatchMode = false,
            isLoading = true,
            title = single.title,
            artist = single.artist,
            album = single.album,
            trackNumber = if (single.trackNumber > 0) single.trackNumber.toString() else "",
            year = single.year.orEmpty()
        )

        viewModelScope.launch {
            val result = metadataEngine.readLocalTrackTags(single)
            result.onSuccess { tags ->
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        title = tags.title.ifBlank { single.title },
                        artist = tags.artist.ifBlank { single.artist },
                        album = tags.album.ifBlank { single.album },
                        albumArtist = tags.albumArtist,
                        trackNumber = tags.trackNumber.ifBlank {
                            if (single.trackNumber > 0) single.trackNumber.toString() else ""
                        },
                        discNumber = tags.discNumber,
                        year = tags.year.ifBlank { single.year.orEmpty() },
                        genre = tags.genre,
                        currentArtworkBytes = tags.artworkBytes
                    )
                }
            }.onFailure { err ->
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Unable to read existing tags from file."
                    )
                }
            }
        }
    }

    fun updateField(field: TagField, value: String) {
        _uiState.update { state ->
            val updatedEnabled = if (state.isBatchMode) state.enabledFields + field else state.enabledFields
            when (field) {
                TagField.TITLE -> state.copy(title = value, enabledFields = updatedEnabled)
                TagField.ARTIST -> state.copy(artist = value, enabledFields = updatedEnabled)
                TagField.ALBUM -> state.copy(album = value, enabledFields = updatedEnabled)
                TagField.ALBUM_ARTIST -> state.copy(albumArtist = value, enabledFields = updatedEnabled)
                TagField.TRACK_NUMBER -> state.copy(trackNumber = value, enabledFields = updatedEnabled)
                TagField.DISC_NUMBER -> state.copy(discNumber = value, enabledFields = updatedEnabled)
                TagField.YEAR -> state.copy(year = value, enabledFields = updatedEnabled)
                TagField.GENRE -> state.copy(genre = value, enabledFields = updatedEnabled)
            }
        }
    }

    fun toggleFieldEnabled(field: TagField) {
        _uiState.update { state ->
            if (!state.isBatchMode) return@update state
            val next = if (field in state.enabledFields) {
                state.enabledFields - field
            } else {
                state.enabledFields + field
            }
            state.copy(enabledFields = next)
        }
    }

    fun replaceArtwork(bytes: ByteArray, mimeType: String) {
        _uiState.update {
            it.copy(
                artworkAction = ArtworkEditAction.Replace(bytes, mimeType),
                currentArtworkBytes = bytes
            )
        }
    }

    fun removeArtwork() {
        _uiState.update {
            it.copy(
                artworkAction = ArtworkEditAction.Remove,
                currentArtworkBytes = null
            )
        }
    }

    fun keepArtwork() {
        _uiState.update { it.copy(artworkAction = ArtworkEditAction.Keep) }
    }

    fun setCreateBackup(enabled: Boolean) {
        _uiState.update { it.copy(createBackup = enabled) }
    }

    fun requestSave() {
        _uiState.update { it.copy(showConfirmDialog = true, errorMessage = null) }
    }

    fun dismissConfirmDialog() {
        _uiState.update { it.copy(showConfirmDialog = false) }
    }

    fun confirmAndSave() {
        val snapshot = _uiState.value
        if (snapshot.tracks.isEmpty() || snapshot.isSaving) return

        val enabled = snapshot.enabledFields
        val request = if (snapshot.isBatchMode) {
            BatchTagEditRequest(
                title = if (TagField.TITLE in enabled) snapshot.title else null,
                artist = if (TagField.ARTIST in enabled) snapshot.artist else null,
                album = if (TagField.ALBUM in enabled) snapshot.album else null,
                albumArtist = if (TagField.ALBUM_ARTIST in enabled) snapshot.albumArtist else null,
                trackNumber = if (TagField.TRACK_NUMBER in enabled) snapshot.trackNumber else null,
                discNumber = if (TagField.DISC_NUMBER in enabled) snapshot.discNumber else null,
                year = if (TagField.YEAR in enabled) snapshot.year else null,
                genre = if (TagField.GENRE in enabled) snapshot.genre else null,
                artworkAction = snapshot.artworkAction
            )
        } else {
            BatchTagEditRequest(
                title = snapshot.title,
                artist = snapshot.artist,
                album = snapshot.album,
                albumArtist = snapshot.albumArtist,
                trackNumber = snapshot.trackNumber,
                discNumber = snapshot.discNumber,
                year = snapshot.year,
                genre = snapshot.genre,
                artworkAction = snapshot.artworkAction
            )
        }

        _uiState.update { it.copy(showConfirmDialog = false, isSaving = true, errorMessage = null) }

        viewModelScope.launch {
            when (val result = metadataEngine.writeLocalTrackTags(
                tracks = snapshot.tracks,
                edit = request,
                createBackup = snapshot.createBackup
            )) {
                is TagWriteResult.Success -> {
                    _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
                }
                is TagWriteResult.PermissionRequired -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            pendingIntentSender = result.intentSender
                        )
                    }
                }
                is TagWriteResult.Failure -> {
                    val details = result.errors.joinToString("\n") { error ->
                        if (error.trackTitle.isNotBlank()) "${error.trackTitle}: ${error.reason}"
                        else error.reason
                    }
                    _uiState.update { it.copy(isSaving = false, errorMessage = details) }
                }
            }
        }
    }

    fun onWritePermissionResult(granted: Boolean) {
        _uiState.update { it.copy(pendingIntentSender = null) }
        if (granted) {
            confirmAndSave()
        } else {
            _uiState.update {
                it.copy(errorMessage = "Write permission was denied. No files were modified.")
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun reset() {
        _uiState.value = TagEditorUiState()
    }
}

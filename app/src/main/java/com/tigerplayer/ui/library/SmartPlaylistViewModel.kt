package com.tigerplayer.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tigerplayer.data.local.MediaSource
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.engine.library.DateOp
import com.tigerplayer.engine.library.NumberOp
import com.tigerplayer.engine.library.SmartPlaylist
import com.tigerplayer.engine.library.SmartPlaylistEngine
import com.tigerplayer.engine.library.SmartPlaylistPreset
import com.tigerplayer.engine.library.SmartPlaylistSortBy
import com.tigerplayer.engine.library.SmartPredicate
import com.tigerplayer.engine.library.TextOp
import com.tigerplayer.ui.player.PlaybackStateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SmartRuleMatchMode(val label: String) {
    ALL("ALL (AND)"),
    ANY("ANY (OR)")
}

enum class SmartRuleField(val label: String) {
    PLAY_COUNT("Play Count"),
    LAST_PLAYED("Last Played"),
    DATE_ADDED("Date Added"),
    LIKED("Liked"),
    ARTIST("Artist"),
    ALBUM("Album"),
    GENRE("Genre"),
    YEAR("Year"),
    DURATION_SEC("Duration (sec)"),
    SOURCE("Source"),
    TITLE("Title")
}

data class SmartRuleDraft(
    val field: SmartRuleField = SmartRuleField.PLAY_COUNT,
    val numberOp: NumberOp = NumberOp.GREATER_THAN,
    val textOp: TextOp = TextOp.CONTAINS,
    val dateOp: DateOp = DateOp.WITHIN_LAST_DAYS,
    val valueText: String = "3",
    val boolValue: Boolean = true,
    val sourceValue: MediaSource = MediaSource.LOCAL
)

data class SmartRuleEditorState(
    val name: String = "",
    val matchMode: SmartRuleMatchMode = SmartRuleMatchMode.ALL,
    val rules: List<SmartRuleDraft> = listOf(SmartRuleDraft()),
    val sortBy: SmartPlaylistSortBy = SmartPlaylistSortBy.PLAY_COUNT,
    val sortDescending: Boolean = true,
    val limitText: String = ""
)

fun SmartPlaylist.toEditorState(): SmartRuleEditorState {
    val (mode, drafts) = predicate.toMatchModeAndDrafts()
    return SmartRuleEditorState(
        name = name,
        matchMode = mode,
        rules = drafts.ifEmpty { listOf(SmartRuleDraft()) },
        sortBy = sortBy,
        sortDescending = sortDescending,
        limitText = limitCount?.toString().orEmpty()
    )
}

fun SmartPlaylistPreset.toEditorState(): SmartRuleEditorState {
    val (mode, drafts) = predicate.toMatchModeAndDrafts()
    return SmartRuleEditorState(
        name = name,
        matchMode = mode,
        rules = drafts.ifEmpty { listOf(SmartRuleDraft()) },
        sortBy = sortBy,
        sortDescending = sortDescending,
        limitText = limitCount?.toString().orEmpty()
    )
}

fun SmartRuleEditorState.toSmartPredicate(): SmartPredicate {
    val compiledRules = rules.map { it.toPredicate() }
    if (compiledRules.size == 1) return compiledRules.first()
    return when (matchMode) {
        SmartRuleMatchMode.ALL -> SmartPredicate.And(compiledRules)
        SmartRuleMatchMode.ANY -> SmartPredicate.Or(compiledRules)
    }
}

fun SmartPredicate.summarize(): String = when (this) {
    is SmartPredicate.And -> predicates.joinToString(" AND ") { it.summarize() }.ifEmpty { "All tracks" }
    is SmartPredicate.Or -> predicates.joinToString(" OR ") { it.summarize() }.ifEmpty { "No tracks" }
    is SmartPredicate.Not -> "NOT (${predicate.summarize()})"
    is SmartPredicate.PlayCount -> "Plays ${op.sqlOperator} $count"
    is SmartPredicate.LastPlayed -> when (op) {
        DateOp.NEVER -> "Never played"
        DateOp.NOT_IN_LAST_DAYS -> "Not played in ${value}d"
        DateOp.WITHIN_LAST_DAYS -> "Played in ${value}d"
        else -> "Last played"
    }
    is SmartPredicate.DateAdded -> when (op) {
        DateOp.WITHIN_LAST_DAYS -> "Added in ${value}d"
        DateOp.NOT_IN_LAST_DAYS -> "Added > ${value}d ago"
        else -> "Date added"
    }
    is SmartPredicate.Liked -> if (isLiked) "Liked" else "Not liked"
    is SmartPredicate.Artist -> "Artist ${op.label} '$value'"
    is SmartPredicate.Album -> "Album ${op.label} '$value'"
    is SmartPredicate.Genre -> "Genre ${op.label} '$value'"
    is SmartPredicate.Year -> "Year ${op.sqlOperator} $year"
    is SmartPredicate.Duration -> "Duration ${op.sqlOperator} ${durationMs / 1000L}s"
    is SmartPredicate.Source -> "Source = ${source.name}"
    is SmartPredicate.Title -> "Title ${op.label} '$value'"
}

private fun SmartPredicate.toMatchModeAndDrafts(): Pair<SmartRuleMatchMode, List<SmartRuleDraft>> {
    return when (this) {
        is SmartPredicate.And -> SmartRuleMatchMode.ALL to predicates.flatMap { it.flattenToDrafts() }
        is SmartPredicate.Or -> SmartRuleMatchMode.ANY to predicates.flatMap { it.flattenToDrafts() }
        else -> SmartRuleMatchMode.ALL to flattenToDrafts()
    }
}

private fun SmartPredicate.flattenToDrafts(): List<SmartRuleDraft> = when (this) {
    is SmartPredicate.And -> predicates.flatMap { it.flattenToDrafts() }
    is SmartPredicate.Or -> predicates.flatMap { it.flattenToDrafts() }
    is SmartPredicate.Not -> predicate.flattenToDrafts()
    is SmartPredicate.PlayCount -> listOf(
        SmartRuleDraft(field = SmartRuleField.PLAY_COUNT, numberOp = op, valueText = count.toString())
    )
    is SmartPredicate.LastPlayed -> listOf(
        SmartRuleDraft(field = SmartRuleField.LAST_PLAYED, dateOp = op, valueText = value.toString())
    )
    is SmartPredicate.DateAdded -> listOf(
        SmartRuleDraft(field = SmartRuleField.DATE_ADDED, dateOp = op, valueText = value.toString())
    )
    is SmartPredicate.Liked -> listOf(
        SmartRuleDraft(field = SmartRuleField.LIKED, boolValue = isLiked)
    )
    is SmartPredicate.Artist -> listOf(
        SmartRuleDraft(field = SmartRuleField.ARTIST, textOp = op, valueText = value)
    )
    is SmartPredicate.Album -> listOf(
        SmartRuleDraft(field = SmartRuleField.ALBUM, textOp = op, valueText = value)
    )
    is SmartPredicate.Genre -> listOf(
        SmartRuleDraft(field = SmartRuleField.GENRE, textOp = op, valueText = value)
    )
    is SmartPredicate.Year -> listOf(
        SmartRuleDraft(field = SmartRuleField.YEAR, numberOp = op, valueText = year.toString())
    )
    is SmartPredicate.Duration -> listOf(
        SmartRuleDraft(
            field = SmartRuleField.DURATION_SEC,
            numberOp = op,
            valueText = (durationMs / 1000L).toString()
        )
    )
    is SmartPredicate.Source -> listOf(
        SmartRuleDraft(field = SmartRuleField.SOURCE, sourceValue = source)
    )
    is SmartPredicate.Title -> listOf(
        SmartRuleDraft(field = SmartRuleField.TITLE, textOp = op, valueText = value)
    )
}

private fun SmartRuleDraft.toPredicate(): SmartPredicate = when (field) {
    SmartRuleField.PLAY_COUNT -> SmartPredicate.PlayCount(
        op = numberOp,
        count = valueText.toIntOrNull()?.coerceAtLeast(0) ?: 0
    )
    SmartRuleField.LAST_PLAYED -> SmartPredicate.LastPlayed(
        op = dateOp,
        value = valueText.toLongOrNull()?.coerceAtLeast(0L) ?: 30L
    )
    SmartRuleField.DATE_ADDED -> SmartPredicate.DateAdded(
        op = dateOp,
        value = valueText.toLongOrNull()?.coerceAtLeast(0L) ?: 30L
    )
    SmartRuleField.LIKED -> SmartPredicate.Liked(isLiked = boolValue)
    SmartRuleField.ARTIST -> SmartPredicate.Artist(op = textOp, value = valueText.trim())
    SmartRuleField.ALBUM -> SmartPredicate.Album(op = textOp, value = valueText.trim())
    SmartRuleField.GENRE -> SmartPredicate.Genre(op = textOp, value = valueText.trim())
    SmartRuleField.YEAR -> SmartPredicate.Year(
        op = numberOp,
        year = valueText.toIntOrNull()?.coerceAtLeast(1900) ?: 2024
    )
    SmartRuleField.DURATION_SEC -> SmartPredicate.Duration(
        op = numberOp,
        durationMs = (valueText.toLongOrNull()?.coerceAtLeast(0L) ?: 180L) * 1000L
    )
    SmartRuleField.SOURCE -> SmartPredicate.Source(source = sourceValue)
    SmartRuleField.TITLE -> SmartPredicate.Title(op = textOp, value = valueText.trim())
}

/**
 * ViewModel for rule-based Smart Playlists (issue #68).
 * Shared by the Main App Library UI and Cover Screen Library UI for full parity.
 */
@HiltViewModel
class SmartPlaylistViewModel @Inject constructor(
    private val smartPlaylistEngine: SmartPlaylistEngine,
    private val playbackStateManager: PlaybackStateManager
) : ViewModel() {

    val smartPlaylists: StateFlow<List<SmartPlaylist>> =
        smartPlaylistEngine.observeSmartPlaylists()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val starterPresets: List<SmartPlaylistPreset> = smartPlaylistEngine.starterPresets()

    fun observeTracks(smartPlaylist: SmartPlaylist): Flow<List<AudioTrack>> =
        smartPlaylistEngine.observeTracks(smartPlaylist)

    fun observeTracksById(smartPlaylistId: Long): Flow<List<AudioTrack>> =
        smartPlaylistEngine.observeTracksById(smartPlaylistId)

    fun createSmartPlaylist(
        name: String,
        predicate: SmartPredicate,
        sortBy: SmartPlaylistSortBy,
        sortDescending: Boolean,
        limitCount: Int?
    ) {
        viewModelScope.launch {
            smartPlaylistEngine.createSmartPlaylist(
                name = name,
                predicate = predicate,
                sortBy = sortBy,
                sortDescending = sortDescending,
                limitCount = limitCount
            )
        }
    }

    fun updateSmartPlaylist(
        existing: SmartPlaylist,
        name: String,
        predicate: SmartPredicate,
        sortBy: SmartPlaylistSortBy,
        sortDescending: Boolean,
        limitCount: Int?
    ) {
        viewModelScope.launch {
            smartPlaylistEngine.updateSmartPlaylist(
                id = existing.id,
                name = name,
                predicate = predicate,
                sortBy = sortBy,
                sortDescending = sortDescending,
                limitCount = limitCount,
                createdAt = existing.createdAt
            )
        }
    }

    fun deleteSmartPlaylist(id: Long) {
        viewModelScope.launch {
            smartPlaylistEngine.deleteSmartPlaylist(id)
        }
    }

    fun playAll(tracks: List<AudioTrack>) {
        if (tracks.isEmpty()) return
        playbackStateManager.playAll(tracks)
    }

    fun shufflePlay(tracks: List<AudioTrack>) {
        if (tracks.isEmpty()) return
        playbackStateManager.playAll(tracks.shuffled())
    }

    fun playTrack(track: AudioTrack, contextTracks: List<AudioTrack>) {
        playbackStateManager.playTrack(track, contextTracks)
    }

    fun addToQueue(track: AudioTrack) {
        playbackStateManager.addToQueue(track)
    }

    fun addNextToQueue(track: AudioTrack) {
        playbackStateManager.addNextToQueue(track)
    }

    fun queueAll(tracks: List<AudioTrack>) {
        tracks.forEach { playbackStateManager.addToQueue(it) }
    }
}

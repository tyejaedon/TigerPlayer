package com.tigerplayer.engine.library

import androidx.sqlite.db.SimpleSQLiteQuery
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.tigerplayer.data.local.MediaSource
import com.tigerplayer.data.local.dao.PlaylistDao
import com.tigerplayer.data.local.entity.SmartPlaylistEntity
import com.tigerplayer.data.local.entity.toAudioTrack
import com.tigerplayer.data.model.AudioTrack
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class NumberOp(val sqlOperator: String, val label: String) {
    EQUALS("=", "is"),
    NOT_EQUALS("!=", "is not"),
    GREATER_THAN(">", "greater than"),
    GREATER_OR_EQUAL(">=", "at least"),
    LESS_THAN("<", "less than"),
    LESS_OR_EQUAL("<=", "at most")
}

enum class TextOp(val label: String) {
    EQUALS("equals"),
    NOT_EQUALS("does not equal"),
    CONTAINS("contains"),
    NOT_CONTAINS("does not contain"),
    STARTS_WITH("starts with")
}

enum class DateOp(val label: String) {
    WITHIN_LAST_DAYS("in the last (days)"),
    NOT_IN_LAST_DAYS("not in the last (days)"),
    BEFORE_EPOCH_MS("before"),
    AFTER_EPOCH_MS("after"),
    NEVER("never")
}

enum class SmartPlaylistSortBy(val label: String) {
    PLAY_COUNT("Play Count"),
    LAST_PLAYED("Last Played"),
    DATE_ADDED("Date Added"),
    TITLE("Title"),
    ARTIST("Artist"),
    ALBUM("Album"),
    YEAR("Year"),
    DURATION("Duration")
}

sealed interface SmartPredicate {
    data class And(val predicates: List<SmartPredicate>) : SmartPredicate
    data class Or(val predicates: List<SmartPredicate>) : SmartPredicate
    data class Not(val predicate: SmartPredicate) : SmartPredicate

    data class PlayCount(val op: NumberOp, val count: Int) : SmartPredicate
    data class LastPlayed(val op: DateOp, val value: Long = 0L) : SmartPredicate
    data class DateAdded(val op: DateOp, val value: Long = 0L) : SmartPredicate
    data class Liked(val isLiked: Boolean = true) : SmartPredicate
    data class Artist(val op: TextOp, val value: String) : SmartPredicate
    data class Album(val op: TextOp, val value: String) : SmartPredicate
    data class Genre(val op: TextOp, val value: String) : SmartPredicate
    data class Year(val op: NumberOp, val year: Int) : SmartPredicate
    data class Duration(val op: NumberOp, val durationMs: Long) : SmartPredicate
    data class Source(val source: MediaSource) : SmartPredicate
    data class Title(val op: TextOp, val value: String) : SmartPredicate
}

data class SmartPlaylist(
    val id: Long,
    val name: String,
    val predicate: SmartPredicate,
    val sortBy: SmartPlaylistSortBy,
    val sortDescending: Boolean,
    val limitCount: Int?,
    val createdAt: Long,
    val updatedAt: Long
)

data class SmartPlaylistPreset(
    val name: String,
    val predicate: SmartPredicate,
    val sortBy: SmartPlaylistSortBy,
    val sortDescending: Boolean,
    val limitCount: Int? = null
)

data class CompiledSmartQuery(
    val sql: String,
    val args: List<Any>
) {
    fun toSupportSQLiteQuery(): SimpleSQLiteQuery = SimpleSQLiteQuery(sql, args.toTypedArray())
}

/**
 * Compiles composable [SmartPredicate] ASTs into parameterized Room `@RawQuery` statements
 * backed by `cached_tracks`, `playback_history`, and `artist_cache`.
 *
 * All literal values are passed exclusively as bound arguments (`?`) — never interpolated into SQL.
 */
@Singleton
class SmartPlaylistEngine private constructor(
    private val playlistDao: PlaylistDao,
    private val nowMsProvider: () -> Long
) {
    @Inject
    constructor(playlistDao: PlaylistDao) : this(playlistDao, System::currentTimeMillis)

    fun observeSmartPlaylists(): Flow<List<SmartPlaylist>> =
        playlistDao.getAllSmartPlaylistsFlow().map { entities ->
            entities.map { it.toDomain() }
        }

    fun observeTracks(smartPlaylist: SmartPlaylist): Flow<List<AudioTrack>> {
        val compiled = compileQuery(
            predicate = smartPlaylist.predicate,
            sortBy = smartPlaylist.sortBy,
            sortDescending = smartPlaylist.sortDescending,
            limitCount = smartPlaylist.limitCount
        )
        return playlistDao.observeSmartPlaylistTracks(compiled.toSupportSQLiteQuery())
            .map { rows -> rows.map { it.toAudioTrack() } }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeTracksById(smartPlaylistId: Long): Flow<List<AudioTrack>> =
        playlistDao.observeSmartPlaylistById(smartPlaylistId).flatMapLatest { entity ->
            if (entity == null) flowOf(emptyList()) else observeTracks(entity.toDomain())
        }

    suspend fun createSmartPlaylist(
        name: String,
        predicate: SmartPredicate,
        sortBy: SmartPlaylistSortBy = SmartPlaylistSortBy.PLAY_COUNT,
        sortDescending: Boolean = true,
        limitCount: Int? = null
    ): Long {
        val now = nowMsProvider()
        val entity = SmartPlaylistEntity(
            name = name.trim().ifEmpty { "Smart Playlist" },
            rulesJson = encodeRules(predicate),
            sortBy = sortBy.name,
            sortDescending = sortDescending,
            limitCount = limitCount?.takeIf { it > 0 },
            createdAt = now,
            updatedAt = now
        )
        return playlistDao.insertSmartPlaylist(entity)
    }

    suspend fun updateSmartPlaylist(
        id: Long,
        name: String,
        predicate: SmartPredicate,
        sortBy: SmartPlaylistSortBy,
        sortDescending: Boolean,
        limitCount: Int?,
        createdAt: Long
    ): Int {
        val entity = SmartPlaylistEntity(
            id = id,
            name = name.trim().ifEmpty { "Smart Playlist" },
            rulesJson = encodeRules(predicate),
            sortBy = sortBy.name,
            sortDescending = sortDescending,
            limitCount = limitCount?.takeIf { it > 0 },
            createdAt = createdAt,
            updatedAt = nowMsProvider()
        )
        return playlistDao.updateSmartPlaylist(entity)
    }

    suspend fun deleteSmartPlaylist(id: Long): Int = playlistDao.deleteSmartPlaylist(id)

    fun compileQuery(
        predicate: SmartPredicate,
        sortBy: SmartPlaylistSortBy = SmartPlaylistSortBy.PLAY_COUNT,
        sortDescending: Boolean = true,
        limitCount: Int? = null
    ): CompiledSmartQuery {
        val args = mutableListOf<Any>()
        val nowMs = nowMsProvider()
        val whereClause = compilePredicate(predicate, nowMs, args)
        val orderDirection = if (sortDescending) "DESC" else "ASC"
        val orderExpr = when (sortBy) {
            SmartPlaylistSortBy.PLAY_COUNT -> "COALESCE(h.playCount, 0)"
            SmartPlaylistSortBy.LAST_PLAYED -> "COALESCE(h.lastPlayed, 0)"
            SmartPlaylistSortBy.DATE_ADDED -> NORMALIZED_DATE_ADDED_SQL
            SmartPlaylistSortBy.TITLE -> "LOWER(c.title)"
            SmartPlaylistSortBy.ARTIST -> "LOWER(c.artist)"
            SmartPlaylistSortBy.ALBUM -> "LOWER(c.album)"
            SmartPlaylistSortBy.YEAR -> "CAST(COALESCE(NULLIF(TRIM(c.year), ''), '0') AS INTEGER)"
            SmartPlaylistSortBy.DURATION -> "c.durationMs"
        }

        val sqlBuilder = StringBuilder()
            .append(
                """
                WITH track_history AS (
                    SELECT
                        trackId,
                        COUNT(*) AS playCount,
                        MAX(timestamp) AS lastPlayed
                    FROM playback_history
                    GROUP BY trackId
                )
                SELECT c.*
                FROM cached_tracks c
                LEFT JOIN track_history h ON h.trackId = c.id
                LEFT JOIN artist_cache ac ON ac.artistName = LOWER(TRIM(c.artist))
                WHERE $whereClause
                ORDER BY $orderExpr $orderDirection, LOWER(c.title) ASC
                """.trimIndent()
            )

        if (limitCount != null && limitCount > 0) {
            sqlBuilder.append("\nLIMIT ?")
            args.add(limitCount)
        }

        return CompiledSmartQuery(sql = sqlBuilder.toString(), args = args)
    }

    private fun compilePredicate(
        predicate: SmartPredicate,
        nowMs: Long,
        args: MutableList<Any>
    ): String = when (predicate) {
        is SmartPredicate.And -> {
            if (predicate.predicates.isEmpty()) {
                "1 = 1"
            } else {
                predicate.predicates.joinToString(
                    separator = " AND ",
                    prefix = "(",
                    postfix = ")"
                ) { compilePredicate(it, nowMs, args) }
            }
        }
        is SmartPredicate.Or -> {
            if (predicate.predicates.isEmpty()) {
                "1 = 0"
            } else {
                predicate.predicates.joinToString(
                    separator = " OR ",
                    prefix = "(",
                    postfix = ")"
                ) { compilePredicate(it, nowMs, args) }
            }
        }
        is SmartPredicate.Not -> "NOT (${compilePredicate(predicate.predicate, nowMs, args)})"
        is SmartPredicate.PlayCount -> {
            args.add(predicate.count)
            "COALESCE(h.playCount, 0) ${predicate.op.sqlOperator} ?"
        }
        is SmartPredicate.LastPlayed -> compileLastPlayed(predicate.op, predicate.value, nowMs, args)
        is SmartPredicate.DateAdded -> compileDateAdded(predicate.op, predicate.value, nowMs, args)
        is SmartPredicate.Liked -> {
            args.add(if (predicate.isLiked) 1 else 0)
            "c.isLiked = ?"
        }
        is SmartPredicate.Artist -> compileText("c.artist", predicate.op, predicate.value, args)
        is SmartPredicate.Album -> compileText("c.album", predicate.op, predicate.value, args)
        is SmartPredicate.Title -> compileText("c.title", predicate.op, predicate.value, args)
        is SmartPredicate.Genre -> compileText("COALESCE(ac.genres, '')", predicate.op, predicate.value, args)
        is SmartPredicate.Year -> {
            args.add(predicate.year)
            "(c.year IS NOT NULL AND TRIM(c.year) != '' AND CAST(c.year AS INTEGER) > 0 AND CAST(c.year AS INTEGER) ${predicate.op.sqlOperator} ?)"
        }
        is SmartPredicate.Duration -> {
            args.add(predicate.durationMs)
            "c.durationMs ${predicate.op.sqlOperator} ?"
        }
        is SmartPredicate.Source -> compileSource(predicate.source, args)
    }

    private fun compileLastPlayed(
        op: DateOp,
        value: Long,
        nowMs: Long,
        args: MutableList<Any>
    ): String = when (op) {
        DateOp.WITHIN_LAST_DAYS -> {
            args.add(nowMs - value.coerceAtLeast(0L) * MILLIS_PER_DAY)
            "(h.lastPlayed IS NOT NULL AND h.lastPlayed >= ?)"
        }
        DateOp.NOT_IN_LAST_DAYS -> {
            args.add(nowMs - value.coerceAtLeast(0L) * MILLIS_PER_DAY)
            "(h.lastPlayed IS NULL OR h.lastPlayed < ?)"
        }
        DateOp.BEFORE_EPOCH_MS -> {
            args.add(value)
            "(h.lastPlayed IS NOT NULL AND h.lastPlayed < ?)"
        }
        DateOp.AFTER_EPOCH_MS -> {
            args.add(value)
            "(h.lastPlayed IS NOT NULL AND h.lastPlayed > ?)"
        }
        DateOp.NEVER -> "h.lastPlayed IS NULL"
    }

    private fun compileDateAdded(
        op: DateOp,
        value: Long,
        nowMs: Long,
        args: MutableList<Any>
    ): String = when (op) {
        DateOp.WITHIN_LAST_DAYS -> {
            args.add(nowMs - value.coerceAtLeast(0L) * MILLIS_PER_DAY)
            "$NORMALIZED_DATE_ADDED_SQL >= ?"
        }
        DateOp.NOT_IN_LAST_DAYS -> {
            args.add(nowMs - value.coerceAtLeast(0L) * MILLIS_PER_DAY)
            "$NORMALIZED_DATE_ADDED_SQL < ?"
        }
        DateOp.BEFORE_EPOCH_MS -> {
            args.add(value)
            "$NORMALIZED_DATE_ADDED_SQL < ?"
        }
        DateOp.AFTER_EPOCH_MS -> {
            args.add(value)
            "$NORMALIZED_DATE_ADDED_SQL > ?"
        }
        DateOp.NEVER -> "c.dateAdded <= 0"
    }

    private fun compileText(
        columnSql: String,
        op: TextOp,
        value: String,
        args: MutableList<Any>
    ): String {
        val lower = value.lowercase()
        return when (op) {
            TextOp.EQUALS -> {
                args.add(lower)
                "LOWER($columnSql) = ?"
            }
            TextOp.NOT_EQUALS -> {
                args.add(lower)
                "LOWER($columnSql) != ?"
            }
            TextOp.CONTAINS -> {
                args.add("%${escapeLike(lower)}%")
                "LOWER($columnSql) LIKE ? ESCAPE '\\'"
            }
            TextOp.NOT_CONTAINS -> {
                args.add("%${escapeLike(lower)}%")
                "LOWER($columnSql) NOT LIKE ? ESCAPE '\\'"
            }
            TextOp.STARTS_WITH -> {
                args.add("${escapeLike(lower)}%")
                "LOWER($columnSql) LIKE ? ESCAPE '\\'"
            }
        }
    }

    private fun compileSource(source: MediaSource, args: MutableList<Any>): String {
        return when (source) {
            MediaSource.NAVIDROME -> {
                args.add("navidrome\\_%")
                args.add("navidrome://%")
                args.add(MediaSource.NAVIDROME.name)
                "(c.id LIKE ? ESCAPE '\\' OR c.uriString LIKE ? ESCAPE '\\' OR EXISTS (SELECT 1 FROM playback_history phs WHERE phs.trackId = c.id AND phs.source = ?))"
            }
            MediaSource.SPOTIFY -> {
                args.add("spotify:%")
                args.add("spotify\\_%")
                args.add("spotify:%")
                args.add(MediaSource.SPOTIFY.name)
                "(c.id LIKE ? ESCAPE '\\' OR c.id LIKE ? ESCAPE '\\' OR c.uriString LIKE ? ESCAPE '\\' OR EXISTS (SELECT 1 FROM playback_history phs WHERE phs.trackId = c.id AND phs.source = ?))"
            }
            MediaSource.LOCAL -> {
                args.add("navidrome\\_%")
                args.add("spotify:%")
                args.add("spotify\\_%")
                args.add("navidrome://%")
                args.add("spotify:%")
                args.add(MediaSource.NAVIDROME.name)
                args.add(MediaSource.SPOTIFY.name)
                "(c.id NOT LIKE ? ESCAPE '\\' AND c.id NOT LIKE ? ESCAPE '\\' AND c.id NOT LIKE ? ESCAPE '\\' AND c.uriString NOT LIKE ? ESCAPE '\\' AND c.uriString NOT LIKE ? ESCAPE '\\' AND NOT EXISTS (SELECT 1 FROM playback_history phs WHERE phs.trackId = c.id AND phs.source IN (?, ?)))"
            }
        }
    }

    private fun escapeLike(value: String): String =
        value.replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")

    fun encodeRules(predicate: SmartPredicate): String = toJson(predicate).toString()

    fun decodeRules(json: String): SmartPredicate {
        val element = JsonParser.parseString(json)
        require(element.isJsonObject) { "Smart playlist rule JSON must be an object" }
        return fromJson(element.asJsonObject)
    }

    private fun toJson(predicate: SmartPredicate): JsonObject {
        val obj = JsonObject()
        when (predicate) {
            is SmartPredicate.And -> {
                obj.addProperty("type", TYPE_AND)
                obj.add("children", JsonArray().apply {
                    predicate.predicates.forEach { add(toJson(it)) }
                })
            }
            is SmartPredicate.Or -> {
                obj.addProperty("type", TYPE_OR)
                obj.add("children", JsonArray().apply {
                    predicate.predicates.forEach { add(toJson(it)) }
                })
            }
            is SmartPredicate.Not -> {
                obj.addProperty("type", TYPE_NOT)
                obj.add("child", toJson(predicate.predicate))
            }
            is SmartPredicate.PlayCount -> {
                obj.addProperty("type", TYPE_PLAY_COUNT)
                obj.addProperty("op", predicate.op.name)
                obj.addProperty("count", predicate.count)
            }
            is SmartPredicate.LastPlayed -> {
                obj.addProperty("type", TYPE_LAST_PLAYED)
                obj.addProperty("op", predicate.op.name)
                obj.addProperty("value", predicate.value)
            }
            is SmartPredicate.DateAdded -> {
                obj.addProperty("type", TYPE_DATE_ADDED)
                obj.addProperty("op", predicate.op.name)
                obj.addProperty("value", predicate.value)
            }
            is SmartPredicate.Liked -> {
                obj.addProperty("type", TYPE_LIKED)
                obj.addProperty("isLiked", predicate.isLiked)
            }
            is SmartPredicate.Artist -> {
                obj.addProperty("type", TYPE_ARTIST)
                obj.addProperty("op", predicate.op.name)
                obj.addProperty("value", predicate.value)
            }
            is SmartPredicate.Album -> {
                obj.addProperty("type", TYPE_ALBUM)
                obj.addProperty("op", predicate.op.name)
                obj.addProperty("value", predicate.value)
            }
            is SmartPredicate.Genre -> {
                obj.addProperty("type", TYPE_GENRE)
                obj.addProperty("op", predicate.op.name)
                obj.addProperty("value", predicate.value)
            }
            is SmartPredicate.Year -> {
                obj.addProperty("type", TYPE_YEAR)
                obj.addProperty("op", predicate.op.name)
                obj.addProperty("year", predicate.year)
            }
            is SmartPredicate.Duration -> {
                obj.addProperty("type", TYPE_DURATION)
                obj.addProperty("op", predicate.op.name)
                obj.addProperty("durationMs", predicate.durationMs)
            }
            is SmartPredicate.Source -> {
                obj.addProperty("type", TYPE_SOURCE)
                obj.addProperty("source", predicate.source.name)
            }
            is SmartPredicate.Title -> {
                obj.addProperty("type", TYPE_TITLE)
                obj.addProperty("op", predicate.op.name)
                obj.addProperty("value", predicate.value)
            }
        }
        return obj
    }

    private fun fromJson(obj: JsonObject): SmartPredicate {
        val type = obj.get("type")?.asString
            ?: throw IllegalArgumentException("Missing predicate type")
        return when (type) {
            TYPE_AND -> SmartPredicate.And(
                obj.getAsJsonArray("children")?.map { child: JsonElement ->
                    fromJson(child.asJsonObject)
                } ?: emptyList()
            )
            TYPE_OR -> SmartPredicate.Or(
                obj.getAsJsonArray("children")?.map { child: JsonElement ->
                    fromJson(child.asJsonObject)
                } ?: emptyList()
            )
            TYPE_NOT -> SmartPredicate.Not(fromJson(obj.getAsJsonObject("child")))
            TYPE_PLAY_COUNT -> SmartPredicate.PlayCount(
                op = NumberOp.valueOf(obj.get("op").asString),
                count = obj.get("count").asInt
            )
            TYPE_LAST_PLAYED -> SmartPredicate.LastPlayed(
                op = DateOp.valueOf(obj.get("op").asString),
                value = obj.get("value")?.asLong ?: 0L
            )
            TYPE_DATE_ADDED -> SmartPredicate.DateAdded(
                op = DateOp.valueOf(obj.get("op").asString),
                value = obj.get("value")?.asLong ?: 0L
            )
            TYPE_LIKED -> SmartPredicate.Liked(isLiked = obj.get("isLiked").asBoolean)
            TYPE_ARTIST -> SmartPredicate.Artist(
                op = TextOp.valueOf(obj.get("op").asString),
                value = obj.get("value").asString
            )
            TYPE_ALBUM -> SmartPredicate.Album(
                op = TextOp.valueOf(obj.get("op").asString),
                value = obj.get("value").asString
            )
            TYPE_GENRE -> SmartPredicate.Genre(
                op = TextOp.valueOf(obj.get("op").asString),
                value = obj.get("value").asString
            )
            TYPE_YEAR -> SmartPredicate.Year(
                op = NumberOp.valueOf(obj.get("op").asString),
                year = obj.get("year").asInt
            )
            TYPE_DURATION -> SmartPredicate.Duration(
                op = NumberOp.valueOf(obj.get("op").asString),
                durationMs = obj.get("durationMs").asLong
            )
            TYPE_SOURCE -> SmartPredicate.Source(
                source = MediaSource.valueOf(obj.get("source").asString)
            )
            TYPE_TITLE -> SmartPredicate.Title(
                op = TextOp.valueOf(obj.get("op").asString),
                value = obj.get("value").asString
            )
            else -> throw IllegalArgumentException("Unknown predicate type: $type")
        }
    }

    fun SmartPlaylistEntity.toDomain(): SmartPlaylist = SmartPlaylist(
        id = id,
        name = name,
        predicate = runCatching { decodeRules(rulesJson) }
            .getOrElse { SmartPredicate.And(emptyList()) },
        sortBy = runCatching { SmartPlaylistSortBy.valueOf(sortBy) }
            .getOrDefault(SmartPlaylistSortBy.PLAY_COUNT),
        sortDescending = sortDescending,
        limitCount = limitCount,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    fun starterPresets(): List<SmartPlaylistPreset> = listOf(
        SmartPlaylistPreset(
            name = "Liked & Forgotten",
            predicate = SmartPredicate.And(
                listOf(
                    SmartPredicate.Liked(true),
                    SmartPredicate.LastPlayed(DateOp.NOT_IN_LAST_DAYS, 90L)
                )
            ),
            sortBy = SmartPlaylistSortBy.DATE_ADDED,
            sortDescending = true
        ),
        SmartPlaylistPreset(
            name = "Recent Favorites",
            predicate = SmartPredicate.And(
                listOf(
                    SmartPredicate.PlayCount(NumberOp.GREATER_THAN, 3),
                    SmartPredicate.DateAdded(DateOp.WITHIN_LAST_DAYS, 30L)
                )
            ),
            sortBy = SmartPlaylistSortBy.PLAY_COUNT,
            sortDescending = true
        ),
        SmartPlaylistPreset(
            name = "Never Played",
            predicate = SmartPredicate.PlayCount(NumberOp.EQUALS, 0),
            sortBy = SmartPlaylistSortBy.DATE_ADDED,
            sortDescending = true
        ),
        SmartPlaylistPreset(
            name = "Heavy Rotation Top 50",
            predicate = SmartPredicate.PlayCount(NumberOp.GREATER_OR_EQUAL, 1),
            sortBy = SmartPlaylistSortBy.PLAY_COUNT,
            sortDescending = true,
            limitCount = 50
        )
    )

    companion object {
        const val MILLIS_PER_DAY = 86_400_000L

        private const val NORMALIZED_DATE_ADDED_SQL =
            "(CASE WHEN c.dateAdded > 10000000000 THEN c.dateAdded ELSE c.dateAdded * 1000 END)"

        private const val TYPE_AND = "AND"
        private const val TYPE_OR = "OR"
        private const val TYPE_NOT = "NOT"
        private const val TYPE_PLAY_COUNT = "PLAY_COUNT"
        private const val TYPE_LAST_PLAYED = "LAST_PLAYED"
        private const val TYPE_DATE_ADDED = "DATE_ADDED"
        private const val TYPE_LIKED = "LIKED"
        private const val TYPE_ARTIST = "ARTIST"
        private const val TYPE_ALBUM = "ALBUM"
        private const val TYPE_GENRE = "GENRE"
        private const val TYPE_YEAR = "YEAR"
        private const val TYPE_DURATION = "DURATION"
        private const val TYPE_SOURCE = "SOURCE"
        private const val TYPE_TITLE = "TITLE"

        internal fun forTesting(
            playlistDao: PlaylistDao,
            nowMsProvider: () -> Long
        ): SmartPlaylistEngine = SmartPlaylistEngine(playlistDao, nowMsProvider)
    }
}

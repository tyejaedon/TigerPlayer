package com.tigerplayer.engine.library

import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.tigerplayer.data.local.entity.toAudioTrack
import com.tigerplayer.data.model.AudioTrack
import com.tigerplayer.data.repository.AudioRepository
import com.tigerplayer.engine.FolderTreeBuilder
import com.tigerplayer.service.MediaItemConverter
import com.tigerplayer.utils.ArtistUtils
import kotlinx.coroutines.flow.firstOrNull
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Constants defining the standardized media browse hierarchy for TigerPlayer.
 */
object MediaBrowseConstants {
    const val ROOT_ID = "root"

    // Top-level categories
    const val CATEGORY_ALBUMS = "category_albums"
    const val CATEGORY_ARTISTS = "category_artists"
    const val CATEGORY_PLAYLISTS = "category_playlists"
    const val CATEGORY_FOLDERS = "category_folders"
    const val CATEGORY_HEAVY_ROTATION = "category_heavy_rotation"
    const val CATEGORY_DAY_LIST = "category_day_list"

    // Prefixes for dynamic container IDs
    const val PREFIX_ALBUM = "album:"
    const val PREFIX_ARTIST = "artist:"
    const val PREFIX_PLAYLIST = "playlist:"
    const val PREFIX_FOLDER = "folder:"
    const val PREFIX_TRACK = "track:"

    // Synthetic playlist IDs
    const val PLAYLIST_LIKED_SONGS = "playlist:-1"
    const val PLAYLIST_RECENTLY_ADDED = "playlist:-2"

    const val HEAVY_ROTATION_LOOKBACK_MS = 30L * 24L * 60L * 60L * 1000L
    const val DAYLIST_LOOKBACK_MS = 14L * 24L * 60L * 60L * 1000L
}

/**
 * Domain engine orchestrating the browsable media tree for external controllers
 * (Android Auto, Wear OS, Google Assistant, and widgets).
 *
 * Implements a 2-level hierarchical navigation model:
 * [Root] -> [Albums | Artists | Playlists | Folders | Heavy Rotation | Day List] -> [Tracks]
 */
@Singleton
class MediaBrowseTree @Inject constructor(
    private val audioRepository: AudioRepository
) {

    companion object {
        private const val TAG = "MediaBrowseTree"
    }

    /**
     * Builds and returns the root browsable [MediaItem].
     */
    fun getRootItem(): MediaItem {
        return MediaItem.Builder()
            .setMediaId(MediaBrowseConstants.ROOT_ID)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("TigerPlayer")
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build()
            )
            .build()
    }

    /**
     * Returns the 6 core browsable categories exposed at the root level.
     */
    fun getRootCategories(): List<MediaItem> {
        return listOf(
            buildCategoryItem(
                id = MediaBrowseConstants.CATEGORY_ALBUMS,
                title = "Albums",
                subtitle = "Browse music by album",
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS,
                isPlayable = false
            ),
            buildCategoryItem(
                id = MediaBrowseConstants.CATEGORY_ARTISTS,
                title = "Artists",
                subtitle = "Browse music by artist",
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS,
                isPlayable = false
            ),
            buildCategoryItem(
                id = MediaBrowseConstants.CATEGORY_PLAYLISTS,
                title = "Playlists",
                subtitle = "Playlists and favorites",
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS,
                isPlayable = false
            ),
            buildCategoryItem(
                id = MediaBrowseConstants.CATEGORY_FOLDERS,
                title = "Folders",
                subtitle = "Storage directory tree",
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                isPlayable = false
            ),
            buildCategoryItem(
                id = MediaBrowseConstants.CATEGORY_HEAVY_ROTATION,
                title = "Heavy Rotation",
                subtitle = "Most frequently played tracks",
                mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST,
                isPlayable = true
            ),
            buildCategoryItem(
                id = MediaBrowseConstants.CATEGORY_DAY_LIST,
                title = "Day List",
                subtitle = "Curated flow for right now",
                mediaType = MediaMetadata.MEDIA_TYPE_PLAYLIST,
                isPlayable = true
            )
        )
    }

    /**
     * Resolves the children of [parentId] with optional 0-based [page] and [pageSize] pagination.
     */
    suspend fun getChildren(
        parentId: String,
        page: Int = 0,
        pageSize: Int = Int.MAX_VALUE
    ): List<MediaItem> {
        val allItems = when {
            parentId == MediaBrowseConstants.ROOT_ID -> getRootCategories()

            parentId == MediaBrowseConstants.CATEGORY_ALBUMS -> getAlbumCategories()

            parentId.startsWith(MediaBrowseConstants.PREFIX_ALBUM) -> {
                val albumName = parentId.removePrefix(MediaBrowseConstants.PREFIX_ALBUM)
                getTracksForAlbum(albumName)
            }

            parentId == MediaBrowseConstants.CATEGORY_ARTISTS -> getArtistCategories()

            parentId.startsWith(MediaBrowseConstants.PREFIX_ARTIST) -> {
                val artistName = parentId.removePrefix(MediaBrowseConstants.PREFIX_ARTIST)
                getTracksForArtist(artistName)
            }

            parentId == MediaBrowseConstants.CATEGORY_PLAYLISTS -> getPlaylistCategories()

            parentId.startsWith(MediaBrowseConstants.PREFIX_PLAYLIST) -> {
                val playlistId = parentId.removePrefix(MediaBrowseConstants.PREFIX_PLAYLIST)
                getTracksForPlaylistId(playlistId)
            }

            parentId == MediaBrowseConstants.CATEGORY_FOLDERS -> getFolderContents(path = "")

            parentId.startsWith(MediaBrowseConstants.PREFIX_FOLDER) -> {
                val folderPath = parentId.removePrefix(MediaBrowseConstants.PREFIX_FOLDER)
                getFolderContents(path = folderPath)
            }

            parentId == MediaBrowseConstants.CATEGORY_HEAVY_ROTATION -> getHeavyRotationTracks()

            parentId == MediaBrowseConstants.CATEGORY_DAY_LIST -> getDayListTracks()

            else -> emptyList()
        }

        return allItems.paginate(page, pageSize)
    }

    /**
     * Retrieves a single [MediaItem] by its [mediaId].
     */
    suspend fun getItem(mediaId: String): MediaItem? {
        return when {
            mediaId == MediaBrowseConstants.ROOT_ID -> getRootItem()

            mediaId == MediaBrowseConstants.CATEGORY_ALBUMS ||
            mediaId == MediaBrowseConstants.CATEGORY_ARTISTS ||
            mediaId == MediaBrowseConstants.CATEGORY_PLAYLISTS ||
            mediaId == MediaBrowseConstants.CATEGORY_FOLDERS ||
            mediaId == MediaBrowseConstants.CATEGORY_HEAVY_ROTATION ||
            mediaId == MediaBrowseConstants.CATEGORY_DAY_LIST -> {
                getRootCategories().find { it.mediaId == mediaId }
            }

            mediaId.startsWith(MediaBrowseConstants.PREFIX_ALBUM) -> {
                val albumName = mediaId.removePrefix(MediaBrowseConstants.PREFIX_ALBUM)
                getAlbumCategories().find { it.mediaId == mediaId }
                    ?: getAllTracks().find { it.album.equals(albumName, ignoreCase = true) }?.let { track ->
                        MediaItem.Builder()
                            .setMediaId(mediaId)
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(track.album)
                                    .setArtist(track.artist)
                                    .setArtworkUri(track.artworkUri)
                                    .setIsBrowsable(true)
                                    .setIsPlayable(true)
                                    .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
                                    .build()
                            )
                            .build()
                    }
            }

            mediaId.startsWith(MediaBrowseConstants.PREFIX_ARTIST) -> {
                val artistName = mediaId.removePrefix(MediaBrowseConstants.PREFIX_ARTIST)
                getArtistCategories().find { it.mediaId == mediaId }
                    ?: getAllTracks().find {
                        ArtistUtils.getBaseArtist(it.artist).equals(artistName, ignoreCase = true) ||
                        it.artist.equals(artistName, ignoreCase = true)
                    }?.let { track ->
                        MediaItem.Builder()
                            .setMediaId(mediaId)
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(ArtistUtils.getBaseArtist(track.artist))
                                    .setArtworkUri(track.artworkUri)
                                    .setIsBrowsable(true)
                                    .setIsPlayable(true)
                                    .setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
                                    .build()
                            )
                            .build()
                    }
            }

            mediaId.startsWith(MediaBrowseConstants.PREFIX_PLAYLIST) -> {
                getPlaylistCategories().find { it.mediaId == mediaId }
            }

            mediaId.startsWith(MediaBrowseConstants.PREFIX_FOLDER) -> {
                val folderPath = mediaId.removePrefix(MediaBrowseConstants.PREFIX_FOLDER)
                val displayName = folderPath.substringAfterLast('/', folderPath)
                MediaItem.Builder()
                    .setMediaId(mediaId)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(displayName)
                            .setSubtitle(folderPath)
                            .setIsBrowsable(true)
                            .setIsPlayable(false)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                            .build()
                    )
                    .build()
            }

            else -> {
                val trackId = mediaId.removePrefix(MediaBrowseConstants.PREFIX_TRACK)
                val track = getAllTracks().find { it.id == trackId || it.id == mediaId }
                    ?: audioRepository.getCachedTrack(trackId)?.toAudioTrack()
                track?.let { MediaItemConverter.toMediaItem(it) }
            }
        }
    }

    /**
     * Performs title, artist, and album substring search across the library with pagination.
     */
    suspend fun search(
        query: String,
        page: Int = 0,
        pageSize: Int = Int.MAX_VALUE
    ): List<MediaItem> {
        if (query.isBlank()) return emptyList()
        val trimmed = query.trim()
        val matched = getAllTracks().filter { track ->
            track.title.contains(trimmed, ignoreCase = true) ||
            track.artist.contains(trimmed, ignoreCase = true) ||
            track.album.contains(trimmed, ignoreCase = true)
        }
        return matched.map { MediaItemConverter.toMediaItem(it) }.paginate(page, pageSize)
    }

    /**
     * Returns total matching item count for a search query.
     */
    suspend fun searchCount(query: String): Int {
        if (query.isBlank()) return 0
        val trimmed = query.trim()
        return getAllTracks().count { track ->
            track.title.contains(trimmed, ignoreCase = true) ||
            track.artist.contains(trimmed, ignoreCase = true) ||
            track.album.contains(trimmed, ignoreCase = true)
        }
    }

    /**
     * Resolves voice search queries from Google Assistant or Android Auto into playable tracks.
     * Handles exact title match, fuzzy substring match, artist match, and generic "play music" intents.
     */
    suspend fun resolveVoiceQuery(query: String): List<MediaItem> {
        val tracks = getAllTracks()
        if (tracks.isEmpty()) return emptyList()

        val cleanQuery = query.trim()
        if (cleanQuery.isBlank() || isGenericPlayIntent(cleanQuery)) {
            return tracks.shuffled().take(50).map { MediaItemConverter.toMediaItem(it) }
        }

        // 1. Exact track title
        val exactTitle = tracks.filter { it.title.equals(cleanQuery, ignoreCase = true) }
        if (exactTitle.isNotEmpty()) {
            return exactTitle.map { MediaItemConverter.toMediaItem(it) }
        }

        // 2. Track title contains query
        val titleMatch = tracks.filter { it.title.contains(cleanQuery, ignoreCase = true) }
        if (titleMatch.isNotEmpty()) {
            return titleMatch.map { MediaItemConverter.toMediaItem(it) }
        }

        // 3. Artist match
        val artistMatch = tracks.filter {
            ArtistUtils.getBaseArtist(it.artist).contains(cleanQuery, ignoreCase = true) ||
            it.artist.contains(cleanQuery, ignoreCase = true)
        }
        if (artistMatch.isNotEmpty()) {
            return artistMatch.map { MediaItemConverter.toMediaItem(it) }
        }

        // 4. Album match
        val albumMatch = tracks.filter { it.album.contains(cleanQuery, ignoreCase = true) }
        if (albumMatch.isNotEmpty()) {
            return albumMatch.map { MediaItemConverter.toMediaItem(it) }
        }

        // 5. Tokenized multi-word search
        val tokens = cleanQuery.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.size > 1) {
            val tokenMatch = tracks.filter { track ->
                tokens.all { token ->
                    track.title.contains(token, ignoreCase = true) ||
                    track.artist.contains(token, ignoreCase = true) ||
                    track.album.contains(token, ignoreCase = true)
                }
            }
            if (tokenMatch.isNotEmpty()) {
                return tokenMatch.map { MediaItemConverter.toMediaItem(it) }
            }
        }

        // Fallback: return top library tracks rather than failing silently
        return tracks.take(25).map { MediaItemConverter.toMediaItem(it) }
    }

    /**
     * Expands a container [MediaItem] (album, playlist, category, or folder) into its
     * individual playable child [MediaItem]s. If the item is already a single track,
     * it resolves and returns that single track.
     */
    suspend fun expandItemToTracks(item: MediaItem): List<MediaItem> {
        val mediaId = item.mediaId
        return when {
            mediaId == MediaBrowseConstants.CATEGORY_HEAVY_ROTATION -> getHeavyRotationTracks()
            mediaId == MediaBrowseConstants.CATEGORY_DAY_LIST -> getDayListTracks()
            mediaId.startsWith(MediaBrowseConstants.PREFIX_ALBUM) -> {
                val albumName = mediaId.removePrefix(MediaBrowseConstants.PREFIX_ALBUM)
                getTracksForAlbum(albumName)
            }
            mediaId.startsWith(MediaBrowseConstants.PREFIX_ARTIST) -> {
                val artistName = mediaId.removePrefix(MediaBrowseConstants.PREFIX_ARTIST)
                getTracksForArtist(artistName)
            }
            mediaId.startsWith(MediaBrowseConstants.PREFIX_PLAYLIST) -> {
                val playlistId = mediaId.removePrefix(MediaBrowseConstants.PREFIX_PLAYLIST)
                getTracksForPlaylistId(playlistId)
            }
            mediaId.startsWith(MediaBrowseConstants.PREFIX_FOLDER) -> {
                val folderPath = mediaId.removePrefix(MediaBrowseConstants.PREFIX_FOLDER)
                val allTracks = getAllTracks()
                val index = FolderTreeBuilder.index(allTracks)
                index[folderPath]?.tracks?.map { MediaItemConverter.toMediaItem(it) } ?: emptyList()
            }
            mediaId.startsWith(MediaBrowseConstants.PREFIX_TRACK) -> {
                val trackId = mediaId.removePrefix(MediaBrowseConstants.PREFIX_TRACK)
                val track = getAllTracks().find { it.id == trackId }
                    ?: audioRepository.getCachedTrack(trackId)?.toAudioTrack()
                track?.let { listOf(MediaItemConverter.toMediaItem(it)) } ?: emptyList()
            }
            else -> {
                val track = getAllTracks().find { it.id == mediaId }
                    ?: audioRepository.getCachedTrack(mediaId)?.toAudioTrack()
                if (track != null) {
                    listOf(MediaItemConverter.toMediaItem(track))
                } else {
                    emptyList()
                }
            }
        }
    }

    suspend fun getAllTracks(): List<AudioTrack> {
        val cached = audioRepository.getCachedLocalTracks().firstOrNull() ?: emptyList()
        if (cached.isNotEmpty()) return cached
        return audioRepository.getLocalTracks(forceRefresh = false).firstOrNull() ?: emptyList()
    }

    private suspend fun getAlbumCategories(): List<MediaItem> {
        val allTracks = getAllTracks()
        return allTracks
            .filter { it.album.isNotBlank() }
            .groupBy { it.album.trim().lowercase() }
            .map { (_, tracks) ->
                val first = tracks.first()
                val albumName = first.album
                MediaItem.Builder()
                    .setMediaId("${MediaBrowseConstants.PREFIX_ALBUM}$albumName")
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(albumName)
                            .setArtist(first.artist)
                            .setArtworkUri(first.artworkUri)
                            .setTotalTrackCount(tracks.size)
                            .setIsBrowsable(true)
                            .setIsPlayable(true)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
                            .build()
                    )
                    .build()
            }
            .sortedBy { it.mediaMetadata.title?.toString()?.lowercase() ?: "" }
    }

    private suspend fun getTracksForAlbum(albumName: String): List<MediaItem> {
        return getAllTracks()
            .filter { it.album.equals(albumName, ignoreCase = true) }
            .sortedWith(compareBy({ it.trackNumber }, { it.title.lowercase() }))
            .map { MediaItemConverter.toMediaItem(it) }
    }

    private suspend fun getArtistCategories(): List<MediaItem> {
        val allTracks = getAllTracks()
        return allTracks
            .filter { it.artist.isNotBlank() }
            .groupBy { ArtistUtils.getBaseArtist(it.artist).trim().lowercase() }
            .map { (_, tracks) ->
                val first = tracks.first()
                val baseArtist = ArtistUtils.getBaseArtist(first.artist)
                MediaItem.Builder()
                    .setMediaId("${MediaBrowseConstants.PREFIX_ARTIST}$baseArtist")
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(baseArtist)
                            .setSubtitle("${tracks.size} ${if (tracks.size == 1) "track" else "tracks"}")
                            .setArtworkUri(first.artworkUri)
                            .setIsBrowsable(true)
                            .setIsPlayable(true)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
                            .build()
                    )
                    .build()
            }
            .sortedBy { it.mediaMetadata.title?.toString()?.lowercase() ?: "" }
    }

    private suspend fun getTracksForArtist(artistName: String): List<MediaItem> {
        return getAllTracks()
            .filter {
                ArtistUtils.getBaseArtist(it.artist).equals(artistName, ignoreCase = true) ||
                it.artist.equals(artistName, ignoreCase = true)
            }
            .sortedWith(compareBy({ it.album.lowercase() }, { it.trackNumber }, { it.title.lowercase() }))
            .map { MediaItemConverter.toMediaItem(it) }
    }

    private suspend fun getPlaylistCategories(): List<MediaItem> {
        val list = mutableListOf<MediaItem>()

        // 1. Liked Songs
        list.add(
            MediaItem.Builder()
                .setMediaId(MediaBrowseConstants.PLAYLIST_LIKED_SONGS)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Liked Songs")
                        .setSubtitle("Your favorite tracks")
                        .setIsBrowsable(true)
                        .setIsPlayable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                        .build()
                )
                .build()
        )

        // 2. Recently Added
        list.add(
            MediaItem.Builder()
                .setMediaId(MediaBrowseConstants.PLAYLIST_RECENTLY_ADDED)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Recently Added")
                        .setSubtitle("Recently added music")
                        .setIsBrowsable(true)
                        .setIsPlayable(true)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                        .build()
                )
                .build()
        )

        // 3. User playlists
        val customPlaylists = audioRepository.getCustomPlaylists().firstOrNull() ?: emptyList()
        customPlaylists.forEach { playlist ->
            list.add(
                MediaItem.Builder()
                    .setMediaId("${MediaBrowseConstants.PREFIX_PLAYLIST}${playlist.id}")
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(playlist.name)
                            .setSubtitle("${playlist.trackCount} tracks")
                            .setArtworkUri(playlist.artworkUri?.toUri())
                            .setIsBrowsable(true)
                            .setIsPlayable(true)
                            .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                            .build()
                    )
                    .build()
            )
        }

        return list
    }

    private suspend fun getTracksForPlaylistId(playlistId: String): List<MediaItem> {
        val tracks = when (playlistId) {
            "-1" -> getAllTracks().filter { it.isLiked }
            "-2" -> getAllTracks().sortedByDescending { it.dateAdded }.take(50)
            else -> {
                val numericId = playlistId.toLongOrNull() ?: return emptyList()
                audioRepository.getTracksForPlaylist(numericId).firstOrNull() ?: emptyList()
            }
        }
        return tracks.map { MediaItemConverter.toMediaItem(it) }
    }

    private suspend fun getFolderContents(path: String): List<MediaItem> {
        val allTracks = getAllTracks()
        val index = FolderTreeBuilder.index(allTracks)
        val contents = index[path] ?: return emptyList()

        val subfolders = contents.subfolders.map { subfolderPath ->
            val displayName = subfolderPath.substringAfterLast('/', subfolderPath)
            MediaItem.Builder()
                .setMediaId("${MediaBrowseConstants.PREFIX_FOLDER}$subfolderPath")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(displayName)
                        .setSubtitle(subfolderPath)
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .build()
                )
                .build()
        }

        val directTracks = contents.tracks.map { MediaItemConverter.toMediaItem(it) }
        return subfolders + directTracks
    }

    private suspend fun getHeavyRotationTracks(): List<MediaItem> {
        val allTracks = getAllTracks()
        val since = System.currentTimeMillis() - MediaBrowseConstants.HEAVY_ROTATION_LOOKBACK_MS
        val stats = audioRepository.getHeavyRotation(since).firstOrNull() ?: emptyList()

        val resolved = stats.mapNotNull { stat ->
            allTracks.find { it.id == stat.trackId }
        }

        val finalTracks = if (resolved.isNotEmpty()) resolved else allTracks.take(15)
        return finalTracks.map { MediaItemConverter.toMediaItem(it) }
    }

    private suspend fun getDayListTracks(): List<MediaItem> {
        val allTracks = getAllTracks()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val segment = when (hour) {
            in 5..11 -> "MORNING"
            in 12..16 -> "AFTERNOON"
            in 17..21 -> "EVENING"
            else -> "NIGHT"
        }
        val since = System.currentTimeMillis() - MediaBrowseConstants.DAYLIST_LOOKBACK_MS
        val daylist = audioRepository.getNeonDaylistTracks(segment = segment, sinceMillis = since, limit = 20)
            .firstOrNull() ?: emptyList()

        val finalTracks = if (daylist.isNotEmpty()) daylist else allTracks.shuffled().take(20)
        return finalTracks.map { MediaItemConverter.toMediaItem(it) }
    }

    private fun isGenericPlayIntent(query: String): Boolean {
        val lower = query.lowercase().trim()
        return lower in listOf(
            "play",
            "play music",
            "play songs",
            "play track",
            "play something",
            "shuffle",
            "shuffle music",
            "music",
            "songs"
        )
    }

    private fun buildCategoryItem(
        id: String,
        title: String,
        subtitle: String,
        mediaType: Int,
        isPlayable: Boolean
    ): MediaItem {
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setIsBrowsable(true)
                    .setIsPlayable(isPlayable)
                    .setMediaType(mediaType)
                    .build()
            )
            .build()
    }

    private fun <T> List<T>.paginate(page: Int, pageSize: Int): List<T> {
        if (pageSize <= 0 || page < 0) return this
        val fromIndex = page * pageSize
        if (fromIndex >= size) return emptyList()
        val toIndex = minOf(fromIndex + pageSize, size)
        return subList(fromIndex, toIndex)
    }
}

package com.example.tigerplayer.constellation

import com.example.tigerplayer.data.repository.AudioRepository
import com.example.tigerplayer.data.repository.HistoryRepository
import com.example.tigerplayer.engine.MetadataEngine
import com.example.tigerplayer.utils.ArtistUtils
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConstellationDataEngine @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val audioRepository: AudioRepository,
    private val metadataEngine: MetadataEngine
) {

    suspend fun buildGraph(): ConstellationGraph {
        // 🔥 THE FIX: Safely extract the List snapshot from the Flow
        val topArtists = historyRepository.getTopArtists(0L, 40).firstOrNull() ?: emptyList()
        val topTracks = historyRepository.getTopTracks(0L, 50).firstOrNull() ?: emptyList()
        val allTracks = audioRepository.getLocalTracks().firstOrNull() ?: emptyList()
        val artistLore = metadataEngine.artistDetails.value

        val trackPlayMap = topTracks.associateBy { it.trackId }
        val tracksByArtist = allTracks.groupBy {
            ArtistUtils.getBaseArtist(it.artist).lowercase().trim()
        }

        val nodes = mutableListOf<GraphNode>()
        val edges = mutableListOf<GraphEdge>()

        // --- 1. BUILD ARTISTS & ALBUMS ---
        topArtists.forEach { artist ->
            val normalizedName = ArtistUtils.getBaseArtist(artist.artistName).lowercase().trim()
            val hdImage = artistLore[normalizedName]?.imageUrl ?: artist.imageUrl
            val artistId = safeId("artist", artist.artistName)

            nodes.add(
                GraphNode(
                    id = artistId,
                    label = artist.artistName,
                    type = NodeType.ARTIST,
                    playCount = artist.playCount,
                    imageUrl = hdImage
                )
            )

            val artistTracks = tracksByArtist[normalizedName] ?: emptyList()
            val albums = artistTracks.groupBy { it.album }.filter { it.key.isNotBlank() && !it.key.contains("unknown", true) }

            albums.forEach { (album, tracks) ->
                val albumId = safeId("album", artist.artistName + album)

                nodes.add(
                    GraphNode(
                        id = albumId,
                        label = album,
                        type = NodeType.ALBUM,
                        playCount = tracks.sumOf { trackPlayMap[it.id]?.playCount ?: 0 },
                        imageUrl = tracks.firstOrNull()?.artworkUri?.toString(),
                        parentId = artistId
                    )
                )

                edges.add(GraphEdge(artistId, albumId, 0.4f))
            }
        }

        // --- 2. BUILD ROGUE TRACKS ---
        val rogueCoreId = "core_rogue"
        nodes.add(
            GraphNode(
                id = rogueCoreId,
                label = "LIFETIME CHANTS",
                type = NodeType.GALAXY_CORE,
                playCount = 0,
                imageUrl = null
            )
        )

        topTracks.forEach { track ->
            val trackId = safeId("rogue", track.trackId)
            nodes.add(
                GraphNode(
                    id = trackId,
                    label = track.title,
                    type = NodeType.TRACK,
                    playCount = track.playCount,
                    imageUrl = track.imageUrl,
                    parentId = rogueCoreId
                )
            )
            edges.add(GraphEdge(rogueCoreId, trackId, 0.1f))
        }

        return ConstellationGraph(nodes, edges)
    }

    private fun safeId(type: String, key: String): String {
        return "${type}_${key.lowercase().replace("[^a-z0-9]".toRegex(), "_")}"
    }
}
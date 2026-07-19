package com.example.tigerplayer.constellation

import com.example.tigerplayer.data.repository.AudioRepository
import com.example.tigerplayer.data.repository.HistoryRepository
import com.example.tigerplayer.engine.MetadataEngine
import com.example.tigerplayer.utils.ArtistUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log10
import kotlin.math.max

@Singleton
class ConstellationDataEngine @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val audioRepository: AudioRepository,
    private val metadataEngine: MetadataEngine
) {
    private val refreshNonce = MutableStateFlow(0L)

    /**
     * 🔥 THE FIX: Returns a Flow instead of a one-shot suspend function.
     * This combines history data with the live MetadataEngine details.
     */
    fun getGraphFlow(): Flow<ConstellationGraph> {
        val topStatsFlow = historyRepository.getTopArtists(0L, 50).combine(
            historyRepository.getTopTracks(0L, 80)
        ) { topArtists, topTracks ->
            topArtists to topTracks
        }

        val trackDataFlow = audioRepository.getLocalTracks().combine(
            historyRepository.getAllTracksStats()
        ) { allTracks, globalTracks ->
            allTracks to globalTracks
        }

        return topStatsFlow.combine(trackDataFlow) { topStats, trackData ->
            topStats to trackData
        }.combine(metadataEngine.artistDetails) { combinedData, artistLore ->
            val (topStats, trackData) = combinedData
            val (topArtists, topTracks) = topStats
            val (allTracks, globalTracks) = trackData

            val trackPlayMap = globalTracks.associateBy { it.trackId }
            val tracksByArtist = allTracks.groupBy {
                ArtistUtils.getBaseArtist(it.artist).lowercase().trim()
            }

            val nodes = mutableListOf<GraphNode>()
            val edges = mutableListOf<GraphEdge>()

            /* -----------------------------------
               ☀️ 1. GALAXY CORE
            ----------------------------------- */
            val coreId = "core_galaxy"
            nodes.add(GraphNode(
                id = coreId,
                label = "NEURAL GALAXY CORE",
                type = NodeType.GALAXY_CORE,
                playCount = topTracks.sumOf { it.playCount },
                imageUrl = null,
                importance = 1.0f,
                audioEnergyBias = 1.0f
            ))

        /* -----------------------------------
           🌠 2. ARTIST STARS (Reactive Mapping)
        ----------------------------------- */
            val artistNodes = topArtists.map { artist ->
                val normalized = ArtistUtils.getBaseArtist(artist.artistName).lowercase().trim()

                // Prioritize live profile/lore art, then fallback to most recent local artwork.
                val lore = artistLore[normalized]
                val artistTracks = tracksByArtist[normalized].orEmpty()
                val imageUrl = lore?.imageUrl?.takeIf { it.isNotBlank() }
                    ?: artistTracks.firstOrNull()?.artworkUri?.toString()?.takeIf { it.isNotBlank() }

                val mass = log10(max(1f, artist.playCount.toFloat() + 1f))

                GraphNode(
                    id = safeId("artist", artist.artistName),
                    label = artist.artistName,
                    type = NodeType.ARTIST,
                    playCount = artist.playCount,
                    imageUrl = imageUrl,
                    parentId = coreId,
                    importance = mass,
                    audioEnergyBias = mass * 0.6f
                )
            }
            nodes.addAll(artistNodes)

        /* -----------------------------------
           🪐 3. ALBUM ORBITAL SYSTEMS
        ----------------------------------- */
            artistNodes.forEach { artist ->
                val artistKey = ArtistUtils.getBaseArtist(artist.label).lowercase().trim()
                val artistTracks = tracksByArtist[artistKey] ?: emptyList()
                val albums = artistTracks.groupBy { it.album }
                    .filter { it.key.isNotBlank() && !it.key.contains("unknown", true) }

                albums.forEach { (albumName, tracks) ->
                    val albumPlay = tracks.sumOf { trackPlayMap[it.id]?.playCount ?: 0 }
                    val albumMass = log10(max(1.0, albumPlay.toDouble() + 1)).toFloat()
                    val albumId = safeId("album", artist.label + albumName)

                    nodes.add(GraphNode(
                        id = albumId,
                        label = albumName,
                        type = NodeType.ALBUM,
                        playCount = albumPlay,
                        imageUrl = tracks.firstOrNull()?.artworkUri?.toString(),
                        parentId = artist.id,
                        importance = albumMass,
                        audioEnergyBias = albumMass * 0.4f
                    ))

                    edges.add(GraphEdge(sourceId = artist.id, targetId = albumId, strength = albumMass))
                }
            }

        /* -----------------------------------
           ☄️ 4. TRACK DEBRIS FIELD
        ----------------------------------- */
            val rogueCoreId = "core_rogue"
            nodes.add(GraphNode(
                id = rogueCoreId,
                label = "LIFETIME CHANTS",
                type = NodeType.GALAXY_CORE,
                playCount = globalTracks.sumOf { it.playCount },
                imageUrl = null,
                importance = 0.3f,
                audioEnergyBias = 0.8f
            ))

            topTracks.forEach { track ->
                val mass = log10(max(1f, track.playCount.toFloat() + 1f)) * 0.5f
                val trackNodeId = safeId("track", track.trackId)

                nodes.add(GraphNode(
                    id = trackNodeId,
                    label = track.title,
                    type = NodeType.TRACK,
                    playCount = track.playCount,
                    imageUrl = track.imageUrl,
                    parentId = rogueCoreId,
                    importance = mass,
                    audioEnergyBias = mass * 0.8f
                ))

                edges.add(GraphEdge(sourceId = rogueCoreId, targetId = trackNodeId, strength = mass))
            }

        // Normalization
            val maxImportance = nodes.maxOfOrNull { it.importance } ?: 1f
            val normalizedNodes = nodes.map { it.copy(importance = it.importance / maxImportance) }

            ConstellationGraph(
                nodes = normalizedNodes,
                edges = edges,
                density = 1f,
                seed = System.currentTimeMillis()
            )
        }.combine(refreshNonce) { graph, nonce ->
            graph.copy(seed = graph.seed + nonce)
        }
    }

    suspend fun refreshGraphData() {
        val topArtists = historyRepository.getTopArtists(0L, 50).first()
        val localTracks = audioRepository.getLocalTracks().first()
        withContext(Dispatchers.IO) {
            val topArtistNames = topArtists.map { it.artistName }
            if (topArtistNames.isNotEmpty()) {
                metadataEngine.forceRefreshArtistProfiles(topArtistNames)
            } else {
                metadataEngine.preSeedArtistCache(localTracks)
            }
        }
        refreshNonce.update { it + 1L }
    }

    private fun safeId(type: String, key: String): String {
        return "${type}_${key.lowercase()
            .replace("[^a-z0-9]".toRegex(), "_")
            .replace("_+".toRegex(), "_")
            .trim('_')}"
    }
}

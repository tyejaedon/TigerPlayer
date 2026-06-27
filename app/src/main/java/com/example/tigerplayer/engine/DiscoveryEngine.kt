package com.example.tigerplayer.engine

import android.net.Uri
import com.example.tigerplayer.data.local.entity.PlaybackHistoryEntity
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.repository.MediaDataRepository
import com.example.tigerplayer.utils.ArtistUtils
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DiscoveryEngine(
    private val mediaDataRepository: MediaDataRepository
) {

    suspend fun generateDaylist(
        playbackHistory: List<PlaybackHistoryEntity>,
        allAvailableTracks: List<AudioTrack>,
        density: ListeningDensitySnapshot,
        limit: Int,
        seed: Long
    ): List<AudioTrack> = withContext(Dispatchers.Default) {
        if (allAvailableTracks.isEmpty()) return@withContext emptyList()

        if (!density.isPersonalizedReady) {
            return@withContext mediaDataRepository.resolveGlobalTrendsetters(
                allAvailableTracks = allAvailableTracks,
                limit = limit,
                seed = seed
            )
        }

        val profile = buildProfile(playbackHistory, allAvailableTracks)
        mediaDataRepository.rankTracksForDiscovery(
            candidates = allAvailableTracks,
            topGenres = profile.topGenres,
            artistFamiliarity = profile.artistAffinity,
            heardGenres = profile.heardGenres,
            seed = seed,
            discoveryWeightMultiplier = 1.0,
            limit = limit
        )
    }

    suspend fun generateVault(
        playbackHistory: List<PlaybackHistoryEntity>,
        allAvailableTracks: List<AudioTrack>,
        density: ListeningDensitySnapshot,
        limit: Int,
        seed: Long
    ): List<AudioTrack> = withContext(Dispatchers.Default) {
        if (allAvailableTracks.isEmpty()) return@withContext emptyList()

        if (!density.isPersonalizedReady) {
            return@withContext mediaDataRepository.resolveGlobalTrendsetters(
                allAvailableTracks = allAvailableTracks,
                limit = limit,
                seed = seed xor 0x5F3759DFL
            )
        }

        val profile = buildProfile(playbackHistory, allAvailableTracks)
        val playCountByTrackId = playbackHistory
            .groupingBy { it.trackId }
            .eachCount()

        val unseenCandidates = allAvailableTracks.filter { track ->
            (playCountByTrackId[track.id] ?: 0) == 0
        }

        val candidatePool = if (unseenCandidates.size >= limit) {
            unseenCandidates
        } else {
            allAvailableTracks.sortedBy { track -> playCountByTrackId[track.id] ?: 0 }
        }

        mediaDataRepository.rankTracksForDiscovery(
            candidates = candidatePool,
            topGenres = profile.topGenres,
            artistFamiliarity = profile.artistAffinity,
            heardGenres = profile.heardGenres,
            seed = seed xor 0x1BADC0DEL,
            discoveryWeightMultiplier = 1.25,
            limit = limit
        )
    }

    private fun buildProfile(
        playbackHistory: List<PlaybackHistoryEntity>,
        allAvailableTracks: List<AudioTrack>
    ): DiscoveryProfile {
        val trackById = allAvailableTracks.associateBy { it.id }

        val genreWeights = mutableMapOf<String, Double>()
        val artistWeights = mutableMapOf<String, Double>()
        val heardGenres = mutableSetOf<String>()

        playbackHistory.forEach { entry ->
            val track = trackById[entry.trackId]
            val trackGenres = if (track != null) {
                mediaDataRepository.inferGenresForTrack(track)
            } else {
                // Gracefully infer genres even when a historical track is no longer in the local cache.
                mediaDataRepository.inferGenresForTrack(
                    AudioTrack(
                        id = entry.trackId,
                        title = entry.title,
                        artist = entry.artist,
                        album = entry.album,
                        uri = Uri.EMPTY,
                        artworkUri = Uri.EMPTY,
                        durationMs = 0L,
                        mimeType = "audio/unknown"
                    )
                )
            }

            val weight = (entry.durationListenedMs / 60_000.0).coerceAtLeast(1.0)
            trackGenres.forEach { genre ->
                genreWeights[genre] = (genreWeights[genre] ?: 0.0) + weight
                heardGenres += genre
            }

            val artist = track?.artist ?: entry.artist
            val normalizedArtist = ArtistUtils.getBaseArtist(artist).trim().lowercase()
            if (normalizedArtist.isNotEmpty()) {
                artistWeights[normalizedArtist] = (artistWeights[normalizedArtist] ?: 0.0) + weight
            }
        }

        return DiscoveryProfile(
            topGenres = genreWeights
                .toList()
                .sortedByDescending { (_, value) -> value }
                .take(5)
                .toMap(),
            artistAffinity = artistWeights,
            heardGenres = heardGenres
        )
    }

    fun currentSegment(now: Calendar = Calendar.getInstance()): String {
        val hour = now.get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> "MORNING"
            in 12..16 -> "AFTERNOON"
            in 17..21 -> "EVENING"
            else -> "NIGHT"
        }
    }

    private data class DiscoveryProfile(
        val topGenres: Map<String, Double>,
        val artistAffinity: Map<String, Double>,
        val heardGenres: Set<String>
    )
}



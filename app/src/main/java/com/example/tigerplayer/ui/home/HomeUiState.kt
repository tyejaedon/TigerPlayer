package com.example.tigerplayer.ui.home

import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.ui.player.StatItem

/**
 * THE VANGUARD STATS
 * A high-level overview of the user's musical journey and the scale of their archives.
 */
data class UserStatistics(
    val listeningTimeToday: String = "0h 0m",
    val topArtistThisWeek: String = "Searching...",
    val listeningTimeTodayMs: Long = 0L,

    // THE GENERALIZED FIX: Replaces losslessTrackCount
    // This represents the total number of "Grimoires" (Tracks) in the local library.
    val totalTracksCount: Int = 0,

    val totalListeningTimeHours: Int = 0,

    // 🔥 NEW: Top Artists mapped directly into the home state for the Constellation & Analytics
    val topArtists: List<StatItem> = emptyList()
)

/**
 * THE HOME UI STATE
 * The single source of truth for the dashboard.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val statistics: UserStatistics = UserStatistics(),

    // Carousel Data
    val discoverTracks: List<AudioTrack> = emptyList(),
    val recentlyPlayedTracks: List<AudioTrack> = emptyList(),
    val recommendedTracks: List<AudioTrack> = emptyList(),

    val errorMessage: String? = null
)
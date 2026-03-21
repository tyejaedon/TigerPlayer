package com.example.tigerplayer.ui.home

import com.example.tigerplayer.data.model.AudioTrack

// 1. A dedicated model for your dashboard statistics
data class UserStatistics(
    val listeningTimeToday: String = "2h 14m", // Placeholder
    val topAlbumThisWeek: String = "Wild Hunt (Original Soundtrack)", // Placeholder
    val topArtistThisWeek: String = "Marcin Przybyłowicz", // Placeholder
    val topGenre: String = "Soundtrack", // Placeholder
    val losslessTrackCount: Int = 0,
    val totalListeningTimeHours: Int = 120 // Lifetime placeholder
)




// 2. The Single Source of Truth for the Home Screen
data class HomeUiState(
    val isLoading: Boolean = true,
    val statistics: UserStatistics = UserStatistics(),

    // The lists that will feed into your CarouselComponents.kt
    val discoverTracks: List<AudioTrack> = emptyList(),
    val recentlyPlayedTracks: List<AudioTrack> = emptyList(),
    val recommendedTracks: List<AudioTrack> = emptyList(),

    val errorMessage: String? = null
)
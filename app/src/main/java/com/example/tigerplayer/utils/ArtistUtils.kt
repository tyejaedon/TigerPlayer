package com.example.tigerplayer.utils

object ArtistUtils {
    // Shared Regex: looks for " ft.", " feat.", " &", "/", or ","
    private val collaboratorRegex = Regex("(?i)\\s+(ft\\.?|feat\\.?|&|/)|,")

    /**
     * Strips away collaborators to find the primary artist.
     * "Drake & 21 Savage" -> "Drake"
     */
    fun getBaseArtist(fullName: String?): String {
        if (fullName.isNullOrBlank()) return "Unknown Witcher"
        return fullName.split(collaboratorRegex).firstOrNull()?.trim() ?: fullName.trim()
    }
}
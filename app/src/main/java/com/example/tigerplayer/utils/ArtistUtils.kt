package com.example.tigerplayer.utils

object ArtistUtils {
    // 1. Handles words with surrounding spaces: " ft. ", " feat. ", " featuring ", " & ", " with "
    // 2. Handles punctuation with OR without spaces: ",", " , ", "/", " / ", ";", " ; "
    private val collaboratorRegex = Regex("(?i)(\\s+(ft\\.?|feat\\.?|featuring|&|with)\\s+)|(\\s*[,/;]\\s*)")

    /**
     * Strips away collaborators to find the primary artist.
     * "Drake & 21 Savage" -> "Drake"
     * "Daft Punk, Pharrell Williams" -> "Daft Punk"
     * "Eminem featuring Rihanna" -> "Eminem"
     * "Witcher/Jaskier" -> "Witcher"
     * "Hans Zimmer; Lisa Gerrard" -> "Hans Zimmer"
     */
    fun getBaseArtist(fullName: String?): String {
        if (fullName.isNullOrBlank()) return "Unknown Witcher"
        return fullName.split(collaboratorRegex).firstOrNull()?.trim() ?: fullName.trim()
    }
}
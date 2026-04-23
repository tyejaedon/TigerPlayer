package com.example.tigerplayer.utils

object ArtistUtils {
    /**
     * THE RAZOR'S EDGE:
     * 1. Matches common keywords: ft, feat, featuring, with, vs, x
     * 2. Matches punctuation: & , / ; |
     * 3. Matches features inside parentheses: (feat. ...) or [feat. ...]
     */
    private val collaboratorRegex = Regex(
        "(?i)" + // Case-insensitive
                "(\\s+((ft|feat|featuring|with|vs|x)\\.?|&)\\s+)" + // Words/Symbols with spaces
                "|(\\s*[,/;|]\\s*)" + // Standard punctuation separators
                "|(\\s*[\\[(](ft|feat|featuring|with)\\.?\\s+.*[\\])])" // (feat. ...) or [feat. ...]
    )

    /**
     * Strips away collaborators to find the primary artist for the Archives.
     * * TEST CASES:
     * "Drake & 21 Savage" -> "Drake"
     * "Justin Bieber (feat. Ludacris)" -> "Justin Bieber"
     * "Skrillex x Fred again.." -> "Skrillex"
     * "Witcher/Jaskier" -> "Witcher"
     * "Daft Punk; Pharrell Williams" -> "Daft Punk"
     */
    fun getBaseArtist(fullName: String?): String {
        if (fullName.isNullOrBlank()) return "Unknown Witcher"

        // Step 1: Split by our refined collaborator regex
        val primary = fullName.split(collaboratorRegex).firstOrNull()?.trim()

        // Step 2: Clean up any trailing parentheses left by partial regex matches
        return primary?.removeSuffix("(")?.removeSuffix("[")?.trim() ?: fullName.trim()
    }
}

sealed class Resource<T>(val data: T? = null, val message: String? = null) {
    class Success<T>(data: T) : Resource<T>(data)
    class Error<T>(message: String, data: T? = null) : Resource<T>(data, message)
    class Loading<T>(data: T? = null) : Resource<T>(data)
}
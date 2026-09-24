package com.tigerplayer.utils

object MusicMetadataSearch {
    private val descriptiveBracketRegex = Regex(
        "\\s*[(\\[](Explicit|Remastered|Deluxe|Live|O.S.T.|Original Motion Picture Soundtrack|Bonus Track|Mono|Stereo|Re-Recorded)[^\\])]*[\\])]",
        RegexOption.IGNORE_CASE
    )
    private val trailingEditionRegex = Regex("\\s+-\\s+.*$")
    private val genericAlbumNames = setOf("spotify", "unknown album")

    /**
     * Strips metadata clutter that commonly harms remote lookups while preserving the core title.
     */
    fun cleanSearchTerm(term: String?): String {
        if (term.isNullOrBlank()) return ""
        return term
            .replace(descriptiveBracketRegex, "")
            .replace(trailingEditionRegex, "")
            .trim()
    }

    /**
     * Removes placeholder album labels that are useful to the UI but harmful to remote lookups.
     */
    fun meaningfulAlbumName(album: String?): String? {
        val cleaned = cleanSearchTerm(album)
        if (cleaned.isBlank()) return null
        return cleaned.takeUnless { it.lowercase() in genericAlbumNames }
    }
}

package com.tigerplayer.engine

import com.tigerplayer.data.model.AudioTrack

/**
 * Builds a lazily-navigable folder index from a flat list of local tracks (issue #50).
 *
 * Rather than materializing a full in-memory tree up front, [index] produces a flat lookup of
 * every directory (including synthetic ancestors that hold no tracks of their own, so an
 * intermediate folder still appears as a browsable node) to its direct children. The UI drills
 * down one level at a time by looking up [FolderContents.subfolders] on demand, which keeps this
 * cheap even for very large libraries.
 *
 * The synthetic root is keyed by the empty string `""`.
 */
object FolderTreeBuilder {

    /**
     * Represents the contents of a specific folder path in the library hierarchy.
     *
     * @property path The absolute or relative storage path of this folder.
     * @property name The display name of the folder (or "Root" for the synthetic root).
     * @property subfolders List of direct child directory paths contained within this folder.
     * @property tracks List of audio tracks residing directly in this folder.
     */
    data class FolderContents(
        val path: String,
        val name: String,
        val subfolders: List<String>,
        val tracks: List<AudioTrack>
    )

    /** Synthetic root path key used for top-level directory grouping. */
    private const val ROOT = ""

    /**
     * Indexes a flat list of audio tracks into a hierarchical map of folder contents.
     *
     * @param tracks Flat list of [AudioTrack] instances representing the local library.
     * @return A map associating each directory path string with its [FolderContents].
     */
    fun index(tracks: List<AudioTrack>): Map<String, FolderContents> {
        // Group tracks by their parent directory path, filtering out blank paths.
        val tracksByDir = tracks
            .filter { !it.path.isNullOrBlank() }
            .groupBy { it.path!!.substringBeforeLast('/', missingDelimiterValue = ROOT) }

        // Collect all distinct directories and their ancestor paths.
        val allDirs = sortedSetOf(ROOT)
        tracksByDir.keys.forEach { dir ->
            var current = dir
            allDirs.add(current)
            while (current.isNotEmpty()) {
                current = current.substringBeforeLast('/', missingDelimiterValue = ROOT)
                allDirs.add(current)
            }
        }

        // Map each directory to its immediate child subdirectories.
        val childrenByParent = allDirs
            .groupBy { it.substringBeforeLast('/', missingDelimiterValue = ROOT) }
            .mapValues { (parent, children) -> children.filter { it != parent } }

        return allDirs.associateWith { dir ->
            FolderContents(
                path = dir,
                name = if (dir.isEmpty()) "Root" else dir.substringAfterLast('/'),
                subfolders = (childrenByParent[dir] ?: emptyList())
                    .sortedBy { it.substringAfterLast('/').lowercase() },
                tracks = (tracksByDir[dir] ?: emptyList()).sortedBy { it.title.lowercase() }
            )
        }
    }
}

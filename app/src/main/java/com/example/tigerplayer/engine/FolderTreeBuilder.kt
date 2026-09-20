package com.example.tigerplayer.engine

import com.example.tigerplayer.data.model.AudioTrack

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

    data class FolderContents(
        val path: String,
        val name: String,
        val subfolders: List<String>,
        val tracks: List<AudioTrack>
    )

    private const val ROOT = ""

    fun index(tracks: List<AudioTrack>): Map<String, FolderContents> {
        val tracksByDir = tracks
            .filter { !it.path.isNullOrBlank() }
            .groupBy { it.path!!.substringBeforeLast('/', missingDelimiterValue = ROOT) }

        val allDirs = sortedSetOf(ROOT)
        tracksByDir.keys.forEach { dir ->
            var current = dir
            allDirs.add(current)
            while (current.isNotEmpty()) {
                current = current.substringBeforeLast('/', missingDelimiterValue = ROOT)
                allDirs.add(current)
            }
        }

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

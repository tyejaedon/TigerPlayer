package com.example.tigerplayer.data.repository

import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.model.TrackFingerprint

/**
 * THE SINGLE SOURCE OF DIFF TRUTH (issue #49)
 *
 * Compares a freshly scanned library against what is already cached, using cheap identity -
 * `(id, dateModified)` - instead of a deep structural comparison across every field of every
 * track. This is the only place that decision is made; both [AudioRepository.getLocalTracks] and
 * [AudioRepository.getLocalTracksWithProgress] route through it so the delta logic can't drift
 * apart between entry points again.
 */
object LibraryCacheDiffer {

    data class Diff(
        val upserts: List<AudioTrack>,
        val removedIds: Set<String>
    ) {
        val hasChanges: Boolean get() = upserts.isNotEmpty() || removedIds.isNotEmpty()
    }

    /**
     * @param forceUpsertAll When true, every scanned track is treated as changed regardless of
     * its fingerprint (used for an explicit user-triggered rescan), while removals are still
     * computed correctly against the real cache instead of being skipped.
     */
    fun diff(
        cached: List<TrackFingerprint>,
        scanned: List<AudioTrack>,
        forceUpsertAll: Boolean = false
    ): Diff {
        val cachedDateModifiedById = cached.associate { it.id to it.dateModified }
        val scannedIds = HashSet<String>(scanned.size)

        val upserts = scanned.filter { track ->
            scannedIds.add(track.id)
            forceUpsertAll || cachedDateModifiedById[track.id] != track.dateModified
        }

        val removedIds = cachedDateModifiedById.keys - scannedIds

        return Diff(upserts, removedIds)
    }
}

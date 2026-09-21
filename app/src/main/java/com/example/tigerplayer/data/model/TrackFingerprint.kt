package com.tigerplayer.data.model

/**
 * Cheap identity used for library-cache diffing (issue #49).
 *
 * Diffing is done on `(id, dateModified)` only - never on a full [AudioTrack] / `CachedTrackEntity`
 * deep equality - so a cold start against a large library costs one lightweight query instead of
 * materializing and comparing every field of every track.
 */
data class TrackFingerprint(
    val id: String,
    val dateModified: Long
)

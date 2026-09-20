package com.example.tigerplayer.data.repository

import android.net.Uri
import com.example.tigerplayer.data.model.AudioTrack
import com.example.tigerplayer.data.model.TrackFingerprint
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Coverage for issue #49 / the "Library cache diffing" gap called out in the testing guidelines:
 * add, remove, modify, and no-change must each produce exactly the delta that keeps the cache
 * accurate - never a full-table rewrite.
 */
class LibraryCacheDifferTest {

    private fun track(id: String, dateModified: Long) = AudioTrack(
        id = id,
        title = "Title $id",
        artist = "Artist",
        album = "Album",
        uri = mockk<Uri>(relaxed = true),
        artworkUri = mockk<Uri>(relaxed = true),
        durationMs = 200_000L,
        mimeType = "audio/flac",
        isLocal = true,
        dateModified = dateModified
    )

    @Test
    fun `a track present only in the scan is upserted as new`() {
        val diff = LibraryCacheDiffer.diff(
            cached = emptyList(),
            scanned = listOf(track("a", dateModified = 100L))
        )

        assertEquals(listOf("a"), diff.upserts.map { it.id })
        assertTrue("nothing should be removed", diff.removedIds.isEmpty())
        assertTrue(diff.hasChanges)
    }

    @Test
    fun `a track present only in the cache is removed`() {
        val diff = LibraryCacheDiffer.diff(
            cached = listOf(TrackFingerprint(id = "a", dateModified = 100L)),
            scanned = emptyList()
        )

        assertTrue("nothing should be upserted", diff.upserts.isEmpty())
        assertEquals(setOf("a"), diff.removedIds)
        assertTrue(diff.hasChanges)
    }

    @Test
    fun `a track whose dateModified changed is upserted, not treated as new or removed`() {
        val diff = LibraryCacheDiffer.diff(
            cached = listOf(TrackFingerprint(id = "a", dateModified = 100L)),
            scanned = listOf(track("a", dateModified = 200L))
        )

        assertEquals(listOf("a"), diff.upserts.map { it.id })
        assertTrue("a changed row must not also be reported as removed", diff.removedIds.isEmpty())
    }

    @Test
    fun `a track with an unchanged fingerprint produces no delta`() {
        val diff = LibraryCacheDiffer.diff(
            cached = listOf(TrackFingerprint(id = "a", dateModified = 100L)),
            scanned = listOf(track("a", dateModified = 100L))
        )

        assertTrue("unchanged rows must not be rewritten", diff.upserts.isEmpty())
        assertTrue(diff.removedIds.isEmpty())
        assertTrue("a no-op diff must report no changes", !diff.hasChanges)
    }

    @Test
    fun `a mixed scan produces exactly the affected rows - never a full rewrite`() {
        val cached = listOf(
            TrackFingerprint(id = "unchanged", dateModified = 100L),
            TrackFingerprint(id = "modified", dateModified = 100L),
            TrackFingerprint(id = "deleted", dateModified = 100L)
        )
        val scanned = listOf(
            track("unchanged", dateModified = 100L),
            track("modified", dateModified = 999L),
            track("new", dateModified = 50L)
        )

        val diff = LibraryCacheDiffer.diff(cached, scanned)

        assertEquals(setOf("modified", "new"), diff.upserts.map { it.id }.toSet())
        assertEquals(setOf("deleted"), diff.removedIds)
    }

    @Test
    fun `forceUpsertAll rewrites every scanned row but still computes real removals`() {
        val cached = listOf(
            TrackFingerprint(id = "unchanged", dateModified = 100L),
            TrackFingerprint(id = "deleted", dateModified = 100L)
        )
        val scanned = listOf(track("unchanged", dateModified = 100L))

        val diff = LibraryCacheDiffer.diff(cached, scanned, forceUpsertAll = true)

        assertEquals(listOf("unchanged"), diff.upserts.map { it.id })
        assertEquals(setOf("deleted"), diff.removedIds)
    }
}

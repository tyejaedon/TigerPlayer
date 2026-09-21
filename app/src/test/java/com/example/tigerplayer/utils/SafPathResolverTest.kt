package com.tigerplayer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Coverage for issue #50's SAF-URI-to-path convention: `primary:` maps to the primary shared
 * storage volume, other volume ids map to `/storage/<volumeId>`, and malformed document ids
 * resolve to `null` rather than throwing.
 */
class SafPathResolverTest {

    @Test
    fun `a primary volume root document id resolves to the shared storage root`() {
        assertEquals(
            "/storage/emulated/0",
            SafPathResolver.resolveFromDocumentId("primary:")
        )
    }

    @Test
    fun `a primary volume subfolder document id resolves under the shared storage root`() {
        assertEquals(
            "/storage/emulated/0/Music/Tiger",
            SafPathResolver.resolveFromDocumentId("primary:Music/Tiger")
        )
    }

    @Test
    fun `a non-primary volume id resolves under storage with its raw volume id`() {
        assertEquals(
            "/storage/1234-5678/Music",
            SafPathResolver.resolveFromDocumentId("1234-5678:Music")
        )
    }

    @Test
    fun `a leading or trailing slash on the relative path is trimmed`() {
        assertEquals(
            "/storage/emulated/0/Music",
            SafPathResolver.resolveFromDocumentId("primary:/Music/")
        )
    }

    @Test
    fun `a document id with no colon separator cannot be resolved`() {
        assertNull(SafPathResolver.resolveFromDocumentId("not-a-valid-document-id"))
    }
}

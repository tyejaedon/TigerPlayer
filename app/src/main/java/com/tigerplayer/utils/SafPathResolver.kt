package com.tigerplayer.utils

import android.net.Uri
import android.provider.DocumentsContract

/**
 * Best-effort mapping from a SAF tree URI to an absolute filesystem path (issue #50).
 *
 * There is no officially supported reverse mapping from a `content://` SAF URI to a `/storage/...`
 * path, but the Android storage stack follows a well-known convention: a tree document id looks
 * like `"<volumeId>:<relative/path>"`, where `primary` always refers to the primary shared
 * storage volume (`/storage/emulated/0`) and any other volume id conventionally maps to
 * `/storage/<volumeId>`. This holds on AOSP and all mainstream OEM ROMs, which is why this is the
 * same approach used by other OSS players (see Auxio's `StorageManagerCompat`).
 *
 * The string-parsing core is kept pure/testable; only [resolvePath] touches the Android framework
 * (`DocumentsContract`), which plain JUnit cannot exercise without Robolectric.
 */
object SafPathResolver {

    /**
     * Pure core: turns a SAF tree/document id (e.g. `"primary:Music/MyAlbum"`) into a best-effort
     * absolute path, or `null` if [documentId] doesn't follow the expected `"volume:path"` shape.
     */
    fun resolveFromDocumentId(documentId: String): String? {
        val colonIndex = documentId.indexOf(':')
        if (colonIndex < 0) return null

        val volumeId = documentId.substring(0, colonIndex)
        val relativePath = documentId.substring(colonIndex + 1).trim('/')
        val volumeRoot = if (volumeId == "primary") "/storage/emulated/0" else "/storage/$volumeId"

        return if (relativePath.isEmpty()) volumeRoot else "$volumeRoot/$relativePath"
    }

    /** Thin Android-dependent wrapper around [resolveFromDocumentId]. */
    fun resolvePath(treeUri: Uri): String? {
        val documentId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (e: Exception) {
            null
        } ?: return null

        return resolveFromDocumentId(documentId)
    }
}

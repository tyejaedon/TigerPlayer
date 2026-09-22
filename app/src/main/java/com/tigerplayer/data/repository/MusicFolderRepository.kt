package com.tigerplayer.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.tigerplayer.data.local.dao.MusicFolderDao
import com.tigerplayer.data.local.entity.MusicFolderEntity
import com.tigerplayer.data.model.MusicFolder
import com.tigerplayer.utils.SafPathResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the persisted set of user-selected music directories (issue #50): "include" roots that
 * [com.tigerplayer.data.source.SafFolderScanner] walks for tracks, and "exclude" entries
 * that must be hidden everywhere. Handles taking/releasing the persistable SAF URI permission so
 * granted access survives reboots.
 */
@Singleton
class MusicFolderRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val musicFolderDao: MusicFolderDao
) {
    companion object {
        private const val TAG = "MusicFolderRepository"
    }

    fun getFolders(): Flow<List<MusicFolder>> =
        musicFolderDao.getAll().map { entities -> entities.map { it.toDomainModel() } }

    /**
     * Persists [treeUri] as either an "include" root (scanned for tracks) or an "exclude" entry.
     * Returns false if the persistable permission grant fails, in which case nothing is saved.
     */
    suspend fun addFolder(treeUri: Uri, isExcluded: Boolean): Boolean {
        return try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            val displayName = try {
                DocumentFile.fromTreeUri(context, treeUri)?.name
            } catch (e: Exception) {
                null
            } ?: treeUri.lastPathSegment ?: treeUri.toString()

            musicFolderDao.insert(
                MusicFolderEntity(
                    uriString = treeUri.toString(),
                    displayName = displayName,
                    path = SafPathResolver.resolvePath(treeUri).orEmpty(),
                    isExcluded = isExcluded
                )
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add music folder: ${e.message}")
            false
        }
    }

    suspend fun removeFolder(uriString: String) {
        musicFolderDao.deleteByUri(uriString)
        try {
            context.contentResolver.releasePersistableUriPermission(
                uriString.toUri(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            // Permission grant may already be gone (e.g. re-granted elsewhere) - not fatal, the
            // folder row is removed either way.
            Log.w(TAG, "Failed to release URI permission for $uriString: ${e.message}")
        }
    }

    /** Included ("root") folders that the SAF scanner should walk for tracks. */
    suspend fun getIncludedFoldersSync(): List<MusicFolder> =
        musicFolderDao.getAllSync().filterNot { it.isExcluded }.map { it.toDomainModel() }

    /** Resolved absolute paths that must be hidden from scan, search and playback. */
    suspend fun getExcludedPathsSync(): Set<String> =
        musicFolderDao.getAllSync()
            .filter { it.isExcluded && it.path.isNotBlank() }
            .map { it.path }
            .toSet()

    private fun MusicFolderEntity.toDomainModel() = MusicFolder(
        uriString = uriString,
        displayName = displayName,
        path = path,
        isExcluded = isExcluded,
        addedAt = addedAt
    )
}

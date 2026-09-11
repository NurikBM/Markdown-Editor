package com.markdown.editor.data.saf

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import com.markdown.editor.core.logger.Logger
import com.markdown.editor.data.local.dao.DocumentDao
import com.markdown.editor.domain.error.DomainError
import com.markdown.editor.domain.error.asException
import androidx.core.net.toUri

/**
 * Storage Access Framework coordinator enforcing Android persistent grant quotas
 * and maintaining an LRU permission eviction policy (maximum 120 active grants).
 */
class SafTreeCoordinator(
    private val contentResolver: ContentResolver,
    private val documentDao: DocumentDao,
    private val logger: Logger
) {

    companion object {
        const val MAX_PERSISTED_GRANTS_THRESHOLD = 120
        const val GRANT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }

    /**
     * Acquires persistable permission for [treeUri], evicting LRU grants if near threshold.
     */
    suspend fun takePersistableGrant(treeUri: Uri, documentId: String): Result<Unit> {
        return try {
            enforceLruEvictionIfNeeded()

            contentResolver.takePersistableUriPermission(treeUri, GRANT_FLAGS)
            logger.i("SafTreeCoordinator", "Acquired persistable URI permission for: $treeUri")
            Result.success(Unit)
        } catch (e: SecurityException) {
            logger.e("SafTreeCoordinator", "SecurityException acquiring grant for $treeUri", e)
            documentDao.markAsUnlinked(documentId)
            Result.failure(DomainError.Storage.PermissionRevoked(treeUri.toString()).asException())
        } catch (e: Exception) {
            logger.e("SafTreeCoordinator", "Error taking persistable grant", e)
            Result.failure(e)
        }
    }

    /**
     * Checks if persisted grants count exceeds threshold and evicts least recently accessed.
     */
    suspend fun enforceLruEvictionIfNeeded() {
        val activeGrantsCount = contentResolver.persistedUriPermissions.size
        logger.d("SafTreeCoordinator", "Current persisted URI permissions count: $activeGrantsCount")

        if (activeGrantsCount >= MAX_PERSISTED_GRANTS_THRESHOLD) {
            val lruDocument = documentDao.getLeastRecentlyUsedTreeGrant()
            if (lruDocument?.treeUri != null) {
                val uriToRelease = lruDocument.treeUri.toUri()
                try {
                    contentResolver.releasePersistableUriPermission(uriToRelease, GRANT_FLAGS)
                    documentDao.markAsUnlinked(lruDocument.documentId)
                    logger.w("SafTreeCoordinator", "Evicted LRU persisted URI grant: $uriToRelease for document: ${lruDocument.documentId}")
                } catch (e: SecurityException) {
                    logger.e("SafTreeCoordinator", "Failed releasing grant for $uriToRelease", e)
                    documentDao.markAsUnlinked(lruDocument.documentId)
                }
            }
        }
    }

    /**
     * Releases persistable URI permission explicitly.
     */
    suspend fun releaseGrant(treeUri: Uri, documentId: String): Result<Unit> {
        return try {
            contentResolver.releasePersistableUriPermission(treeUri, GRANT_FLAGS)
            documentDao.markAsUnlinked(documentId)
            Result.success(Unit)
        } catch (e: Exception) {
            logger.e("SafTreeCoordinator", "Error releasing grant for $treeUri", e)
            Result.failure(e)
        }
    }
}

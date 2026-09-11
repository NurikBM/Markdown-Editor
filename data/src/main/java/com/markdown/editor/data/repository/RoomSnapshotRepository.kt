package com.markdown.editor.data.repository

import com.markdown.editor.core.dispatcher.DispatcherProvider
import com.markdown.editor.data.local.dao.SnapshotDao
import com.markdown.editor.data.local.entity.SnapshotEntity
import com.markdown.editor.domain.model.BlockId
import com.markdown.editor.domain.model.DocumentSnapshot
import com.markdown.editor.domain.repository.SnapshotRepository
import kotlinx.coroutines.withContext

/**
 * Room-backed persistence repository for Document and Block snapshots used in undo/redo history.
 */
class RoomSnapshotRepository(
    private val snapshotDao: SnapshotDao,
    private val dispatcherProvider: DispatcherProvider
) : SnapshotRepository {

    override suspend fun recordSnapshot(snapshot: DocumentSnapshot): Result<Unit> = withContext(dispatcherProvider.io) {
        try {
            val entity = SnapshotEntity(
                versionId = snapshot.versionId,
                documentId = snapshot.documentId,
                blockId = snapshot.blockId.value,
                timestamp = snapshot.timestamp,
                forwardDiff = snapshot.forwardDiff,
                reverseDiff = snapshot.reverseDiff
            )
            snapshotDao.insertSnapshot(entity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getLatestSnapshot(
        documentId: String,
        blockId: BlockId
    ): DocumentSnapshot? = withContext(dispatcherProvider.io) {
        val entity = snapshotDao.getLatestSnapshot(documentId, blockId.value) ?: return@withContext null
        DocumentSnapshot(
            versionId = entity.versionId,
            documentId = entity.documentId,
            blockId = BlockId(entity.blockId),
            timestamp = entity.timestamp,
            forwardDiff = entity.forwardDiff,
            reverseDiff = entity.reverseDiff
        )
    }

    override suspend fun getSnapshotsForDocument(
        documentId: String
    ): List<DocumentSnapshot> = withContext(dispatcherProvider.io) {
        snapshotDao.getSnapshotsForDocument(documentId).map { entity ->
            DocumentSnapshot(
                versionId = entity.versionId,
                documentId = entity.documentId,
                blockId = BlockId(entity.blockId),
                timestamp = entity.timestamp,
                forwardDiff = entity.forwardDiff,
                reverseDiff = entity.reverseDiff
            )
        }
    }

    override suspend fun pruneSnapshots(
        documentId: String,
        maxCount: Int
    ): Result<Unit> = withContext(dispatcherProvider.io) {
        try {
            snapshotDao.pruneOldSnapshots(documentId, maxCount)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}


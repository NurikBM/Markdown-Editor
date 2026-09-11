package com.markdown.editor.domain.repository

import com.markdown.editor.domain.model.BlockId
import com.markdown.editor.domain.model.DocumentSnapshot

interface SnapshotRepository {
    suspend fun recordSnapshot(snapshot: DocumentSnapshot): Result<Unit>
    suspend fun getLatestSnapshot(documentId: String, blockId: BlockId): DocumentSnapshot?
    suspend fun getSnapshotsForDocument(documentId: String): List<DocumentSnapshot>
    suspend fun pruneSnapshots(documentId: String, maxCount: Int = 100): Result<Unit>
}


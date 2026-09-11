package com.markdown.editor.domain.model

import java.util.UUID

/**
 * Snapshot representing a version delta for undo/redo block state.
 */
data class DocumentSnapshot(
    val versionId: String = UUID.randomUUID().toString(),
    val documentId: String,
    val blockId: BlockId,
    val timestamp: Long = System.currentTimeMillis(),
    val forwardDiff: String,
    val reverseDiff: String
)


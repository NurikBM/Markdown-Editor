package com.markdown.editor.domain.repository

import com.markdown.editor.domain.model.DocumentMetadata
import com.markdown.editor.domain.model.MarkdownDocument
import kotlinx.coroutines.flow.Flow

interface MarkdownRepository {
    fun observeDocument(id: String): Flow<MarkdownDocument?>
    fun observeAllMetadata(): Flow<List<DocumentMetadata>>
    suspend fun getDocument(id: String): Result<MarkdownDocument>
    suspend fun saveDocument(document: MarkdownDocument, metadata: DocumentMetadata? = null): Result<Unit>
    suspend fun deleteDocument(id: String): Result<Unit>
    suspend fun updateLastAccessed(id: String, timestamp: Long = System.currentTimeMillis()): Result<Unit>
    suspend fun updateLockStatus(id: String, isLocked: Boolean): Result<Unit>
}


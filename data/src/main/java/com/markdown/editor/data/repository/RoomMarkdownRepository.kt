package com.markdown.editor.data.repository

import com.markdown.editor.core.dispatcher.DispatcherProvider
import com.markdown.editor.data.local.dao.BlockDao
import com.markdown.editor.data.local.dao.DocumentDao
import com.markdown.editor.data.local.entity.BlockEntity
import com.markdown.editor.data.local.entity.DocumentMetadataEntity
import com.markdown.editor.domain.error.DomainError
import com.markdown.editor.domain.error.asException
import com.markdown.editor.domain.model.BlockId
import com.markdown.editor.domain.model.BlockType
import com.markdown.editor.domain.model.DocumentMetadata
import com.markdown.editor.domain.model.MarkdownDocument
import com.markdown.editor.domain.parser.MarkdownBlockParser
import com.markdown.editor.domain.repository.MarkdownRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Room-backed persistence repository for Markdown documents and metadata.
 */
class RoomMarkdownRepository(
    private val documentDao: DocumentDao,
    private val blockDao: BlockDao,
    private val parser: MarkdownBlockParser,
    private val dispatcherProvider: DispatcherProvider
) : MarkdownRepository {

    override fun observeDocument(id: String): Flow<MarkdownDocument?> {
        return combine(
            documentDao.observeById(id),
            blockDao.observeBlocksForDocument(id)
        ) { metadata, blockEntities ->
            if (metadata == null) {
                null
            } else {
                val domainBlocks = blockEntities.map { entity ->
                    parser.parseBlock(entity.rawContent, BlockId(entity.blockId))
                }
                MarkdownDocument(
                    id = metadata.documentId,
                    title = metadata.title,
                    blocks = domainBlocks
                )
            }
        }.flowOn(dispatcherProvider.diffAndParsing)
    }

    override fun observeAllMetadata(): Flow<List<DocumentMetadata>> {
        return documentDao.observeAll().map { list ->
            list.map { it.toDomain() }
        }.flowOn(dispatcherProvider.default)
    }

    override suspend fun getDocument(id: String): Result<MarkdownDocument> = withContext(dispatcherProvider.io) {
        try {
            val metadata = documentDao.getById(id)
                ?: return@withContext Result.failure(DomainError.Storage.FileNotFound(id).asException())

            val blockEntities = blockDao.getBlocksForDocument(id)
            val domainBlocks = withContext(dispatcherProvider.diffAndParsing) {
                blockEntities.map { entity ->
                    parser.parseBlock(entity.rawContent, BlockId(entity.blockId))
                }
            }

            Result.success(
                MarkdownDocument(
                    id = metadata.documentId,
                    title = metadata.title,
                    blocks = domainBlocks
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveDocument(
        document: MarkdownDocument,
        metadata: DocumentMetadata?
    ): Result<Unit> = withContext(dispatcherProvider.io) {
        try {
            val now = System.currentTimeMillis()
            val entity = DocumentMetadataEntity(
                documentId = document.id,
                title = metadata?.title ?: document.title,
                treeUri = metadata?.treeUri,
                documentUri = metadata?.documentUri,
                lastAccessedTimestamp = metadata?.lastAccessedTimestamp ?: now,
                lastModifiedTimestamp = now,
                isUnlinked = metadata?.isUnlinked ?: false,
                isLocked = metadata?.isLocked ?: false
            )
            documentDao.upsert(entity)

            val blockEntities = document.blocks.mapIndexed { index, block ->
                BlockEntity(
                    blockId = block.id.value,
                    documentId = document.id,
                    orderIndex = index,
                    rawContent = block.rawContent,
                    blockType = block.type.toTypeString()
                )
            }
            blockDao.replaceBlocks(document.id, blockEntities)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteDocument(id: String): Result<Unit> = withContext(dispatcherProvider.io) {
        try {
            documentDao.deleteById(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateLastAccessed(
        id: String,
        timestamp: Long
    ): Result<Unit> = withContext(dispatcherProvider.io) {
        try {
            documentDao.updateLastAccessed(id, timestamp)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateLockStatus(
        id: String,
        isLocked: Boolean
    ): Result<Unit> = withContext(dispatcherProvider.io) {
        try {
            documentDao.updateLockStatus(id, isLocked)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun DocumentMetadataEntity.toDomain(): DocumentMetadata = DocumentMetadata(
        id = documentId,
        title = title,
        treeUri = treeUri,
        documentUri = documentUri,
        lastAccessedTimestamp = lastAccessedTimestamp,
        lastModifiedTimestamp = lastModifiedTimestamp,
        isUnlinked = isUnlinked,
        isLocked = isLocked
    )

    private fun BlockType.toTypeString(): String = when (this) {
        is BlockType.Heading -> "HEADING:$level"
        BlockType.Paragraph -> "PARAGRAPH"
        is BlockType.CodeBlock -> if (language != null) "CODE_BLOCK:$language" else "CODE_BLOCK"
        is BlockType.ListItem -> "LIST_ITEM:$ordered:${index ?: ""}"
        BlockType.BlockQuote -> "BLOCK_QUOTE"
        BlockType.ThematicBreak -> "THEMATIC_BREAK"
    }
}


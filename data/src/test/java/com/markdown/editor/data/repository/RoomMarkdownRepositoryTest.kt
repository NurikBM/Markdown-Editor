package com.markdown.editor.data.repository

import app.cash.turbine.test
import com.markdown.editor.core.dispatcher.DispatcherProvider
import com.markdown.editor.data.local.dao.BlockDao
import com.markdown.editor.data.local.dao.DocumentDao
import com.markdown.editor.data.local.entity.BlockEntity
import com.markdown.editor.data.local.entity.DocumentMetadataEntity
import com.markdown.editor.domain.error.DomainError
import com.markdown.editor.domain.error.DomainException
import com.markdown.editor.domain.model.BlockId
import com.markdown.editor.domain.model.BlockType
import com.markdown.editor.domain.model.DocumentMetadata
import com.markdown.editor.domain.model.MarkdownBlock
import com.markdown.editor.domain.model.MarkdownDocument
import com.markdown.editor.domain.parser.MarkdownBlockParser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoomMarkdownRepositoryTest {

    private val documentDao: DocumentDao = mockk(relaxed = true)
    private val blockDao: BlockDao = mockk(relaxed = true)
    private val parser: MarkdownBlockParser = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private val dispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
        override val diffAndParsing: CoroutineDispatcher = testDispatcher
    }

    private lateinit var repository: RoomMarkdownRepository

    @BeforeEach
    fun setUp() {
        repository = RoomMarkdownRepository(
            documentDao = documentDao,
            blockDao = blockDao,
            parser = parser,
            dispatcherProvider = dispatcherProvider
        )
    }

    @Test
    fun `observeDocument emits MarkdownDocument when document exists`() = runTest(testDispatcher) {
        val docId = "doc-1"
        val metadataEntity = DocumentMetadataEntity(
            documentId = docId,
            title = "Test Doc"
        )
        val blockEntities = listOf(
            BlockEntity(
                blockId = "block-1",
                documentId = docId,
                orderIndex = 0,
                rawContent = "# Header",
                blockType = "HEADING:1"
            )
        )
        val domainBlock = MarkdownBlock(
            id = BlockId("block-1"),
            rawContent = "# Header",
            type = BlockType.Heading(1)
        )

        every { documentDao.observeById(docId) } returns flowOf(metadataEntity)
        every { blockDao.observeBlocksForDocument(docId) } returns flowOf(blockEntities)
        every { parser.parseBlock("# Header", BlockId("block-1")) } returns domainBlock

        repository.observeDocument(docId).test {
            val doc = awaitItem()
            assertEquals(docId, doc?.id)
            assertEquals("Test Doc", doc?.title)
            assertEquals(1, doc?.blocks?.size)
            assertEquals(BlockId("block-1"), doc?.blocks?.first()?.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeDocument emits null when document metadata is not found`() = runTest(testDispatcher) {
        val docId = "doc-nonexistent"
        every { documentDao.observeById(docId) } returns flowOf(null)
        every { blockDao.observeBlocksForDocument(docId) } returns flowOf(emptyList())

        repository.observeDocument(docId).test {
            val doc = awaitItem()
            assertNull(doc)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeAllMetadata emits mapped DocumentMetadata list`() = runTest(testDispatcher) {
        val entities = listOf(
            DocumentMetadataEntity(documentId = "1", title = "Doc 1"),
            DocumentMetadataEntity(documentId = "2", title = "Doc 2")
        )
        every { documentDao.observeAll() } returns flowOf(entities)

        repository.observeAllMetadata().test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertEquals("1", list[0].id)
            assertEquals("Doc 1", list[0].title)
            assertEquals("2", list[1].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getDocument returns success when found`() = runTest(testDispatcher) {
        val docId = "doc-1"
        coEvery { documentDao.getById(docId) } returns DocumentMetadataEntity(docId, "Title")
        coEvery { blockDao.getBlocksForDocument(docId) } returns listOf(
            BlockEntity("b-1", docId, 0, "Paragraph content", "PARAGRAPH")
        )
        every { parser.parseBlock("Paragraph content", BlockId("b-1")) } returns MarkdownBlock(
            id = BlockId("b-1"),
            rawContent = "Paragraph content",
            type = BlockType.Paragraph
        )

        val result = repository.getDocument(docId)
        assertTrue(result.isSuccess)
        val doc = result.getOrThrow()
        assertEquals(docId, doc.id)
        assertEquals(1, doc.blocks.size)
    }

    @Test
    fun `getDocument returns failure when document not found`() = runTest(testDispatcher) {
        val docId = "missing"
        coEvery { documentDao.getById(docId) } returns null

        val result = repository.getDocument(docId)
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is DomainException)
        assertTrue((ex as DomainException).error is DomainError.Storage.FileNotFound)
    }

    @Test
    fun `saveDocument upserts metadata and replaces blocks`() = runTest(testDispatcher) {
        val block = MarkdownBlock(
            id = BlockId("b-1"),
            rawContent = "Content",
            type = BlockType.Paragraph
        )
        val document = MarkdownDocument(
            id = "doc-save",
            title = "Saved Doc",
            blocks = listOf(block)
        )

        val result = repository.saveDocument(document)
        assertTrue(result.isSuccess)

        coVerify {
            documentDao.upsert(match { it.documentId == "doc-save" && it.title == "Saved Doc" })
            blockDao.replaceBlocks("doc-save", match { it.size == 1 && it.first().blockId == "b-1" })
        }
    }

    @Test
    fun `deleteDocument delegates to documentDao`() = runTest(testDispatcher) {
        val result = repository.deleteDocument("del-1")
        assertTrue(result.isSuccess)
        coVerify { documentDao.deleteById("del-1") }
    }

    @Test
    fun `updateLastAccessed delegates to documentDao`() = runTest(testDispatcher) {
        val timestamp = 123456789L
        val result = repository.updateLastAccessed("doc-1", timestamp)
        assertTrue(result.isSuccess)
        coVerify { documentDao.updateLastAccessed("doc-1", timestamp) }
    }
}


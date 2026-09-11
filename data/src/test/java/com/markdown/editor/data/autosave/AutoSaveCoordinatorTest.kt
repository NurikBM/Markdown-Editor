package com.markdown.editor.data.autosave

import com.markdown.editor.core.dispatcher.DispatcherProvider
import com.markdown.editor.data.diff.DiffEngine
import com.markdown.editor.domain.model.BlockId
import com.markdown.editor.domain.model.BlockType
import com.markdown.editor.domain.model.MarkdownBlock
import com.markdown.editor.domain.model.MarkdownDocument
import com.markdown.editor.domain.repository.MarkdownRepository
import com.markdown.editor.domain.repository.SnapshotRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutoSaveCoordinatorTest {

    private val markdownRepository: MarkdownRepository = mockk(relaxed = true)
    private val snapshotRepository: SnapshotRepository = mockk(relaxed = true)
    private val diffEngine = DiffEngine()
    private val testDispatcher = StandardTestDispatcher()

    private val dispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
        override val diffAndParsing: CoroutineDispatcher = testDispatcher
    }

    @BeforeEach
    fun setUp() {
        coEvery { markdownRepository.saveDocument(any(), any()) } returns Result.success(Unit)
        coEvery { snapshotRepository.recordSnapshot(any()) } returns Result.success(Unit)
    }

    @Test
    fun `scheduleSave executes save after debounce interval`() = runTest(testDispatcher) {
        val coordinator = AutoSaveCoordinator(
            markdownRepository = markdownRepository,
            snapshotRepository = snapshotRepository,
            diffEngine = diffEngine,
            dispatcherProvider = dispatcherProvider,
            coroutineScope = this,
            debounceMillis = 300L
        )

        val block = MarkdownBlock(
            id = BlockId("b-1"),
            rawContent = "Updated content",
            type = BlockType.Paragraph
        )
        val document = MarkdownDocument(
            id = "doc-1",
            title = "Test",
            blocks = listOf(block)
        )

        val request = AutoSaveRequest(
            document = document,
            modifiedBlockId = BlockId("b-1"),
            oldBlockContent = "Old content"
        )

        coordinator.scheduleSave(request)

        // Advance before debounce
        advanceTimeBy(100L)
        coVerify(exactly = 0) { markdownRepository.saveDocument(any(), any()) }

        // Advance beyond debounce (300ms)
        advanceTimeBy(250L)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            snapshotRepository.recordSnapshot(match {
                it.documentId == "doc-1" && it.blockId == BlockId("b-1") && it.forwardDiff.isNotEmpty()
            })
            markdownRepository.saveDocument(document, null)
        }
        assertFalse(coordinator.isSaving.value)
    }

    @Test
    fun `saveImmediately executes immediately without waiting for debounce`() = runTest(testDispatcher) {
        val coordinator = AutoSaveCoordinator(
            markdownRepository = markdownRepository,
            snapshotRepository = snapshotRepository,
            diffEngine = diffEngine,
            dispatcherProvider = dispatcherProvider,
            coroutineScope = this,
            debounceMillis = 300L
        )

        val block = MarkdownBlock(
            id = BlockId("b-1"),
            rawContent = "Immediate content",
            type = BlockType.Paragraph
        )
        val document = MarkdownDocument(id = "doc-2", title = "Immediate Doc", blocks = listOf(block))

        val request = AutoSaveRequest(document = document)
        val result = coordinator.saveImmediately(request)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { markdownRepository.saveDocument(document, null) }
        coVerify(exactly = 0) { snapshotRepository.recordSnapshot(any()) }
    }

    @Test
    fun `scheduleSave does not record snapshot when block content is unchanged`() = runTest(testDispatcher) {
        val coordinator = AutoSaveCoordinator(
            markdownRepository = markdownRepository,
            snapshotRepository = snapshotRepository,
            diffEngine = diffEngine,
            dispatcherProvider = dispatcherProvider,
            coroutineScope = this,
            debounceMillis = 300L
        )

        val block = MarkdownBlock(
            id = BlockId("b-1"),
            rawContent = "Same content",
            type = BlockType.Paragraph
        )
        val document = MarkdownDocument(id = "doc-3", title = "Same", blocks = listOf(block))

        val request = AutoSaveRequest(
            document = document,
            modifiedBlockId = BlockId("b-1"),
            oldBlockContent = "Same content"
        )

        coordinator.scheduleSave(request)
        advanceTimeBy(350L)
        advanceUntilIdle()

        coVerify(exactly = 1) { markdownRepository.saveDocument(document, null) }
        coVerify(exactly = 0) { snapshotRepository.recordSnapshot(any()) }
    }
}

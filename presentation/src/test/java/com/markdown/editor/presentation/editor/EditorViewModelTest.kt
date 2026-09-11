package com.markdown.editor.presentation.editor

import app.cash.turbine.test
import com.markdown.editor.core.dispatcher.DispatcherProvider
import com.markdown.editor.domain.diff.DiffCalculator
import com.markdown.editor.domain.diff.DiffResult
import com.markdown.editor.domain.model.BlockId
import com.markdown.editor.domain.model.BlockType
import com.markdown.editor.domain.model.MarkdownBlock
import com.markdown.editor.domain.model.MarkdownDocument
import com.markdown.editor.domain.parser.CommonmarkBlockParser
import com.markdown.editor.domain.repository.MarkdownRepository
import com.markdown.editor.domain.repository.SnapshotRepository
import com.markdown.editor.domain.usecase.MergeBlockUseCase
import com.markdown.editor.domain.usecase.RedoBlockUseCase
import com.markdown.editor.domain.usecase.SplitBlockUseCase
import com.markdown.editor.domain.usecase.UndoBlockUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {

    private val markdownRepository: MarkdownRepository = mockk(relaxed = true)
    private val snapshotRepository: SnapshotRepository = mockk(relaxed = true)
    private val parser = CommonmarkBlockParser()

    private val diffCalculator = object : DiffCalculator {
        override fun computeDiff(oldText: String, newText: String): DiffResult {
            return DiffResult(
                forwardDiff = newText,
                reverseDiff = oldText,
                hasChanges = oldText != newText
            )
        }

        override fun applyPatch(sourceText: String, unifiedDiff: String): Result<String> {
            return Result.success(unifiedDiff)
        }
    }

    private val splitBlockUseCase = SplitBlockUseCase(parser)
    private val mergeBlockUseCase = MergeBlockUseCase(parser)
    private val undoBlockUseCase = UndoBlockUseCase(diffCalculator, parser)
    private val redoBlockUseCase = RedoBlockUseCase(diffCalculator, parser)

    private val testDispatcher = StandardTestDispatcher()
    private val exportHtmlUseCase = com.markdown.editor.domain.usecase.ExportHtmlUseCase(testDispatcher)

    private val dispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
        override val diffAndParsing: CoroutineDispatcher = testDispatcher
    }

    private lateinit var viewModel: EditorViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { markdownRepository.saveDocument(any(), any()) } returns Result.success(Unit)
        coEvery { snapshotRepository.recordSnapshot(any()) } returns Result.success(Unit)

        viewModel = EditorViewModel(
            markdownRepository = markdownRepository,
            snapshotRepository = snapshotRepository,
            splitBlockUseCase = splitBlockUseCase,
            mergeBlockUseCase = mergeBlockUseCase,
            undoBlockUseCase = undoBlockUseCase,
            redoBlockUseCase = redoBlockUseCase,
            diffCalculator = diffCalculator,
            parser = parser,
            dispatcherProvider = dispatcherProvider,
            exportHtmlUseCase = exportHtmlUseCase
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadDocument updates state with loaded blocks`() = runTest(testDispatcher) {
        val block = MarkdownBlock(
            id = BlockId("b-1"),
            rawContent = "# Title",
            type = BlockType.Heading(1)
        )
        val doc = MarkdownDocument(id = "doc-1", title = "Loaded Title", blocks = listOf(block))
        coEvery { markdownRepository.getDocument("doc-1") } returns Result.success(doc)

        viewModel.processIntent(EditorIntent.LoadDocument("doc-1"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("doc-1", state.documentId)
        assertEquals("Loaded Title", state.title)
        assertEquals(1, state.blocks.size)
        assertEquals(BlockId("b-1"), state.blocks.first().id)
        assertFalse(state.isLoading)
        assertFalse(state.canUndo)
    }

    @Test
    fun `updateBlock modifies content and enables undo`() = runTest(testDispatcher) {
        val block = MarkdownBlock(
            id = BlockId("b-1"),
            rawContent = "Initial text",
            type = BlockType.Paragraph
        )
        val doc = MarkdownDocument(id = "doc-1", blocks = listOf(block))
        coEvery { markdownRepository.getDocument("doc-1") } returns Result.success(doc)

        viewModel.processIntent(EditorIntent.LoadDocument("doc-1"))
        advanceUntilIdle()

        viewModel.processIntent(EditorIntent.UpdateBlock(BlockId("b-1"), "Modified text"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Modified text", state.blocks.first().rawContent)
        assertTrue(state.canUndo)
        assertFalse(state.canRedo)
        coVerify { snapshotRepository.recordSnapshot(any()) }
    }

    @Test
    fun `splitBlock partitions block and emits RequestFocusOnBlock effect`() = runTest(testDispatcher) {
        val block = MarkdownBlock(
            id = BlockId("b-1"),
            rawContent = "Hello World",
            type = BlockType.Paragraph
        )
        val doc = MarkdownDocument(id = "doc-1", blocks = listOf(block))
        coEvery { markdownRepository.getDocument("doc-1") } returns Result.success(doc)

        viewModel.processIntent(EditorIntent.LoadDocument("doc-1"))
        advanceUntilIdle()

        viewModel.uiEffect.test {
            viewModel.processIntent(EditorIntent.SplitBlock(BlockId("b-1"), cursorPosition = 5))
            advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is EditorEffect.RequestFocusOnBlock)

            val state = viewModel.uiState.value
            assertEquals(2, state.blocks.size)
            assertEquals("Hello", state.blocks[0].rawContent)
            assertEquals(" World", state.blocks[1].rawContent)
            assertEquals(state.blocks[1].id, (effect as EditorEffect.RequestFocusOnBlock).blockId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `mergeBlockWithPrevious joins blocks and sets cursor position`() = runTest(testDispatcher) {
        val b1 = MarkdownBlock(id = BlockId("b-1"), rawContent = "Prefix ", type = BlockType.Paragraph)
        val b2 = MarkdownBlock(id = BlockId("b-2"), rawContent = "Suffix", type = BlockType.Paragraph)
        val doc = MarkdownDocument(id = "doc-1", blocks = listOf(b1, b2))
        coEvery { markdownRepository.getDocument("doc-1") } returns Result.success(doc)

        viewModel.processIntent(EditorIntent.LoadDocument("doc-1"))
        advanceUntilIdle()

        viewModel.uiEffect.test {
            viewModel.processIntent(EditorIntent.MergeBlockWithPrevious(BlockId("b-2")))
            advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is EditorEffect.RequestFocusOnBlock)
            val focusEffect = effect as EditorEffect.RequestFocusOnBlock
            assertEquals(BlockId("b-1"), focusEffect.blockId)
            assertEquals(7, focusEffect.cursorPosition)

            val state = viewModel.uiState.value
            assertEquals(1, state.blocks.size)
            assertEquals("Prefix Suffix", state.blocks[0].rawContent)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `undo and redo restore block state and update flags`() = runTest(testDispatcher) {
        val block = MarkdownBlock(id = BlockId("b-1"), rawContent = "Version 1", type = BlockType.Paragraph)
        val doc = MarkdownDocument(id = "doc-1", blocks = listOf(block))
        coEvery { markdownRepository.getDocument("doc-1") } returns Result.success(doc)

        viewModel.processIntent(EditorIntent.LoadDocument("doc-1"))
        advanceUntilIdle()

        // Edit
        viewModel.processIntent(EditorIntent.UpdateBlock(BlockId("b-1"), "Version 2"))
        advanceUntilIdle()
        assertEquals("Version 2", viewModel.uiState.value.blocks[0].rawContent)
        assertTrue(viewModel.uiState.value.canUndo)

        // Undo
        viewModel.processIntent(EditorIntent.Undo)
        advanceUntilIdle()
        assertEquals("Version 1", viewModel.uiState.value.blocks[0].rawContent)
        assertFalse(viewModel.uiState.value.canUndo)
        assertTrue(viewModel.uiState.value.canRedo)

        // Redo
        viewModel.processIntent(EditorIntent.Redo)
        advanceUntilIdle()
        assertEquals("Version 2", viewModel.uiState.value.blocks[0].rawContent)
        assertTrue(viewModel.uiState.value.canUndo)
        assertFalse(viewModel.uiState.value.canRedo)
    }

    @Test
    fun `saveExplicitly saves document and emits ShowToast`() = runTest(testDispatcher) {
        val block = MarkdownBlock(id = BlockId("b-1"), rawContent = "Content", type = BlockType.Paragraph)
        val doc = MarkdownDocument(id = "doc-1", blocks = listOf(block))
        coEvery { markdownRepository.getDocument("doc-1") } returns Result.success(doc)

        viewModel.processIntent(EditorIntent.LoadDocument("doc-1"))
        advanceUntilIdle()

        viewModel.uiEffect.test {
            viewModel.processIntent(EditorIntent.SaveExplicitly)
            advanceUntilIdle()

            val effect = awaitItem()
            assertTrue(effect is EditorEffect.ShowToast)
            cancelAndIgnoreRemainingEvents()
        }

        coVerify { markdownRepository.saveDocument(any()) }
    }

    @Test
    fun `setViewMode updates viewMode in state`() = runTest(testDispatcher) {
        assertEquals(EditorViewMode.EDITOR_ONLY, viewModel.uiState.value.viewMode)

        viewModel.processIntent(EditorIntent.SetViewMode(EditorViewMode.SPLIT_VIEW))
        advanceUntilIdle()
        assertEquals(EditorViewMode.SPLIT_VIEW, viewModel.uiState.value.viewMode)

        viewModel.processIntent(EditorIntent.SetViewMode(EditorViewMode.PREVIEW_ONLY))
        advanceUntilIdle()
        assertEquals(EditorViewMode.PREVIEW_ONLY, viewModel.uiState.value.viewMode)
    }

    @Test
    fun `togglePreview cycles sequentially through view modes`() = runTest(testDispatcher) {
        assertEquals(EditorViewMode.EDITOR_ONLY, viewModel.uiState.value.viewMode)

        viewModel.processIntent(EditorIntent.TogglePreview)
        advanceUntilIdle()
        assertEquals(EditorViewMode.SPLIT_VIEW, viewModel.uiState.value.viewMode)

        viewModel.processIntent(EditorIntent.TogglePreview)
        advanceUntilIdle()
        assertEquals(EditorViewMode.PREVIEW_ONLY, viewModel.uiState.value.viewMode)

        viewModel.processIntent(EditorIntent.TogglePreview)
        advanceUntilIdle()
        assertEquals(EditorViewMode.EDITOR_ONLY, viewModel.uiState.value.viewMode)
    }

    @Test
    fun `loadDocument when not found bootstraps initial welcome document`() = runTest(testDispatcher) {
        coEvery { markdownRepository.getDocument("missing-doc") } returns Result.failure(Exception("Not found"))

        viewModel.processIntent(EditorIntent.LoadDocument("missing-doc"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("missing-doc", state.documentId)
        assertEquals("Welcome to Markdown Editor", state.title)
        assertTrue(state.blocks.isNotEmpty())
        assertFalse(state.isLoading)
        coVerify { markdownRepository.saveDocument(match { it.id == "missing-doc" }, any()) }
    }

    @Test
    fun `exportDocument HTML emits ShareContent effect with html mime type`() = runTest(testDispatcher) {
        val testDoc = MarkdownDocument(
            id = "doc-export",
            title = "Export Test",
            blocks = listOf(MarkdownBlock(id = BlockId("1"), rawContent = "Hello HTML", type = BlockType.Paragraph))
        )
        coEvery { markdownRepository.getDocument("doc-export") } returns Result.success(testDoc)
        viewModel.processIntent(EditorIntent.LoadDocument("doc-export"))
        advanceUntilIdle()

        viewModel.uiEffect.test {
            viewModel.processIntent(EditorIntent.ExportDocument(com.markdown.editor.domain.model.ExportFormat.HTML))
            val effect = awaitItem()
            assertTrue(effect is EditorEffect.ShareContent)
            val share = effect as EditorEffect.ShareContent
            assertEquals("Export Test.html", share.title)
            assertEquals("text/html", share.mimeType)
            assertTrue(share.content.contains("Hello HTML"))
        }
    }

    @Test
    fun `exportDocument PDF emits PrintHtml effect`() = runTest(testDispatcher) {
        val testDoc = MarkdownDocument(
            id = "doc-pdf",
            title = "PDF Test",
            blocks = listOf(MarkdownBlock(id = BlockId("1"), rawContent = "Hello PDF", type = BlockType.Paragraph))
        )
        coEvery { markdownRepository.getDocument("doc-pdf") } returns Result.success(testDoc)
        viewModel.processIntent(EditorIntent.LoadDocument("doc-pdf"))
        advanceUntilIdle()

        viewModel.uiEffect.test {
            viewModel.processIntent(EditorIntent.ExportDocument(com.markdown.editor.domain.model.ExportFormat.PDF))
            val effect = awaitItem()
            assertTrue(effect is EditorEffect.PrintHtml)
            val print = effect as EditorEffect.PrintHtml
            assertEquals("PDF Test", print.jobName)
            assertTrue(print.htmlContent.contains("Hello PDF"))
        }
    }

    @Test
    fun `exportDocument MARKDOWN emits ShareContent effect with markdown mime type`() = runTest(testDispatcher) {
        val testDoc = MarkdownDocument(
            id = "doc-md",
            title = "Markdown Test",
            blocks = listOf(MarkdownBlock(id = BlockId("1"), rawContent = "Hello MD", type = BlockType.Paragraph))
        )
        coEvery { markdownRepository.getDocument("doc-md") } returns Result.success(testDoc)
        viewModel.processIntent(EditorIntent.LoadDocument("doc-md"))
        advanceUntilIdle()

        viewModel.uiEffect.test {
            viewModel.processIntent(EditorIntent.ExportDocument(com.markdown.editor.domain.model.ExportFormat.MARKDOWN))
            val effect = awaitItem()
            assertTrue(effect is EditorEffect.ShareContent)
            val share = effect as EditorEffect.ShareContent
            assertEquals("Markdown Test.md", share.title)
            assertEquals("text/markdown", share.mimeType)
            assertEquals("Hello MD", share.content)
        }
    }
}

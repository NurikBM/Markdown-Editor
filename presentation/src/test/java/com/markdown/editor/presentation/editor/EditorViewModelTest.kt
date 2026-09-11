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

    @Test
    fun `loading document generates table of contents from heading blocks`() = runTest(testDispatcher) {
        val testDoc = MarkdownDocument(
            id = "doc-toc",
            title = "TOC Test",
            blocks = listOf(
                MarkdownBlock(id = BlockId("h1"), rawContent = "# Introduction", type = BlockType.Heading(1), plainText = "Introduction"),
                MarkdownBlock(id = BlockId("p1"), rawContent = "Some text", type = BlockType.Paragraph, plainText = "Some text"),
                MarkdownBlock(id = BlockId("h2"), rawContent = "## Getting Started", type = BlockType.Heading(2), plainText = "Getting Started")
            )
        )
        coEvery { markdownRepository.getDocument("doc-toc") } returns Result.success(testDoc)

        viewModel.processIntent(EditorIntent.LoadDocument("doc-toc"))
        advanceUntilIdle()

        val toc = viewModel.uiState.value.tableOfContents
        assertEquals(2, toc.size)
        assertEquals(BlockId("h1"), toc[0].blockId)
        assertEquals(1, toc[0].level)
        assertEquals("Introduction", toc[0].title)
        assertEquals(0, toc[0].blockIndex)

        assertEquals(BlockId("h2"), toc[1].blockId)
        assertEquals(2, toc[1].level)
        assertEquals("Getting Started", toc[1].title)
        assertEquals(2, toc[1].blockIndex)
    }

    @Test
    fun `toggleTableOfContents toggles visibility in uiState`() = runTest(testDispatcher) {
        assertFalse(viewModel.uiState.value.isTableOfContentsVisible)

        viewModel.processIntent(EditorIntent.ToggleTableOfContents())
        assertTrue(viewModel.uiState.value.isTableOfContentsVisible)

        viewModel.processIntent(EditorIntent.ToggleTableOfContents())
        assertFalse(viewModel.uiState.value.isTableOfContentsVisible)

        viewModel.processIntent(EditorIntent.ToggleTableOfContents(visible = true))
        assertTrue(viewModel.uiState.value.isTableOfContentsVisible)
    }

    @Test
    fun `navigateToHeading closes TOC, updates focus, and emits ScrollToBlock effect`() = runTest(testDispatcher) {
        val headingBlock = MarkdownBlock(
            id = BlockId("heading-target"),
            rawContent = "## Architecture",
            type = BlockType.Heading(2),
            plainText = "Architecture"
        )
        val testDoc = MarkdownDocument(
            id = "doc-nav",
            title = "Navigation Test",
            blocks = listOf(
                MarkdownBlock(id = BlockId("p1"), rawContent = "Paragraph", type = BlockType.Paragraph),
                headingBlock
            )
        )
        coEvery { markdownRepository.getDocument("doc-nav") } returns Result.success(testDoc)
        viewModel.processIntent(EditorIntent.LoadDocument("doc-nav"))
        viewModel.processIntent(EditorIntent.ToggleTableOfContents(visible = true))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isTableOfContentsVisible)
        val tocItem = viewModel.uiState.value.tableOfContents.first()

        viewModel.uiEffect.test {
            viewModel.processIntent(EditorIntent.NavigateToHeading(tocItem))

            val scrollEffect = awaitItem()
            assertTrue(scrollEffect is EditorEffect.ScrollToBlock)
            val scroll = scrollEffect as EditorEffect.ScrollToBlock
            assertEquals(1, scroll.blockIndex)
            assertEquals(BlockId("heading-target"), scroll.blockId)

            val focusEffect = awaitItem()
            assertTrue(focusEffect is EditorEffect.RequestFocusOnBlock)
            assertEquals(BlockId("heading-target"), (focusEffect as EditorEffect.RequestFocusOnBlock).blockId)
        }

        assertFalse(viewModel.uiState.value.isTableOfContentsVisible)
        assertEquals(BlockId("heading-target"), viewModel.uiState.value.focusedBlockId)
    }

    @Test
    fun `updating block to heading automatically refreshes table of contents`() = runTest(testDispatcher) {
        val blockId = BlockId("editable-block")
        val testDoc = MarkdownDocument(
            id = "doc-edit",
            title = "Edit Test",
            blocks = listOf(
                MarkdownBlock(id = blockId, rawContent = "Just a paragraph", type = BlockType.Paragraph)
            )
        )
        coEvery { markdownRepository.getDocument("doc-edit") } returns Result.success(testDoc)
        viewModel.processIntent(EditorIntent.LoadDocument("doc-edit"))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.tableOfContents.isEmpty())

        viewModel.processIntent(EditorIntent.UpdateBlock(blockId, "### Deep Dive"))
        advanceUntilIdle()

        val toc = viewModel.uiState.value.tableOfContents
        assertEquals(1, toc.size)
        assertEquals(3, toc[0].level)
        assertEquals("Deep Dive", toc[0].title)
    }

    @Test
    fun `toggleFindReplace toggles visibility and resets query on hide`() = runTest(testDispatcher) {
        assertFalse(viewModel.uiState.value.isFindReplaceVisible)

        viewModel.processIntent(EditorIntent.ToggleFindReplace(visible = true))
        assertTrue(viewModel.uiState.value.isFindReplaceVisible)

        viewModel.processIntent(EditorIntent.SetSearchQuery("Kotlin"))
        viewModel.processIntent(EditorIntent.SetReplaceQuery("Java"))
        assertEquals("Kotlin", viewModel.uiState.value.searchQuery)
        assertEquals("Java", viewModel.uiState.value.replaceQuery)

        viewModel.processIntent(EditorIntent.ToggleFindReplace(visible = false))
        assertFalse(viewModel.uiState.value.isFindReplaceVisible)
        assertEquals("", viewModel.uiState.value.searchQuery)
        assertEquals("", viewModel.uiState.value.replaceQuery)
        assertTrue(viewModel.uiState.value.findMatches.isEmpty())
    }

    @Test
    fun `search query finds matches across multiple blocks and navigates to first match`() = runTest(testDispatcher) {
        val testDoc = MarkdownDocument(
            id = "doc-find",
            title = "Find Test",
            blocks = listOf(
                MarkdownBlock(id = BlockId("b1"), rawContent = "Hello world, markdown world", type = BlockType.Paragraph),
                MarkdownBlock(id = BlockId("b2"), rawContent = "Another world here", type = BlockType.Paragraph)
            )
        )
        coEvery { markdownRepository.getDocument("doc-find") } returns Result.success(testDoc)
        viewModel.processIntent(EditorIntent.LoadDocument("doc-find"))
        advanceUntilIdle()

        viewModel.uiEffect.test {
            viewModel.processIntent(EditorIntent.SetSearchQuery("world"))
            advanceUntilIdle()

            val scrollEffect = awaitItem()
            assertTrue(scrollEffect is EditorEffect.ScrollToBlock)
            val scroll = scrollEffect as EditorEffect.ScrollToBlock
            assertEquals(0, scroll.blockIndex)

            val focusEffect = awaitItem()
            assertTrue(focusEffect is EditorEffect.RequestFocusOnBlock)
            val focus = focusEffect as EditorEffect.RequestFocusOnBlock
            assertEquals(BlockId("b1"), focus.blockId)
            assertEquals(6, focus.cursorPosition)
        }

        val state = viewModel.uiState.value
        assertEquals(3, state.findMatches.size)
        assertEquals(0, state.currentMatchIndex)
    }

    @Test
    fun `findNextMatch and findPreviousMatch navigate cyclically through matches`() = runTest(testDispatcher) {
        val testDoc = MarkdownDocument(
            id = "doc-cycle",
            title = "Cycle Test",
            blocks = listOf(
                MarkdownBlock(id = BlockId("b1"), rawContent = "match one and match two", type = BlockType.Paragraph),
                MarkdownBlock(id = BlockId("b2"), rawContent = "match three", type = BlockType.Paragraph)
            )
        )
        coEvery { markdownRepository.getDocument("doc-cycle") } returns Result.success(testDoc)
        viewModel.processIntent(EditorIntent.LoadDocument("doc-cycle"))
        advanceUntilIdle()

        viewModel.uiEffect.test {
            viewModel.processIntent(EditorIntent.SetSearchQuery("match"))
            advanceUntilIdle()

            // Consume initial effects from match 0
            awaitItem() // scroll to match 0
            awaitItem() // focus on match 0

            assertEquals(3, viewModel.uiState.value.findMatches.size)
            assertEquals(0, viewModel.uiState.value.currentMatchIndex)

            // Next -> index 1
            viewModel.processIntent(EditorIntent.FindNextMatch)
            assertEquals(1, viewModel.uiState.value.currentMatchIndex)
            awaitItem() // scroll
            awaitItem() // focus

            // Next -> index 2
            viewModel.processIntent(EditorIntent.FindNextMatch)
            assertEquals(2, viewModel.uiState.value.currentMatchIndex)
            awaitItem() // scroll
            awaitItem() // focus

            // Next -> wrap around to index 0
            viewModel.processIntent(EditorIntent.FindNextMatch)
            assertEquals(0, viewModel.uiState.value.currentMatchIndex)
            awaitItem() // scroll
            awaitItem() // focus

            // Previous -> wrap around to index 2
            viewModel.processIntent(EditorIntent.FindPreviousMatch)
            assertEquals(2, viewModel.uiState.value.currentMatchIndex)
            awaitItem() // scroll
            awaitItem() // focus
        }
    }

    @Test
    fun `replaceCurrentMatch replaces single match and records undo snapshot`() = runTest(testDispatcher) {
        val testDoc = MarkdownDocument(
            id = "doc-replace",
            title = "Replace Test",
            blocks = listOf(
                MarkdownBlock(id = BlockId("b1"), rawContent = "foo bar foo", type = BlockType.Paragraph)
            )
        )
        coEvery { markdownRepository.getDocument("doc-replace") } returns Result.success(testDoc)
        viewModel.processIntent(EditorIntent.LoadDocument("doc-replace"))
        advanceUntilIdle()

        viewModel.processIntent(EditorIntent.SetSearchQuery("foo"))
        viewModel.processIntent(EditorIntent.SetReplaceQuery("baz"))
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.findMatches.size)
        assertEquals(0, viewModel.uiState.value.currentMatchIndex)

        viewModel.processIntent(EditorIntent.ReplaceCurrentMatch)
        advanceUntilIdle()

        val updatedBlock = viewModel.uiState.value.blocks.first()
        assertEquals("baz bar foo", updatedBlock.rawContent)
        // 1 match remaining
        assertEquals(1, viewModel.uiState.value.findMatches.size)
        assertTrue(viewModel.uiState.value.canUndo)
    }

    @Test
    fun `replaceAllMatches replaces all occurrences across document blocks`() = runTest(testDispatcher) {
        val testDoc = MarkdownDocument(
            id = "doc-replace-all",
            title = "Replace All Test",
            blocks = listOf(
                MarkdownBlock(id = BlockId("b1"), rawContent = "cat and cat", type = BlockType.Paragraph),
                MarkdownBlock(id = BlockId("b2"), rawContent = "another cat", type = BlockType.Paragraph)
            )
        )
        coEvery { markdownRepository.getDocument("doc-replace-all") } returns Result.success(testDoc)
        viewModel.processIntent(EditorIntent.LoadDocument("doc-replace-all"))
        advanceUntilIdle()

        viewModel.processIntent(EditorIntent.SetSearchQuery("cat"))
        viewModel.processIntent(EditorIntent.SetReplaceQuery("dog"))
        advanceUntilIdle()

        assertEquals(3, viewModel.uiState.value.findMatches.size)

        viewModel.processIntent(EditorIntent.ReplaceAllMatches)
        advanceUntilIdle()

        assertEquals("dog and dog", viewModel.uiState.value.blocks[0].rawContent)
        assertEquals("another dog", viewModel.uiState.value.blocks[1].rawContent)
        assertEquals(0, viewModel.uiState.value.findMatches.size)
    }

    @Test
    fun `applyFormatting wraps text and creates undo snapshot`() = runTest(testDispatcher) {
        val testDoc = MarkdownDocument(
            id = "doc-format",
            title = "Format Test",
            blocks = listOf(
                MarkdownBlock(id = BlockId("b1"), rawContent = "Hello World", type = BlockType.Paragraph)
            )
        )
        coEvery { markdownRepository.getDocument("doc-format") } returns Result.success(testDoc)
        viewModel.processIntent(EditorIntent.LoadDocument("doc-format"))
        advanceUntilIdle()

        viewModel.processIntent(EditorIntent.RequestFocus(BlockId("b1"), cursorPosition = 6, selectionEnd = 11))
        advanceUntilIdle()

        viewModel.processIntent(EditorIntent.ApplyFormatting(com.markdown.editor.domain.model.MarkdownFormatAction.BOLD))
        advanceUntilIdle()

        val updated = viewModel.uiState.value.blocks.first()
        assertEquals("Hello **World**", updated.rawContent)
        assertTrue(viewModel.uiState.value.canUndo)
    }

    @Test
    fun `setTheme updates uiState appTheme`() = runTest(testDispatcher) {
        assertEquals(com.markdown.editor.presentation.theme.AppTheme.SYSTEM, viewModel.uiState.value.appTheme)

        viewModel.processIntent(EditorIntent.SetTheme(com.markdown.editor.presentation.theme.AppTheme.AMOLED))
        advanceUntilIdle()

        assertEquals(com.markdown.editor.presentation.theme.AppTheme.AMOLED, viewModel.uiState.value.appTheme)
    }
}

package com.markdown.editor.presentation.editor

import androidx.lifecycle.viewModelScope
import com.markdown.editor.core.dispatcher.DispatcherProvider
import com.markdown.editor.domain.diff.DiffCalculator
import com.markdown.editor.domain.model.BlockId
import com.markdown.editor.domain.model.DocumentSnapshot
import com.markdown.editor.domain.model.MarkdownBlock
import com.markdown.editor.domain.model.MarkdownDocument
import com.markdown.editor.domain.parser.MarkdownBlockParser
import com.markdown.editor.domain.repository.MarkdownRepository
import com.markdown.editor.domain.repository.SnapshotRepository
import com.markdown.editor.domain.model.ExportFormat
import com.markdown.editor.domain.model.FindMatch
import com.markdown.editor.domain.model.TableOfContentsItem
import com.markdown.editor.domain.usecase.ExportHtmlUseCase
import com.markdown.editor.domain.usecase.FindInDocumentUseCase
import com.markdown.editor.domain.usecase.GenerateTableOfContentsUseCase
import com.markdown.editor.domain.usecase.MergeBlockUseCase
import com.markdown.editor.domain.usecase.MergeResult
import com.markdown.editor.domain.usecase.RedoBlockUseCase
import com.markdown.editor.domain.usecase.ReplaceInDocumentUseCase
import com.markdown.editor.domain.usecase.SplitBlockUseCase
import com.markdown.editor.domain.usecase.SplitResult
import com.markdown.editor.domain.usecase.UndoBlockUseCase
import com.markdown.editor.presentation.mvi.MviViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Production MVI ViewModel managing block-based document state, split/merge keystrokes,
 * deterministic undo/redo history, and in-editor navigation and search.
 */
class EditorViewModel(
    private val markdownRepository: MarkdownRepository,
    private val snapshotRepository: SnapshotRepository,
    private val splitBlockUseCase: SplitBlockUseCase,
    private val mergeBlockUseCase: MergeBlockUseCase,
    private val undoBlockUseCase: UndoBlockUseCase,
    private val redoBlockUseCase: RedoBlockUseCase,
    private val diffCalculator: DiffCalculator,
    private val parser: MarkdownBlockParser,
    private val dispatcherProvider: DispatcherProvider,
    private val exportHtmlUseCase: ExportHtmlUseCase,
    private val generateTableOfContentsUseCase: GenerateTableOfContentsUseCase = GenerateTableOfContentsUseCase(),
    private val findInDocumentUseCase: FindInDocumentUseCase = FindInDocumentUseCase(),
    private val replaceInDocumentUseCase: ReplaceInDocumentUseCase = ReplaceInDocumentUseCase(parser)
) : MviViewModel<EditorUiState, EditorIntent, EditorEffect>(EditorUiState()) {

    private val undoStack = ArrayDeque<DocumentSnapshot>()
    private val redoStack = ArrayDeque<DocumentSnapshot>()

    override fun processIntent(intent: EditorIntent) {
        when (intent) {
            is EditorIntent.LoadDocument -> loadDocument(intent.documentId)
            is EditorIntent.UpdateBlock -> updateBlock(intent.blockId, intent.newContent)
            is EditorIntent.SplitBlock -> splitBlock(intent.blockId, intent.cursorPosition)
            is EditorIntent.MergeBlockWithPrevious -> mergeBlock(intent.blockId)
            is EditorIntent.RequestFocus -> requestFocus(intent.blockId, intent.cursorPosition)
            is EditorIntent.ChangeTitle -> changeTitle(intent.newTitle)
            is EditorIntent.SetViewMode -> setViewMode(intent.mode)
            is EditorIntent.ExportDocument -> exportDocument(intent.format)
            is EditorIntent.ToggleTableOfContents -> toggleTableOfContents(intent.visible)
            is EditorIntent.NavigateToHeading -> navigateToHeading(intent.item)
            is EditorIntent.ToggleFindReplace -> toggleFindReplace(intent.visible)
            is EditorIntent.SetSearchQuery -> setSearchQuery(intent.query)
            is EditorIntent.SetReplaceQuery -> setReplaceQuery(intent.query)
            is EditorIntent.SetCaseSensitive -> setCaseSensitive(intent.caseSensitive)
            EditorIntent.FindNextMatch -> findNextMatch()
            EditorIntent.FindPreviousMatch -> findPreviousMatch()
            EditorIntent.ReplaceCurrentMatch -> replaceCurrentMatch()
            EditorIntent.ReplaceAllMatches -> replaceAllMatches()
            EditorIntent.TogglePreview -> togglePreview()
            EditorIntent.Undo -> performUndo()
            EditorIntent.Redo -> performRedo()
            EditorIntent.SaveExplicitly -> saveExplicitly()
        }
    }

    private fun setViewMode(mode: EditorViewMode) {
        updateState { copy(viewMode = mode) }
    }

    private fun togglePreview() {
        updateState {
            val nextMode = when (viewMode) {
                EditorViewMode.EDITOR_ONLY -> EditorViewMode.SPLIT_VIEW
                EditorViewMode.SPLIT_VIEW -> EditorViewMode.PREVIEW_ONLY
                EditorViewMode.PREVIEW_ONLY -> EditorViewMode.EDITOR_ONLY
            }
            copy(viewMode = nextMode)
        }
    }

    private fun toggleTableOfContents(visible: Boolean?) {
        updateState { copy(isTableOfContentsVisible = visible ?: !isTableOfContentsVisible) }
    }

    private fun navigateToHeading(item: TableOfContentsItem) {
        updateState {
            copy(
                isTableOfContentsVisible = false,
                focusedBlockId = item.blockId,
                cursorPosition = 0
            )
        }
        sendEffect(EditorEffect.ScrollToBlock(blockIndex = item.blockIndex, blockId = item.blockId))
        sendEffect(EditorEffect.RequestFocusOnBlock(item.blockId, 0))
    }

    private fun toggleFindReplace(visible: Boolean?) {
        val nextVisible = visible ?: !uiState.value.isFindReplaceVisible
        val matches = if (nextVisible && uiState.value.searchQuery.isNotEmpty()) {
            findInDocumentUseCase(uiState.value.blocks, uiState.value.searchQuery, uiState.value.isCaseSensitive)
        } else {
            emptyList()
        }
        val matchIndex = if (matches.isNotEmpty()) 0 else -1

        updateState {
            copy(
                isFindReplaceVisible = nextVisible,
                searchQuery = if (!nextVisible) "" else searchQuery,
                replaceQuery = if (!nextVisible) "" else replaceQuery,
                findMatches = matches,
                currentMatchIndex = matchIndex
            )
        }

        if (nextVisible && matches.isNotEmpty()) {
            val first = matches[0]
            sendEffect(EditorEffect.ScrollToBlock(first.blockIndex, first.blockId))
            sendEffect(EditorEffect.RequestFocusOnBlock(first.blockId, first.startIndex))
        }
    }

    private fun setSearchQuery(query: String) {
        val matches = if (query.isNotEmpty()) {
            findInDocumentUseCase(uiState.value.blocks, query, uiState.value.isCaseSensitive)
        } else {
            emptyList()
        }
        val matchIndex = if (matches.isNotEmpty()) 0 else -1

        updateState {
            copy(
                searchQuery = query,
                findMatches = matches,
                currentMatchIndex = matchIndex
            )
        }

        if (matches.isNotEmpty()) {
            val first = matches[0]
            sendEffect(EditorEffect.ScrollToBlock(first.blockIndex, first.blockId))
            sendEffect(EditorEffect.RequestFocusOnBlock(first.blockId, first.startIndex))
        }
    }

    private fun setReplaceQuery(query: String) {
        updateState { copy(replaceQuery = query) }
    }

    private fun setCaseSensitive(caseSensitive: Boolean) {
        val matches = if (uiState.value.searchQuery.isNotEmpty()) {
            findInDocumentUseCase(uiState.value.blocks, uiState.value.searchQuery, caseSensitive)
        } else {
            emptyList()
        }
        val matchIndex = if (matches.isNotEmpty()) 0 else -1

        updateState {
            copy(
                isCaseSensitive = caseSensitive,
                findMatches = matches,
                currentMatchIndex = matchIndex
            )
        }
    }

    private fun findNextMatch() {
        val state = uiState.value
        if (state.findMatches.isEmpty()) return

        val nextIndex = (state.currentMatchIndex + 1) % state.findMatches.size
        updateState { copy(currentMatchIndex = nextIndex) }

        val match = state.findMatches[nextIndex]
        sendEffect(EditorEffect.ScrollToBlock(match.blockIndex, match.blockId))
        sendEffect(EditorEffect.RequestFocusOnBlock(match.blockId, match.startIndex))
    }

    private fun findPreviousMatch() {
        val state = uiState.value
        if (state.findMatches.isEmpty()) return

        val prevIndex = if (state.currentMatchIndex - 1 < 0) {
            state.findMatches.size - 1
        } else {
            state.currentMatchIndex - 1
        }
        updateState { copy(currentMatchIndex = prevIndex) }

        val match = state.findMatches[prevIndex]
        sendEffect(EditorEffect.ScrollToBlock(match.blockIndex, match.blockId))
        sendEffect(EditorEffect.RequestFocusOnBlock(match.blockId, match.startIndex))
    }

    private fun replaceCurrentMatch() {
        val state = uiState.value
        if (state.currentMatchIndex !in state.findMatches.indices) return

        val match = state.findMatches[state.currentMatchIndex]
        val currentDoc = currentDocument()

        viewModelScope.launch {
            val oldBlock = currentDoc.blocks[match.blockIndex]
            val updatedDoc = withContext(dispatcherProvider.diffAndParsing) {
                replaceInDocumentUseCase.replaceSingle(currentDoc, match, state.replaceQuery)
            }
            val newBlock = updatedDoc.blocks[match.blockIndex]

            // Record undo snapshot
            val diffResult = withContext(dispatcherProvider.diffAndParsing) {
                diffCalculator.computeDiff(oldBlock.rawContent, newBlock.rawContent)
            }
            if (diffResult.hasChanges) {
                val snapshot = DocumentSnapshot(
                    documentId = currentDoc.id,
                    blockId = match.blockId,
                    forwardDiff = diffResult.forwardDiff,
                    reverseDiff = diffResult.reverseDiff
                )
                undoStack.addLast(snapshot)
                redoStack.clear()
                withContext(dispatcherProvider.io) {
                    snapshotRepository.recordSnapshot(snapshot)
                }
            }

            val newMatches = withContext(dispatcherProvider.diffAndParsing) {
                findInDocumentUseCase(updatedDoc.blocks, state.searchQuery, state.isCaseSensitive)
            }
            val newToc = withContext(dispatcherProvider.diffAndParsing) {
                generateTableOfContentsUseCase(updatedDoc.blocks)
            }
            val nextMatchIndex = if (newMatches.isNotEmpty()) {
                state.currentMatchIndex.coerceIn(0, newMatches.size - 1)
            } else {
                -1
            }

            updateState {
                copy(
                    blocks = updatedDoc.blocks,
                    findMatches = newMatches,
                    currentMatchIndex = nextMatchIndex,
                    tableOfContents = newToc,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = redoStack.isNotEmpty()
                )
            }
            persistCurrentDocument()

            if (nextMatchIndex in newMatches.indices) {
                val nextMatch = newMatches[nextMatchIndex]
                sendEffect(EditorEffect.ScrollToBlock(nextMatch.blockIndex, nextMatch.blockId))
                sendEffect(EditorEffect.RequestFocusOnBlock(nextMatch.blockId, nextMatch.startIndex))
            }
        }
    }

    private fun replaceAllMatches() {
        val state = uiState.value
        if (state.findMatches.isEmpty() || state.searchQuery.isEmpty()) return

        val currentDoc = currentDocument()

        viewModelScope.launch {
            val updatedDoc = withContext(dispatcherProvider.diffAndParsing) {
                replaceInDocumentUseCase.replaceAll(
                    document = currentDoc,
                    query = state.searchQuery,
                    replacement = state.replaceQuery,
                    isCaseSensitive = state.isCaseSensitive
                )
            }

            // Recompute matches and TOC
            val newMatches = withContext(dispatcherProvider.diffAndParsing) {
                findInDocumentUseCase(updatedDoc.blocks, state.searchQuery, state.isCaseSensitive)
            }
            val newToc = withContext(dispatcherProvider.diffAndParsing) {
                generateTableOfContentsUseCase(updatedDoc.blocks)
            }

            updateState {
                copy(
                    blocks = updatedDoc.blocks,
                    findMatches = newMatches,
                    currentMatchIndex = if (newMatches.isNotEmpty()) 0 else -1,
                    tableOfContents = newToc
                )
            }
            persistCurrentDocument()
            sendEffect(EditorEffect.ShowToast("Replaced all occurrences"))
        }
    }

    private fun loadDocument(documentId: String) {
        viewModelScope.launch {
            updateState { copy(isLoading = true, errorMessage = null) }
            val result = withContext(dispatcherProvider.io) {
                markdownRepository.getDocument(documentId)
            }
            result.onSuccess { doc ->
                val blocks = doc.blocks.ifEmpty {
                    listOf(withContext(dispatcherProvider.diffAndParsing) {
                        parser.parseBlock(
                            "",
                            BlockId()
                        )
                    })
                }
                val toc = generateTableOfContentsUseCase(blocks)
                undoStack.clear()
                redoStack.clear()
                updateState {
                    copy(
                        documentId = doc.id,
                        title = doc.title,
                        blocks = blocks,
                        tableOfContents = toc,
                        isLoading = false,
                        canUndo = false,
                        canRedo = false,
                        errorMessage = null
                    )
                }
            }.onFailure {
                // If not found in local persistence, bootstrap a rich initial document so the app is immediately usable
                val defaultDoc = createInitialDocument(documentId)
                withContext(dispatcherProvider.io) {
                    markdownRepository.saveDocument(defaultDoc)
                }
                val toc = generateTableOfContentsUseCase(defaultDoc.blocks)
                undoStack.clear()
                redoStack.clear()
                updateState {
                    copy(
                        documentId = defaultDoc.id,
                        title = defaultDoc.title,
                        blocks = defaultDoc.blocks,
                        tableOfContents = toc,
                        isLoading = false,
                        canUndo = false,
                        canRedo = false,
                        errorMessage = null
                    )
                }
            }
        }
    }

    private suspend fun createInitialDocument(documentId: String): MarkdownDocument {
        val welcomeMarkdown = """
            # Welcome to Markdown Editor

            A high-performance, offline-first Markdown editor built with modern Android and Jetpack Compose.

            ## Features
            - **Block-based editing**: Each paragraph, heading, and code block updates independently.
            - **Live Preview & Split-View**: Tap the view icon in the top bar to toggle side-by-side or stacked view.
            - **Offline-first**: All changes are continuously saved to your local Room database.

            > "Simplicity is prerequisite for reliability." — Edsger W. Dijkstra

            ```kotlin
            fun main() {
                println("Hello, Markdown Editor!")
            }
            ```

            ---

            Start typing here to test block splitting with Enter and merging with Backspace!
        """.trimIndent()

        return withContext(dispatcherProvider.diffAndParsing) {
            parser.parseDocument(
                rawMarkdown = welcomeMarkdown,
                documentId = documentId,
                title = "Welcome to Markdown Editor"
            )
        }
    }

    private fun updateBlock(blockId: BlockId, newContent: String) {
        viewModelScope.launch {
            val currentDoc = currentDocument()
            val blockIndex = currentDoc.blocks.indexOfFirst { it.id == blockId }
            if (blockIndex == -1) return@launch

            val oldBlock = currentDoc.blocks[blockIndex]
            if (oldBlock.rawContent == newContent) return@launch

            // Compute Myers diff for undo snapshot
            val diffResult = withContext(dispatcherProvider.diffAndParsing) {
                diffCalculator.computeDiff(oldBlock.rawContent, newContent)
            }

            if (diffResult.hasChanges) {
                val snapshot = DocumentSnapshot(
                    documentId = currentDoc.id,
                    blockId = blockId,
                    forwardDiff = diffResult.forwardDiff,
                    reverseDiff = diffResult.reverseDiff
                )
                undoStack.addLast(snapshot)
                redoStack.clear()
                withContext(dispatcherProvider.io) {
                    snapshotRepository.recordSnapshot(snapshot)
                }
            }

            // Re-parse single block incrementally on diff/parsing dispatcher
            val updatedBlock = withContext(dispatcherProvider.diffAndParsing) {
                parser.parseBlock(newContent, blockId)
            }

            val updatedBlocks = currentDoc.blocks.toMutableList().apply {
                set(blockIndex, updatedBlock)
            }

            val toc = generateTableOfContentsUseCase(updatedBlocks)
            updateState {
                copy(
                    blocks = updatedBlocks,
                    tableOfContents = toc,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = redoStack.isNotEmpty()
                )
            }

            persistCurrentDocument()
        }
    }

    private fun splitBlock(blockId: BlockId, cursorPosition: Int) {
        viewModelScope.launch {
            val currentDoc = currentDocument()
            val result = withContext(dispatcherProvider.diffAndParsing) {
                splitBlockUseCase(currentDoc, blockId, cursorPosition)
            }

            if (result is SplitResult.Success) {
                val toc = generateTableOfContentsUseCase(result.document.blocks)
                updateState {
                    copy(
                        blocks = result.document.blocks,
                        tableOfContents = toc,
                        focusedBlockId = result.newBlockId,
                        cursorPosition = 0
                    )
                }
                sendEffect(EditorEffect.RequestFocusOnBlock(result.newBlockId, 0))
                persistCurrentDocument()
            }
        }
    }

    private fun mergeBlock(blockId: BlockId) {
        viewModelScope.launch {
            val currentDoc = currentDocument()
            val result = withContext(dispatcherProvider.diffAndParsing) {
                mergeBlockUseCase(currentDoc, blockId)
            }

            if (result is MergeResult.Success) {
                val toc = generateTableOfContentsUseCase(result.document.blocks)
                updateState {
                    copy(
                        blocks = result.document.blocks,
                        tableOfContents = toc,
                        focusedBlockId = result.targetBlockId,
                        cursorPosition = result.cursorPosition
                    )
                }
                sendEffect(EditorEffect.RequestFocusOnBlock(result.targetBlockId, result.cursorPosition))
                persistCurrentDocument()
            }
        }
    }

    private fun performUndo() {
        if (undoStack.isEmpty()) return

        viewModelScope.launch {
            val snapshot = undoStack.removeLast()
            val currentDoc = currentDocument()

            val undoResult = withContext(dispatcherProvider.diffAndParsing) {
                undoBlockUseCase(currentDoc, snapshot)
            }

            undoResult.onSuccess { undoneDoc ->
                val toc = generateTableOfContentsUseCase(undoneDoc.blocks)
                redoStack.addLast(snapshot)
                updateState {
                    copy(
                        blocks = undoneDoc.blocks,
                        tableOfContents = toc,
                        canUndo = undoStack.isNotEmpty(),
                        canRedo = redoStack.isNotEmpty()
                    )
                }
                persistCurrentDocument()
            }.onFailure { error ->
                sendEffect(EditorEffect.ShowError("Undo failed: ${error.message}"))
            }
        }
    }

    private fun performRedo() {
        if (redoStack.isEmpty()) return

        viewModelScope.launch {
            val snapshot = redoStack.removeLast()
            val currentDoc = currentDocument()

            val redoResult = withContext(dispatcherProvider.diffAndParsing) {
                redoBlockUseCase(currentDoc, snapshot)
            }

            redoResult.onSuccess { redoneDoc ->
                val toc = generateTableOfContentsUseCase(redoneDoc.blocks)
                undoStack.addLast(snapshot)
                updateState {
                    copy(
                        blocks = redoneDoc.blocks,
                        tableOfContents = toc,
                        canUndo = undoStack.isNotEmpty(),
                        canRedo = redoStack.isNotEmpty()
                    )
                }
                persistCurrentDocument()
            }.onFailure { error ->
                sendEffect(EditorEffect.ShowError("Redo failed: ${error.message}"))
            }
        }
    }

    private fun requestFocus(blockId: BlockId, cursorPosition: Int) {
        updateState { copy(focusedBlockId = blockId, cursorPosition = cursorPosition) }
        sendEffect(EditorEffect.RequestFocusOnBlock(blockId, cursorPosition))
    }

    private fun changeTitle(newTitle: String) {
        updateState { copy(title = newTitle) }
        viewModelScope.launch {
            persistCurrentDocument()
        }
    }

    private fun saveExplicitly() {
        viewModelScope.launch {
            updateState { copy(isSaving = true) }
            val doc = currentDocument()
            val result = withContext(dispatcherProvider.io) {
                markdownRepository.saveDocument(doc)
            }
            updateState { copy(isSaving = false) }
            if (result.isSuccess) {
                sendEffect(EditorEffect.ShowToast("Document saved successfully"))
            } else {
                sendEffect(EditorEffect.ShowError("Failed to save document"))
            }
        }
    }

    private fun exportDocument(format: ExportFormat) {
        viewModelScope.launch {
            val doc = currentDocument()
            val state = uiState.value

            when (format) {
                ExportFormat.HTML -> {
                    exportHtmlUseCase.execute(doc, standalone = true)
                        .onSuccess { html ->
                            sendEffect(
                                EditorEffect.ShareContent(
                                    title = "${state.title}.html",
                                    content = html,
                                    mimeType = "text/html"
                                )
                            )
                        }
                        .onFailure { error ->
                            sendEffect(EditorEffect.ShowError(error.message ?: "Failed to export HTML"))
                        }
                }
                ExportFormat.PDF -> {
                    exportHtmlUseCase.execute(doc, standalone = true)
                        .onSuccess { html ->
                            sendEffect(
                                EditorEffect.PrintHtml(
                                    jobName = state.title,
                                    htmlContent = html
                                )
                            )
                        }
                        .onFailure { error ->
                            sendEffect(EditorEffect.ShowError(error.message ?: "Failed to generate PDF"))
                        }
                }
                ExportFormat.MARKDOWN -> {
                    sendEffect(
                        EditorEffect.ShareContent(
                            title = "${state.title}.md",
                            content = doc.rawContent,
                            mimeType = "text/markdown"
                        )
                    )
                }
            }
        }
    }

    private suspend fun persistCurrentDocument() {
        val doc = currentDocument()
        withContext(dispatcherProvider.io) {
            markdownRepository.saveDocument(doc)
        }
    }

    private fun currentDocument(): MarkdownDocument {
        val state = uiState.value
        return MarkdownDocument(
            id = state.documentId,
            title = state.title,
            blocks = state.blocks
        )
    }
}


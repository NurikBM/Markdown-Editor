package com.markdown.editor.presentation.editor

import androidx.lifecycle.viewModelScope
import com.markdown.editor.core.dispatcher.DispatcherProvider
import com.markdown.editor.domain.diff.DiffCalculator
import com.markdown.editor.domain.model.BlockId
import com.markdown.editor.domain.model.BlockType
import com.markdown.editor.domain.model.DocumentSnapshot
import com.markdown.editor.domain.model.ExportFormat
import com.markdown.editor.domain.model.FindMatch
import com.markdown.editor.domain.model.MarkdownBlock
import com.markdown.editor.domain.model.MarkdownDocument
import com.markdown.editor.domain.model.MarkdownFormatAction
import com.markdown.editor.domain.model.TableOfContentsItem
import java.util.UUID
import com.markdown.editor.domain.parser.MarkdownBlockParser
import com.markdown.editor.domain.repository.MarkdownRepository
import com.markdown.editor.domain.repository.SnapshotRepository
import com.markdown.editor.domain.usecase.ApplyFormattingUseCase
import com.markdown.editor.domain.usecase.ConvertDocumentUseCase
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
import com.markdown.editor.presentation.theme.AppTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Production MVI ViewModel managing block-based document state, split/merge keystrokes,
 * deterministic undo/redo history, in-editor navigation, search, and quick accessory formatting.
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
    private val replaceInDocumentUseCase: ReplaceInDocumentUseCase = ReplaceInDocumentUseCase(parser),
    private val applyFormattingUseCase: ApplyFormattingUseCase = ApplyFormattingUseCase(),
    private val convertDocumentUseCase: ConvertDocumentUseCase? = null
) : MviViewModel<EditorUiState, EditorIntent, EditorEffect>(EditorUiState()) {

    private val undoStack = ArrayDeque<DocumentSnapshot>()
    private val redoStack = ArrayDeque<DocumentSnapshot>()
    private var typingJob: Job? = null
    private val typingBaselineContent = mutableMapOf<BlockId, String>()

    init {
        viewModelScope.launch {
            markdownRepository.observeAllMetadata().collect { metadataList ->
                updateState { copy(recentDocuments = metadataList) }
            }
        }
    }

    override fun processIntent(intent: EditorIntent) {
        when (intent) {
            is EditorIntent.LoadDocument -> loadDocument(intent.documentId)
            is EditorIntent.UpdateBlock -> updateBlock(intent.blockId, intent.newContent)
            is EditorIntent.SplitBlock -> splitBlock(intent.blockId, intent.cursorPosition)
            is EditorIntent.MergeBlockWithPrevious -> mergeBlock(intent.blockId)
            is EditorIntent.RequestFocus -> requestFocus(intent.blockId, intent.cursorPosition, intent.selectionEnd)
            is EditorIntent.ChangeTitle -> changeTitle(intent.newTitle)
            is EditorIntent.SetViewMode -> setViewMode(intent.mode)
            is EditorIntent.ExportDocument -> exportDocument(intent.format)
            is EditorIntent.ToggleTableOfContents -> toggleTableOfContents(intent.visible)
            is EditorIntent.NavigateToHeading -> navigateToHeading(intent.item)
            is EditorIntent.ToggleFindReplace -> toggleFindReplace(intent.visible)
            is EditorIntent.SetSearchQuery -> setSearchQuery(intent.query)
            is EditorIntent.SetReplaceQuery -> setReplaceQuery(intent.query)
            is EditorIntent.SetCaseSensitive -> setCaseSensitive(intent.caseSensitive)
            is EditorIntent.ApplyFormatting -> applyFormatting(intent.action)
            is EditorIntent.SetTheme -> setTheme(intent.theme)
            is EditorIntent.ToggleDrawer -> toggleDrawer(intent.open)
            is EditorIntent.CreateNewDocument -> createNewDocument()
            is EditorIntent.DeleteDocument -> deleteDocument(intent.documentId)
            is EditorIntent.OpenExternalDocument -> openExternalDocument(intent.fileName, intent.content, intent.rawBytes)
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
                cursorPosition = 0,
                selectionEnd = 0
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
                val activeDoc = if (doc.blocks.isEmpty()) {
                    createInitialDocument(doc.id)
                } else {
                    doc
                }

                undoStack.clear()
                redoStack.clear()

                val toc = generateTableOfContentsUseCase(activeDoc.blocks)
                updateState {
                    copy(
                        documentId = activeDoc.id,
                        title = activeDoc.title,
                        blocks = activeDoc.blocks,
                        tableOfContents = toc,
                        isLoading = false,
                        canUndo = false,
                        canRedo = false,
                        isDrawerOpen = false,
                        focusedBlockId = activeDoc.blocks.firstOrNull()?.id,
                        cursorPosition = 0,
                        selectionEnd = 0
                    )
                }

                if (doc.blocks.isEmpty()) {
                    withContext(dispatcherProvider.io) {
                        markdownRepository.saveDocument(activeDoc)
                    }
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
                        isDrawerOpen = false,
                        focusedBlockId = defaultDoc.blocks.firstOrNull()?.id,
                        cursorPosition = 0,
                        selectionEnd = 0,
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
        val currentDoc = currentDocument()
        val blockIndex = currentDoc.blocks.indexOfFirst { it.id == blockId }
        if (blockIndex == -1) return

        val oldBlock = currentDoc.blocks[blockIndex]
        if (oldBlock.rawContent == newContent) return

        // 1. Preserve initial baseline before continuous typing started (for single undo snapshot)
        if (!typingBaselineContent.containsKey(blockId)) {
            typingBaselineContent[blockId] = oldBlock.rawContent
        }

        // 2. Immediately update in-memory state for zero-latency UI responsiveness
        val inMemoryUpdated = currentDoc.blocks.toMutableList().apply {
            set(blockIndex, oldBlock.copy(rawContent = newContent))
        }
        updateState { copy(blocks = inMemoryUpdated) }

        // 3. Debounce Myers diff, Room snapshot, Commonmark parsing, and persistence
        typingJob?.cancel()
        typingJob = viewModelScope.launch {
            delay(300L)
            flushTyping(blockId, newContent)
        }
    }

    private suspend fun flushTyping(blockId: BlockId, newContent: String) {
        val currentDoc = currentDocument()
        val blockIndex = currentDoc.blocks.indexOfFirst { it.id == blockId }
        if (blockIndex == -1) return

        val baselineContent = typingBaselineContent.remove(blockId) ?: newContent

        // Compute Myers diff between initial baseline and debounced final content
        if (baselineContent != newContent) {
            val diffResult = withContext(dispatcherProvider.diffAndParsing) {
                diffCalculator.computeDiff(baselineContent, newContent)
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
        }

        // Re-parse single block incrementally on diff/parsing dispatcher
        val updatedBlock = withContext(dispatcherProvider.diffAndParsing) {
            parser.parseBlock(newContent, blockId)
        }

        val updatedBlocks = currentDoc.blocks.toMutableList().apply {
            set(blockIndex, updatedBlock)
        }

        val toc = generateTableOfContentsUseCase(updatedBlocks)
        val matches = if (uiState.value.searchQuery.isNotEmpty()) {
            findInDocumentUseCase(updatedBlocks, uiState.value.searchQuery, uiState.value.isCaseSensitive)
        } else {
            emptyList()
        }

        updateState {
            copy(
                blocks = updatedBlocks,
                tableOfContents = toc,
                findMatches = matches,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }

        persistCurrentDocument()
    }

    private fun splitBlock(blockId: BlockId, cursorPosition: Int) {
        typingJob?.cancel()
        typingBaselineContent.clear()
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
                        cursorPosition = result.cursorPosition,
                        selectionEnd = result.cursorPosition
                    )
                }
                sendEffect(EditorEffect.RequestFocusOnBlock(result.newBlockId, result.cursorPosition))
                persistCurrentDocument()
            }
        }
    }

    private fun mergeBlock(blockId: BlockId) {
        typingJob?.cancel()
        typingBaselineContent.clear()
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
                        cursorPosition = result.cursorPosition,
                        selectionEnd = result.cursorPosition
                    )
                }
                sendEffect(EditorEffect.RequestFocusOnBlock(result.targetBlockId, result.cursorPosition))
                persistCurrentDocument()
            }
        }
    }

    private fun performUndo() {
        typingJob?.cancel()
        typingBaselineContent.clear()
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
        typingJob?.cancel()
        typingBaselineContent.clear()
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

    private fun requestFocus(blockId: BlockId, cursorPosition: Int, selectionEnd: Int = cursorPosition) {
        updateState {
            copy(
                focusedBlockId = blockId,
                cursorPosition = cursorPosition,
                selectionEnd = selectionEnd
            )
        }
        sendEffect(EditorEffect.RequestFocusOnBlock(blockId, cursorPosition))
    }

    private fun setTheme(theme: AppTheme) {
        updateState { copy(appTheme = theme) }
    }

    private fun applyFormatting(action: MarkdownFormatAction) {
        typingJob?.cancel()
        typingBaselineContent.clear()
        viewModelScope.launch {
            val currentDoc = currentDocument()
            val focusedId = uiState.value.focusedBlockId ?: currentDoc.blocks.firstOrNull()?.id ?: return@launch
            val blockIndex = currentDoc.blocks.indexOfFirst { it.id == focusedId }
            if (blockIndex == -1) return@launch

            val targetBlock = currentDoc.blocks[blockIndex]
            val formattingResult = applyFormattingUseCase(
                content = targetBlock.rawContent,
                selectionStart = uiState.value.cursorPosition,
                selectionEnd = uiState.value.selectionEnd,
                action = action
            )

            if (formattingResult.newContent == targetBlock.rawContent) return@launch

            val diffResult = withContext(dispatcherProvider.diffAndParsing) {
                diffCalculator.computeDiff(targetBlock.rawContent, formattingResult.newContent)
            }

            if (diffResult.hasChanges) {
                val snapshot = DocumentSnapshot(
                    documentId = currentDoc.id,
                    blockId = focusedId,
                    forwardDiff = diffResult.forwardDiff,
                    reverseDiff = diffResult.reverseDiff
                )
                undoStack.addLast(snapshot)
                redoStack.clear()
                withContext(dispatcherProvider.io) {
                    snapshotRepository.recordSnapshot(snapshot)
                }
            }

            val updatedBlock = withContext(dispatcherProvider.diffAndParsing) {
                parser.parseBlock(formattingResult.newContent, focusedId)
            }

            val updatedBlocks = currentDoc.blocks.toMutableList().apply {
                set(blockIndex, updatedBlock)
            }

            val toc = generateTableOfContentsUseCase(updatedBlocks)
            updateState {
                copy(
                    blocks = updatedBlocks,
                    tableOfContents = toc,
                    focusedBlockId = focusedId,
                    cursorPosition = formattingResult.cursorPosition,
                    selectionEnd = formattingResult.selectionEnd,
                    canUndo = undoStack.isNotEmpty(),
                    canRedo = redoStack.isNotEmpty()
                )
            }

            sendEffect(EditorEffect.RequestFocusOnBlock(focusedId, formattingResult.cursorPosition))
            persistCurrentDocument()
        }
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

    private fun toggleDrawer(open: Boolean?) {
        updateState { copy(isDrawerOpen = open ?: !isDrawerOpen) }
    }

    private fun createNewDocument() {
        viewModelScope.launch {
            typingJob?.cancel()
            val newId = "doc_${System.currentTimeMillis()}"
            val initialDoc = createInitialEmptyDocument(newId)
            withContext(dispatcherProvider.io) {
                markdownRepository.saveDocument(initialDoc)
            }
            undoStack.clear()
            redoStack.clear()
            val toc = generateTableOfContentsUseCase(initialDoc.blocks)
            updateState {
                copy(
                    documentId = initialDoc.id,
                    title = initialDoc.title,
                    blocks = initialDoc.blocks,
                    tableOfContents = toc,
                    isLoading = false,
                    canUndo = false,
                    canRedo = false,
                    isDrawerOpen = false,
                    focusedBlockId = initialDoc.blocks.firstOrNull()?.id,
                    cursorPosition = 0,
                    selectionEnd = 0
                )
            }
            sendEffect(EditorEffect.ShowToast("Created new document"))
        }
    }

    private fun createInitialEmptyDocument(id: String): MarkdownDocument {
        val block = MarkdownBlock(
            id = BlockId(UUID.randomUUID().toString()),
            rawContent = "",
            type = BlockType.Paragraph
        )
        return MarkdownDocument(
            id = id,
            title = "New Document",
            blocks = listOf(block)
        )
    }

    private fun deleteDocument(documentId: String) {
        viewModelScope.launch {
            withContext(dispatcherProvider.io) {
                markdownRepository.deleteDocument(documentId)
            }
            sendEffect(EditorEffect.ShowToast("Document deleted"))
            if (uiState.value.documentId == documentId) {
                val remaining = uiState.value.recentDocuments.filter { it.id != documentId }
                if (remaining.isNotEmpty()) {
                    loadDocument(remaining.first().id)
                } else {
                    createNewDocument()
                }
            }
        }
    }

    private fun openExternalDocument(fileName: String, content: String, rawBytes: ByteArray? = null) {
        val lowerName = fileName.lowercase()
        val extension = lowerName.substringAfterLast('.', "")

        val imageExtensions = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "ico", "heic")
        val otherBinaryExtensions = setOf("zip", "rar", "7z", "tar", "gz", "mp3", "wav", "ogg", "m4a", "flac", "mp4", "mkv", "avi", "mov", "apk", "exe", "bin")

        val isConvertible = convertDocumentUseCase?.canConvert(fileName) == true

        when {
            imageExtensions.contains(extension) -> {
                sendEffect(EditorEffect.ShowError("Images cannot be opened as text. Please select a Markdown (.md) or document file."))
                return
            }
            otherBinaryExtensions.contains(extension) -> {
                sendEffect(EditorEffect.ShowError("Binary files cannot be opened as text. Please select a Markdown (.md) or document file."))
                return
            }
            !isConvertible && extension in setOf("doc", "xls", "ppt") -> {
                sendEffect(EditorEffect.ShowError("Legacy office formats (.${extension}) are not supported. Please use modern .docx or .xlsx files."))
                return
            }
            !isConvertible && content.take(4096).contains('\u0000') -> {
                sendEffect(EditorEffect.ShowError("This file contains binary data and cannot be opened as text."))
                return
            }
        }

        viewModelScope.launch {
            typingJob?.cancel()
            updateState { copy(isLoading = true) }

            val inputStream = rawBytes?.inputStream() ?: content.byteInputStream(Charsets.UTF_8)
            val effectiveContent: String
            val effectiveTitle: String
            var warningNotice: String? = null

            if (isConvertible) {
                try {
                    val converted = withContext(dispatcherProvider.io) {
                        convertDocumentUseCase(fileName, inputStream)
                    }
                    effectiveContent = converted.markdownContent
                    effectiveTitle = converted.title
                    if (converted.warningMessage != null) {
                        warningNotice = converted.warningMessage
                    }
                } catch (e: Exception) {
                    updateState { copy(isLoading = false) }
                    sendEffect(EditorEffect.ShowError("Conversion failed: ${e.message ?: "Unsupported structure"}"))
                    return@launch
                }
            } else {
                effectiveContent = content
                effectiveTitle = fileName.removeSuffix(".md").removeSuffix(".markdown").removeSuffix(".txt")
            }

            val docId = "doc_${System.currentTimeMillis()}"
            val parsedDoc = withContext(dispatcherProvider.diffAndParsing) {
                parser.parseDocument(effectiveContent, docId, effectiveTitle)
            }
            val cleanBlocks = if (parsedDoc.blocks.isEmpty()) {
                listOf(MarkdownBlock(id = BlockId(UUID.randomUUID().toString()), rawContent = "", type = BlockType.Paragraph))
            } else {
                parsedDoc.blocks
            }
            val doc = MarkdownDocument(id = docId, title = effectiveTitle, blocks = cleanBlocks)
            withContext(dispatcherProvider.io) {
                markdownRepository.saveDocument(doc)
            }
            undoStack.clear()
            redoStack.clear()
            val toc = withContext(dispatcherProvider.diffAndParsing) {
                generateTableOfContentsUseCase(cleanBlocks)
            }
            updateState {
                copy(
                    documentId = doc.id,
                    title = doc.title,
                    blocks = doc.blocks,
                    tableOfContents = toc,
                    isLoading = false,
                    canUndo = false,
                    canRedo = false,
                    isDrawerOpen = false,
                    focusedBlockId = cleanBlocks.firstOrNull()?.id,
                    cursorPosition = 0,
                    selectionEnd = 0
                )
            }
            if (warningNotice != null) {
                sendEffect(EditorEffect.ShowToast(warningNotice))
            } else {
                sendEffect(EditorEffect.ShowToast("Opened $fileName"))
            }
        }
    }
}

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
import com.markdown.editor.domain.usecase.ExportHtmlUseCase
import com.markdown.editor.domain.usecase.MergeBlockUseCase
import com.markdown.editor.domain.usecase.MergeResult
import com.markdown.editor.domain.usecase.RedoBlockUseCase
import com.markdown.editor.domain.usecase.SplitBlockUseCase
import com.markdown.editor.domain.usecase.SplitResult
import com.markdown.editor.domain.usecase.UndoBlockUseCase
import com.markdown.editor.presentation.mvi.MviViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Production MVI ViewModel managing block-based document state, split/merge keystrokes,
 * and deterministic undo/redo history.
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
    private val exportHtmlUseCase: ExportHtmlUseCase
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
                undoStack.clear()
                redoStack.clear()
                updateState {
                    copy(
                        documentId = doc.id,
                        title = doc.title,
                        blocks = blocks,
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
                undoStack.clear()
                redoStack.clear()
                updateState {
                    copy(
                        documentId = defaultDoc.id,
                        title = defaultDoc.title,
                        blocks = defaultDoc.blocks,
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

            updateState {
                copy(
                    blocks = updatedBlocks,
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
                updateState {
                    copy(
                        blocks = result.document.blocks,
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
                updateState {
                    copy(
                        blocks = result.document.blocks,
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
                redoStack.addLast(snapshot)
                updateState {
                    copy(
                        blocks = undoneDoc.blocks,
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
                undoStack.addLast(snapshot)
                updateState {
                    copy(
                        blocks = redoneDoc.blocks,
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


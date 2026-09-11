package com.markdown.editor.presentation.editor

import androidx.compose.runtime.Immutable
import com.markdown.editor.domain.model.BlockId
import com.markdown.editor.domain.model.MarkdownBlock
import com.markdown.editor.presentation.mvi.UiEffect
import com.markdown.editor.presentation.mvi.UiIntent
import com.markdown.editor.presentation.mvi.UiState

/**
 * Display modes for the Markdown editor.
 */
enum class EditorViewMode {
    EDITOR_ONLY,
    SPLIT_VIEW,
    PREVIEW_ONLY
}

/**
 * Immutable UI state representing the Markdown editor.
 */
@Immutable
data class EditorUiState(
    val documentId: String = "",
    val title: String = "Untitled",
    val blocks: List<MarkdownBlock> = emptyList(),
    val viewMode: EditorViewMode = EditorViewMode.EDITOR_ONLY,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val focusedBlockId: BlockId? = null,
    val cursorPosition: Int = 0,
    val errorMessage: String? = null
) : UiState

/**
 * Sealed hierarchy of user intents processed by [EditorViewModel].
 */
sealed interface EditorIntent : UiIntent {
    data class LoadDocument(val documentId: String) : EditorIntent
    data class UpdateBlock(val blockId: BlockId, val newContent: String) : EditorIntent
    data class SplitBlock(val blockId: BlockId, val cursorPosition: Int) : EditorIntent
    data class MergeBlockWithPrevious(val blockId: BlockId) : EditorIntent
    data class RequestFocus(val blockId: BlockId, val cursorPosition: Int = 0) : EditorIntent
    data class ChangeTitle(val newTitle: String) : EditorIntent
    data class SetViewMode(val mode: EditorViewMode) : EditorIntent
    data object TogglePreview : EditorIntent
    data object Undo : EditorIntent
    data object Redo : EditorIntent
    data object SaveExplicitly : EditorIntent
    data class ExportDocument(val format: com.markdown.editor.domain.model.ExportFormat) : EditorIntent
}

/**
 * One-shot UI effects emitted by [EditorViewModel].
 */
sealed interface EditorEffect : UiEffect {
    data class RequestFocusOnBlock(val blockId: BlockId, val cursorPosition: Int = 0) : EditorEffect
    data class ShowToast(val message: String) : EditorEffect
    data class ShowError(val message: String) : EditorEffect
    data class PrintHtml(val jobName: String, val htmlContent: String) : EditorEffect
    data class ShareContent(val title: String, val content: String, val mimeType: String) : EditorEffect
}


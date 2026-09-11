package com.markdown.editor.presentation.editor

import androidx.compose.runtime.Immutable
import com.markdown.editor.domain.model.BlockId
import com.markdown.editor.domain.model.MarkdownBlock
import com.markdown.editor.domain.model.FindMatch
import com.markdown.editor.domain.model.TableOfContentsItem
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
    val tableOfContents: List<TableOfContentsItem> = emptyList(),
    val isTableOfContentsVisible: Boolean = false,
    val isFindReplaceVisible: Boolean = false,
    val searchQuery: String = "",
    val replaceQuery: String = "",
    val findMatches: List<FindMatch> = emptyList(),
    val currentMatchIndex: Int = -1,
    val isCaseSensitive: Boolean = false,
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
    data class ToggleTableOfContents(val visible: Boolean? = null) : EditorIntent
    data class NavigateToHeading(val item: TableOfContentsItem) : EditorIntent
    data class ToggleFindReplace(val visible: Boolean? = null) : EditorIntent
    data class SetSearchQuery(val query: String) : EditorIntent
    data class SetReplaceQuery(val query: String) : EditorIntent
    data class SetCaseSensitive(val caseSensitive: Boolean) : EditorIntent
    data object FindNextMatch : EditorIntent
    data object FindPreviousMatch : EditorIntent
    data object ReplaceCurrentMatch : EditorIntent
    data object ReplaceAllMatches : EditorIntent
}

/**
 * One-shot UI effects emitted by [EditorViewModel].
 */
sealed interface EditorEffect : UiEffect {
    data class RequestFocusOnBlock(val blockId: BlockId, val cursorPosition: Int = 0) : EditorEffect
    data class ScrollToBlock(val blockIndex: Int, val blockId: BlockId) : EditorEffect
    data class ShowToast(val message: String) : EditorEffect
    data class ShowError(val message: String) : EditorEffect
    data class PrintHtml(val jobName: String, val htmlContent: String) : EditorEffect
    data class ShareContent(val title: String, val content: String, val mimeType: String) : EditorEffect
}


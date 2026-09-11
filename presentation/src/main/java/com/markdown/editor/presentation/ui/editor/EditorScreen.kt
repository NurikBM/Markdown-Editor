package com.markdown.editor.presentation.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.markdown.editor.domain.model.BlockId
import com.markdown.editor.domain.model.MarkdownBlock
import com.markdown.editor.presentation.editor.EditorEffect
import com.markdown.editor.presentation.editor.EditorIntent
import com.markdown.editor.presentation.editor.EditorUiState
import com.markdown.editor.presentation.editor.EditorViewMode
import com.markdown.editor.presentation.ui.block.MarkdownBlockItem
import com.markdown.editor.presentation.ui.preview.MarkdownPreviewPane
import com.markdown.editor.presentation.ui.search.FindReplaceBar
import com.markdown.editor.presentation.ui.sync.rememberSynchronizedScroll
import com.markdown.editor.presentation.ui.toc.TableOfContentsSheet
import kotlinx.coroutines.flow.Flow

/**
 * Root editor screen hosting the block-based LazyColumn editor, rich preview, and TopAppBar.
 * Enforces key-based recomposition isolation per [BlockId] and supports Split-View mode.
 */
@Composable
fun EditorScreen(
    state: EditorUiState,
    effects: Flow<EditorEffect>,
    onIntent: (EditorIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val editorListState = rememberLazyListState()
    val previewListState = rememberLazyListState()

    rememberSynchronizedScroll(
        editorListState = editorListState,
        previewListState = previewListState,
        enabled = state.viewMode == EditorViewMode.SPLIT_VIEW
    )

    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is EditorEffect.ShowToast -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                is EditorEffect.ShowError -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                is EditorEffect.RequestFocusOnBlock -> {
                    // Handled reactively via state.focusedBlockId
                }
                is EditorEffect.ScrollToBlock -> {
                    editorListState.animateScrollToItem(effect.blockIndex)
                    previewListState.animateScrollToItem(effect.blockIndex)
                }
                is EditorEffect.PrintHtml -> {
                    com.markdown.editor.presentation.export.AndroidExportHelper.printHtml(
                        context = context,
                        jobName = effect.jobName,
                        htmlContent = effect.htmlContent,
                        onError = { error ->
                            // Display error via snackbar
                        }
                    )
                }
                is EditorEffect.ShareContent -> {
                    com.markdown.editor.presentation.export.AndroidExportHelper.shareText(
                        context = context,
                        title = effect.title,
                        content = effect.content,
                        mimeType = effect.mimeType
                    )
                }
            }
        }
    }

    Scaffold(
        topBar = {
            EditorTopBar(
                state = state,
                onIntent = onIntent
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        modifier = modifier.imePadding()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.isFindReplaceVisible) {
                FindReplaceBar(
                    searchQuery = state.searchQuery,
                    replaceQuery = state.replaceQuery,
                    currentMatchIndex = state.currentMatchIndex,
                    totalMatches = state.findMatches.size,
                    isCaseSensitive = state.isCaseSensitive,
                    onSearchQueryChange = { q -> onIntent(EditorIntent.SetSearchQuery(q)) },
                    onReplaceQueryChange = { q -> onIntent(EditorIntent.SetReplaceQuery(q)) },
                    onNextMatch = { onIntent(EditorIntent.FindNextMatch) },
                    onPreviousMatch = { onIntent(EditorIntent.FindPreviousMatch) },
                    onToggleCaseSensitive = { cs -> onIntent(EditorIntent.SetCaseSensitive(cs)) },
                    onReplace = { onIntent(EditorIntent.ReplaceCurrentMatch) },
                    onReplaceAll = { onIntent(EditorIntent.ReplaceAllMatches) },
                    onClose = { onIntent(EditorIntent.ToggleFindReplace(visible = false)) }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else {
                    val activeMatch = state.findMatches.getOrNull(state.currentMatchIndex)
                    when (state.viewMode) {
                        EditorViewMode.EDITOR_ONLY -> {
                            EditorPane(
                                blocks = state.blocks,
                                focusedBlockId = state.focusedBlockId,
                                cursorPosition = state.cursorPosition,
                                searchQuery = state.searchQuery,
                                isCaseSensitive = state.isCaseSensitive,
                                activeMatch = activeMatch,
                                onIntent = onIntent,
                                listState = editorListState
                            )
                        }
                        EditorViewMode.PREVIEW_ONLY -> {
                            MarkdownPreviewPane(
                                blocks = state.blocks,
                                searchQuery = state.searchQuery,
                                isCaseSensitive = state.isCaseSensitive,
                                listState = previewListState
                            )
                        }
                        EditorViewMode.SPLIT_VIEW -> {
                            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                if (maxWidth >= 600.dp) {
                                    Row(modifier = Modifier.fillMaxSize()) {
                                        EditorPane(
                                            blocks = state.blocks,
                                            focusedBlockId = state.focusedBlockId,
                                            cursorPosition = state.cursorPosition,
                                            searchQuery = state.searchQuery,
                                            isCaseSensitive = state.isCaseSensitive,
                                            activeMatch = activeMatch,
                                            onIntent = onIntent,
                                            listState = editorListState,
                                            modifier = Modifier.weight(1f)
                                        )
                                        VerticalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant,
                                            thickness = 1.dp
                                        )
                                        MarkdownPreviewPane(
                                            blocks = state.blocks,
                                            searchQuery = state.searchQuery,
                                            isCaseSensitive = state.isCaseSensitive,
                                            listState = previewListState,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                } else {
                                    Column(modifier = Modifier.fillMaxSize()) {
                                        EditorPane(
                                            blocks = state.blocks,
                                            focusedBlockId = state.focusedBlockId,
                                            cursorPosition = state.cursorPosition,
                                            searchQuery = state.searchQuery,
                                            isCaseSensitive = state.isCaseSensitive,
                                            activeMatch = activeMatch,
                                            onIntent = onIntent,
                                            listState = editorListState,
                                            modifier = Modifier.weight(1f)
                                        )
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant,
                                            thickness = 1.dp
                                        )
                                        MarkdownPreviewPane(
                                            blocks = state.blocks,
                                            searchQuery = state.searchQuery,
                                            isCaseSensitive = state.isCaseSensitive,
                                            listState = previewListState,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (state.viewMode != EditorViewMode.PREVIEW_ONLY) {
                com.markdown.editor.presentation.ui.toolbar.MarkdownAccessoryToolbar(
                    onIntent = onIntent
                )
            }
        }
    }

    if (state.isTableOfContentsVisible) {
        TableOfContentsSheet(
            items = state.tableOfContents,
            onItemClick = { item -> onIntent(EditorIntent.NavigateToHeading(item)) },
            onDismiss = { onIntent(EditorIntent.ToggleTableOfContents(visible = false)) }
        )
    }
}

@Composable
private fun EditorPane(
    blocks: List<MarkdownBlock>,
    focusedBlockId: BlockId?,
    cursorPosition: Int,
    searchQuery: String,
    isCaseSensitive: Boolean,
    activeMatch: com.markdown.editor.domain.model.FindMatch?,
    onIntent: (EditorIntent) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize()
    ) {
        items(
            items = blocks,
            key = { it.id.value }
        ) { block ->
            val matchRange = if (activeMatch?.blockId == block.id) {
                activeMatch.startIndex..activeMatch.endIndex
            } else {
                null
            }
            MarkdownBlockItem(
                block = block,
                isFocused = focusedBlockId == block.id,
                requestedCursorPosition = if (focusedBlockId == block.id) cursorPosition else null,
                searchQuery = searchQuery,
                isCaseSensitive = isCaseSensitive,
                activeMatchRange = matchRange,
                onIntent = onIntent
            )
        }
    }
}


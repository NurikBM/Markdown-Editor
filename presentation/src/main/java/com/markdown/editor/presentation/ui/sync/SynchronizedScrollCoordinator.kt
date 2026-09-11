package com.markdown.editor.presentation.ui.sync

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow

/**
 * Coordinates bidirectional scroll synchronization between [editorListState] and [previewListState].
 * Utilizes Compose [LazyListState.isScrollInProgress] to avoid recursive scroll update loops.
 */
@Composable
fun rememberSynchronizedScroll(
    editorListState: LazyListState,
    previewListState: LazyListState,
    enabled: Boolean = true
) {
    if (!enabled) return

    // Sync from editor to preview when editor is actively scrolled
    LaunchedEffect(editorListState.isScrollInProgress) {
        if (editorListState.isScrollInProgress) {
            snapshotFlow {
                Pair(editorListState.firstVisibleItemIndex, editorListState.firstVisibleItemScrollOffset)
            }.collect { (index, offset) ->
                if (!previewListState.isScrollInProgress) {
                    previewListState.scrollToItem(index, offset)
                }
            }
        }
    }

    // Sync from preview to editor when preview is actively scrolled
    LaunchedEffect(previewListState.isScrollInProgress) {
        if (previewListState.isScrollInProgress) {
            snapshotFlow {
                Pair(previewListState.firstVisibleItemIndex, previewListState.firstVisibleItemScrollOffset)
            }.collect { (index, offset) ->
                if (!editorListState.isScrollInProgress) {
                    editorListState.scrollToItem(index, offset)
                }
            }
        }
    }
}

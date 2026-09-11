package com.markdown.editor.presentation.ui.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.markdown.editor.domain.model.MarkdownBlock

import androidx.compose.foundation.text.selection.SelectionContainer

/**
 * Preview pane displaying rendered Markdown blocks in a keyed LazyColumn.
 * Wrapped in [SelectionContainer] to enable native text selection, copying, and sharing in preview mode.
 */
@Composable
fun MarkdownPreviewPane(
    blocks: List<MarkdownBlock>,
    modifier: Modifier = Modifier,
    searchQuery: String = "",
    isCaseSensitive: Boolean = false,
    listState: LazyListState = rememberLazyListState()
) {
    if (blocks.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No content to preview",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            )
        }
    } else {
        SelectionContainer(modifier = modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 8.dp)
            ) {
                items(
                    items = blocks,
                    key = { it.id.value }
                ) { block ->
                    MarkdownPreviewBlockItem(
                        block = block,
                        searchQuery = searchQuery,
                        isCaseSensitive = isCaseSensitive
                    )
                }
            }
        }
    }
}


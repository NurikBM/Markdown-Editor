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

/**
 * Preview pane displaying rendered Markdown blocks in a keyed LazyColumn.
 */
@Composable
fun MarkdownPreviewPane(
    blocks: List<MarkdownBlock>,
    modifier: Modifier = Modifier,
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
        LazyColumn(
            state = listState,
            modifier = modifier
                .fillMaxSize()
                .padding(vertical = 8.dp)
        ) {
            items(
                items = blocks,
                key = { it.id.value }
            ) { block ->
                MarkdownPreviewBlockItem(block = block)
            }
        }
    }
}

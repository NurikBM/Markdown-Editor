package com.markdown.editor.presentation.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * State representing search and replace inputs and match positions.
 */
data class FindReplaceState(
    val searchQuery: String = "",
    val replaceQuery: String = "",
    val currentMatchIndex: Int = 0,
    val totalMatches: Int = 0,
    val isCaseSensitive: Boolean = false
)

/**
 * Event callbacks for the Find & Replace bar.
 */
data class FindReplaceActions(
    val onSearchQueryChange: (String) -> Unit,
    val onReplaceQueryChange: (String) -> Unit,
    val onNextMatch: () -> Unit,
    val onPreviousMatch: () -> Unit,
    val onToggleCaseSensitive: (Boolean) -> Unit,
    val onReplace: () -> Unit,
    val onReplaceAll: () -> Unit,
    val onClose: () -> Unit
)

/**
 * Floating / anchored search and replace accessory bar in Material 3.
 * Supports real-time search, match stepping (next/prev), case sensitivity toggle, and bulk replacement.
 */
@Composable
fun FindReplaceBar(
    state: FindReplaceState,
    actions: FindReplaceActions,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            FindRow(state = state, actions = actions)
            Spacer(modifier = Modifier.height(6.dp))
            ReplaceRow(state = state, actions = actions)
        }
    }
}

@Composable
private fun FindRow(
    state: FindReplaceState,
    actions: FindReplaceActions,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(start = 4.dp, end = 8.dp)
                .size(20.dp)
        )

        SearchInputField(
            query = state.searchQuery,
            onQueryChange = actions.onSearchQueryChange,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(6.dp))

        MatchCounterBadge(
            searchQuery = state.searchQuery,
            currentMatchIndex = state.currentMatchIndex,
            totalMatches = state.totalMatches
        )

        IconButton(
            onClick = actions.onPreviousMatch,
            enabled = state.totalMatches > 0,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Previous match",
                tint = if (state.totalMatches > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.size(20.dp)
            )
        }

        IconButton(
            onClick = actions.onNextMatch,
            enabled = state.totalMatches > 0,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Next match",
                tint = if (state.totalMatches > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.size(20.dp)
            )
        }

        CaseSensitivityButton(
            isCaseSensitive = state.isCaseSensitive,
            onToggle = { actions.onToggleCaseSensitive(!state.isCaseSensitive) }
        )

        IconButton(
            onClick = actions.onClose,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SearchInputField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        if (query.isEmpty()) {
            Text(
                text = "Find in document...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun MatchCounterBadge(
    searchQuery: String,
    currentMatchIndex: Int,
    totalMatches: Int
) {
    val matchCountText = when {
        searchQuery.isEmpty() -> ""
        totalMatches == 0 -> "0/0"
        else -> "${currentMatchIndex + 1}/$totalMatches"
    }

    if (matchCountText.isNotEmpty()) {
        Text(
            text = matchCountText,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (totalMatches > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun CaseSensitivityButton(
    isCaseSensitive: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isCaseSensitive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        modifier = Modifier.padding(horizontal = 2.dp)
    ) {
        IconButton(
            onClick = onToggle,
            modifier = Modifier.size(32.dp)
        ) {
            Text(
                text = "Aa",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = if (isCaseSensitive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReplaceRow(
    state: FindReplaceState,
    actions: FindReplaceActions,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.Default.FindReplace,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier
                .padding(start = 4.dp, end = 8.dp)
                .size(20.dp)
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            if (state.replaceQuery.isEmpty()) {
                Text(
                    text = "Replace with...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            BasicTextField(
                value = state.replaceQuery,
                onValueChange = actions.onReplaceQueryChange,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        TextButton(
            onClick = actions.onReplace,
            enabled = state.totalMatches > 0,
            modifier = Modifier.height(32.dp)
        ) {
            Text(
                text = "Replace",
                style = MaterialTheme.typography.labelMedium
            )
        }

        TextButton(
            onClick = actions.onReplaceAll,
            enabled = state.totalMatches > 0,
            modifier = Modifier.height(32.dp)
        ) {
            Text(
                text = "All",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

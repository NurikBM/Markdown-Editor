package com.markdown.editor.presentation.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.markdown.editor.presentation.editor.EditorEffect
import com.markdown.editor.presentation.editor.EditorIntent
import com.markdown.editor.presentation.editor.EditorUiState
import com.markdown.editor.presentation.ui.block.MarkdownBlockItem
import kotlinx.coroutines.flow.Flow

/**
 * Root editor screen hosting the block-based LazyColumn editor and TopAppBar.
 * Enforces key-based recomposition isolation per [BlockId].
 */
@Composable
fun EditorScreen(
    state: EditorUiState,
    effects: Flow<EditorEffect>,
    onIntent: (EditorIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }

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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = state.blocks,
                        key = { it.id.value }
                    ) { block ->
                        MarkdownBlockItem(
                            block = block,
                            isFocused = state.focusedBlockId == block.id,
                            requestedCursorPosition = if (state.focusedBlockId == block.id) state.cursorPosition else null,
                            onIntent = onIntent
                        )
                    }
                }
            }
        }
    }
}


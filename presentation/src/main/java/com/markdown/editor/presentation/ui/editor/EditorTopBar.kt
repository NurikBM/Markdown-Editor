package com.markdown.editor.presentation.ui.editor

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material.icons.filled.Visibility
import com.markdown.editor.presentation.editor.EditorViewMode
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.markdown.editor.presentation.editor.EditorIntent
import com.markdown.editor.presentation.editor.EditorUiState

/**
 * Top bar for the editor providing title editing, Undo, Redo, and Save actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorTopBar(
    state: EditorUiState,
    onIntent: (EditorIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            BasicTextField(
                value = state.title,
                onValueChange = { newTitle -> onIntent(EditorIntent.ChangeTitle(newTitle)) },
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        actions = {
            IconButton(onClick = { onIntent(EditorIntent.TogglePreview) }) {
                val (icon, desc) = when (state.viewMode) {
                    EditorViewMode.EDITOR_ONLY -> Icons.Default.Edit to "Mode: Editor (Tap to switch)"
                    EditorViewMode.SPLIT_VIEW -> Icons.Default.VerticalSplit to "Mode: Split View (Tap to switch)"
                    EditorViewMode.PREVIEW_ONLY -> Icons.Default.Visibility to "Mode: Preview (Tap to switch)"
                }
                Icon(
                    imageVector = icon,
                    contentDescription = desc,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            IconButton(
                onClick = { onIntent(EditorIntent.Undo) },
                enabled = state.canUndo
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                    tint = if (state.canUndo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }

            IconButton(
                onClick = { onIntent(EditorIntent.Redo) },
                enabled = state.canRedo
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Redo,
                    contentDescription = "Redo",
                    tint = if (state.canRedo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }

            if (state.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                IconButton(onClick = { onIntent(EditorIntent.SaveExplicitly) }) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Save document",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            var showMenu by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            androidx.compose.foundation.layout.Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Export & Actions",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    androidx.compose.material3.DropdownMenuItem(
                        text = { androidx.compose.material3.Text("Print / Export PDF") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Print,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            showMenu = false
                            onIntent(EditorIntent.ExportDocument(com.markdown.editor.domain.model.ExportFormat.PDF))
                        }
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { androidx.compose.material3.Text("Export as HTML") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            showMenu = false
                            onIntent(EditorIntent.ExportDocument(com.markdown.editor.domain.model.ExportFormat.HTML))
                        }
                    )
                    androidx.compose.material3.DropdownMenuItem(
                        text = { androidx.compose.material3.Text("Share Markdown") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null
                            )
                        },
                        onClick = {
                            showMenu = false
                            onIntent(EditorIntent.ExportDocument(com.markdown.editor.domain.model.ExportFormat.MARKDOWN))
                        }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
    )
}


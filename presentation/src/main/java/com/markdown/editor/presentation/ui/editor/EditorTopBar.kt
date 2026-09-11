package com.markdown.editor.presentation.ui.editor

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Toc
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Nightlife
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.markdown.editor.domain.model.ExportFormat
import com.markdown.editor.presentation.editor.EditorIntent
import com.markdown.editor.presentation.editor.EditorUiState
import com.markdown.editor.presentation.editor.EditorViewMode
import com.markdown.editor.presentation.theme.AppTheme

/**
 * Adaptive Top bar for the editor.
 * On compact mobile portrait screens (< 600dp width), secondary actions collapse into the overflow
 * menu so that the document title has abundant space and remains easily editable.
 * On wider/tablet/landscape screens (>= 600dp), actions are laid out directly in the top bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorTopBar(
    state: EditorUiState,
    onIntent: (EditorIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val isCompact = configuration.screenWidthDp < 600
    var showMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            val scrollState = rememberScrollState()
            var titleValue by remember(state.documentId) {
                mutableStateOf(TextFieldValue(state.title, TextRange(state.title.length)))
            }

            // Sync with external state changes (e.g. document loaded or updated externally)
            LaunchedEffect(state.title) {
                if (state.title != titleValue.text) {
                    titleValue = titleValue.copy(text = state.title)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = titleValue,
                    onValueChange = { newValue ->
                        titleValue = newValue
                        if (newValue.text != state.title) {
                            onIntent(EditorIntent.ChangeTitle(newValue.text))
                        }
                    },
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    modifier = Modifier.wrapContentWidth(),
                    decorationBox = { innerTextField ->
                        if (titleValue.text.isEmpty()) {
                            Text(
                                text = "Document Title",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            )
                        }
                        innerTextField()
                    }
                )
            }
        },
        actions = {
            // Find / Replace
            IconButton(onClick = { onIntent(EditorIntent.ToggleFindReplace()) }) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Find and Replace",
                    tint = if (state.isFindReplaceVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }

            // View Mode switcher (always visible for quick access)
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

            // Actions displayed inline ONLY on wider screens (>= 600dp)
            if (!isCompact) {
                IconButton(onClick = { onIntent(EditorIntent.ToggleTableOfContents()) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Toc,
                        contentDescription = "Table of Contents",
                        tint = MaterialTheme.colorScheme.onSurface
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
            }

            // Overflow Menu
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More Actions & Settings",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    // In compact mode, show Undo, Redo, ToC, and Save in dropdown
                    if (isCompact) {
                        DropdownMenuItem(
                            text = { Text("Undo") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Undo, null) },
                            enabled = state.canUndo,
                            onClick = {
                                showMenu = false
                                onIntent(EditorIntent.Undo)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Redo") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Redo, null) },
                            enabled = state.canRedo,
                            onClick = {
                                showMenu = false
                                onIntent(EditorIntent.Redo)
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Table of Contents") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Toc, null) },
                            onClick = {
                                showMenu = false
                                onIntent(EditorIntent.ToggleTableOfContents())
                            }
                        )

                        DropdownMenuItem(
                            text = { Text(if (state.isSaving) "Saving..." else "Save Document") },
                            leadingIcon = { Icon(Icons.Default.Save, null) },
                            enabled = !state.isSaving,
                            onClick = {
                                showMenu = false
                                onIntent(EditorIntent.SaveExplicitly)
                            }
                        )

                        HorizontalDivider()
                    }

                    // Export section
                    DropdownMenuItem(
                        text = { Text("Print / Export PDF") },
                        leadingIcon = { Icon(Icons.Default.Print, null) },
                        onClick = {
                            showMenu = false
                            onIntent(EditorIntent.ExportDocument(ExportFormat.PDF))
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Export as HTML") },
                        leadingIcon = { Icon(Icons.Default.Code, null) },
                        onClick = {
                            showMenu = false
                            onIntent(EditorIntent.ExportDocument(ExportFormat.HTML))
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Share Markdown") },
                        leadingIcon = { Icon(Icons.Default.Share, null) },
                        onClick = {
                            showMenu = false
                            onIntent(EditorIntent.ExportDocument(ExportFormat.MARKDOWN))
                        }
                    )

                    HorizontalDivider()

                    // Theme section
                    DropdownMenuItem(
                        text = { Text("Theme: System") },
                        leadingIcon = { Icon(Icons.Default.Palette, null) },
                        trailingIcon = if (state.appTheme == AppTheme.SYSTEM) { { Icon(Icons.Default.Check, null) } } else null,
                        onClick = {
                            showMenu = false
                            onIntent(EditorIntent.SetTheme(AppTheme.SYSTEM))
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Theme: Light") },
                        leadingIcon = { Icon(Icons.Default.LightMode, null) },
                        trailingIcon = if (state.appTheme == AppTheme.LIGHT) { { Icon(Icons.Default.Check, null) } } else null,
                        onClick = {
                            showMenu = false
                            onIntent(EditorIntent.SetTheme(AppTheme.LIGHT))
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Theme: Dark") },
                        leadingIcon = { Icon(Icons.Default.DarkMode, null) },
                        trailingIcon = if (state.appTheme == AppTheme.DARK) { { Icon(Icons.Default.Check, null) } } else null,
                        onClick = {
                            showMenu = false
                            onIntent(EditorIntent.SetTheme(AppTheme.DARK))
                        }
                    )

                    DropdownMenuItem(
                        text = { Text("Theme: AMOLED Black") },
                        leadingIcon = { Icon(Icons.Default.Nightlife, null) },
                        trailingIcon = if (state.appTheme == AppTheme.AMOLED) { { Icon(Icons.Default.Check, null) } } else null,
                        onClick = {
                            showMenu = false
                            onIntent(EditorIntent.SetTheme(AppTheme.AMOLED))
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

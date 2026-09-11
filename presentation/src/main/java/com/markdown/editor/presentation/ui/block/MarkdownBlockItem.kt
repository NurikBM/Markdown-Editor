package com.markdown.editor.presentation.ui.block

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.markdown.editor.domain.model.BlockId
import com.markdown.editor.domain.model.BlockType
import com.markdown.editor.domain.model.MarkdownBlock
import com.markdown.editor.domain.syntax.RegexCodeSyntaxTokenizer
import com.markdown.editor.presentation.editor.EditorIntent
import com.markdown.editor.presentation.syntax.CodeSyntaxVisualTransformation

/**
 * Keyed block-level Composable ensuring isolated recomposition per [BlockId].
 * Uses a single, unified BasicTextField to prevent IME / keyboard destruction
 * during block type transitions (e.g. typing `# ` to transform paragraph to heading).
 */
@Composable
fun MarkdownBlockItem(
    block: MarkdownBlock,
    isFocused: Boolean,
    requestedCursorPosition: Int?,
    onIntent: (EditorIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }

    // Internal text state for smooth typing and immediate cursor feedback.
    // Keyed ONLY on block.id to avoid destroying state on every keystroke.
    var textFieldValue by remember(block.id) {
        val initialCursor = requestedCursorPosition?.coerceIn(0, block.rawContent.length)
            ?: block.rawContent.length
        mutableStateOf(
            TextFieldValue(
                text = block.rawContent,
                selection = TextRange(initialCursor)
            )
        )
    }

    // Keep external updates (Undo, Redo, initial load) synchronized without fighting user's active keystrokes
    LaunchedEffect(block.rawContent) {
        if (block.rawContent != textFieldValue.text) {
            val clampedCursor = textFieldValue.selection.start.coerceIn(0, block.rawContent.length)
            textFieldValue = textFieldValue.copy(
                text = block.rawContent,
                selection = TextRange(clampedCursor)
            )
        }
    }

    // Tracks the last applied programmatic cursor position so it's only applied once per request
    var lastHandledCursorRequest by remember(block.id) { mutableStateOf<Int?>(null) }

    // Handle programmatic cursor movement requests (e.g. from block split, merge, or TOC navigation)
    LaunchedEffect(requestedCursorPosition, isFocused) {
        if (isFocused && requestedCursorPosition != null && requestedCursorPosition != lastHandledCursorRequest) {
            lastHandledCursorRequest = requestedCursorPosition
            val target = requestedCursorPosition.coerceIn(0, textFieldValue.text.length)
            if (textFieldValue.selection.start != target || textFieldValue.selection.end != target) {
                textFieldValue = textFieldValue.copy(selection = TextRange(target))
            }
        }
    }

    // Focus handling when this block is designated as focused
    LaunchedEffect(isFocused) {
        if (isFocused) {
            focusRequester.requestFocus()
        }
    }

    val isDark = isSystemInDarkTheme()
    val visualTransformation: VisualTransformation = remember(block.type, isDark) {
        if (block.type is BlockType.CodeBlock) {
            CodeSyntaxVisualTransformation(
                language = (block.type as BlockType.CodeBlock).language,
                tokenizer = RegexCodeSyntaxTokenizer(),
                isDarkTheme = isDark
            )
        } else {
            VisualTransformation.None
        }
    }

    val textStyle = when (val type = block.type) {
        is BlockType.Heading -> {
            when (type.level) {
                1 -> MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = 28.sp, color = MaterialTheme.colorScheme.primary)
                2 -> MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
                3 -> MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                4 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                5 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                else -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
        is BlockType.CodeBlock -> {
            TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        BlockType.BlockQuote -> {
            MaterialTheme.typography.bodyLarge.copy(
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 24.sp
            )
        }
        is BlockType.ListItem -> {
            MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 24.sp
            )
        }
        else -> {
            MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 24.sp
            )
        }
    }

    val verticalPadding = when (block.type) {
        is BlockType.Heading -> 6.dp
        is BlockType.CodeBlock -> 6.dp
        is BlockType.ListItem -> 3.dp
        else -> 4.dp
    }

    BasicTextField(
        value = textFieldValue,
        onValueChange = { newValue ->
            handleValueChange(block, newValue, onIntent) { textFieldValue = it }
        },
        textStyle = textStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        visualTransformation = visualTransformation,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding, horizontal = 16.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused && !isFocused) {
                    onIntent(EditorIntent.RequestFocus(block.id, textFieldValue.selection.start))
                }
            }
            .onPreviewKeyEvent { event ->
                handleKeyEvent(event, textFieldValue, block.id, onIntent)
            },
        decorationBox = { innerTextField ->
            when (block.type) {
                is BlockType.CodeBlock -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(12.dp)
                    ) {
                        innerTextField()
                    }
                }
                BlockType.BlockQuote -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(24.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.secondary,
                                    shape = RoundedCornerShape(2.dp)
                                )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            innerTextField()
                        }
                    }
                }
                BlockType.ThematicBreak -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        innerTextField()
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            thickness = 1.dp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                else -> {
                    innerTextField()
                }
            }
        }
    )
}

/**
 * Handles text field changes, detecting enter key splits across all keyboards.
 * Code blocks are allowed to contain multiple lines without splitting.
 */
private fun handleValueChange(
    block: MarkdownBlock,
    newValue: TextFieldValue,
    onIntent: (EditorIntent) -> Unit,
    updateState: (TextFieldValue) -> Unit
) {
    val canContainNewlines = block.type is BlockType.CodeBlock
    if (!canContainNewlines && newValue.text.contains('\n')) {
        val newlineIndex = newValue.text.indexOf('\n')
        onIntent(EditorIntent.SplitBlock(block.id, newlineIndex))
    } else {
        updateState(newValue)
        onIntent(EditorIntent.UpdateBlock(block.id, newValue.text))
    }
}

/**
 * Handles physical/hardware key events for Enter (split) and Backspace (merge).
 */
private fun handleKeyEvent(
    event: androidx.compose.ui.input.key.KeyEvent,
    textFieldValue: TextFieldValue,
    blockId: BlockId,
    onIntent: (EditorIntent) -> Unit
): Boolean {
    if (event.type == KeyEventType.KeyDown) {
        if (event.key == Key.Enter) {
            onIntent(EditorIntent.SplitBlock(blockId, textFieldValue.selection.start))
            return true
        }
        if (event.key == Key.Backspace && textFieldValue.selection.start == 0 && textFieldValue.selection.end == 0) {
            onIntent(EditorIntent.MergeBlockWithPrevious(blockId))
            return true
        }
    }
    return false
}

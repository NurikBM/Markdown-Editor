package com.markdown.editor.presentation.ui.block

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

    // Internal text state for smooth typing and immediate cursor feedback
    var textFieldValue by remember(block.id, block.rawContent) {
        val initialCursor = requestedCursorPosition?.coerceIn(0, block.rawContent.length)
            ?: block.rawContent.length
        mutableStateOf(
            TextFieldValue(
                text = block.rawContent,
                selection = TextRange(initialCursor)
            )
        )
    }

    LaunchedEffect(isFocused, requestedCursorPosition) {
        if (isFocused) {
            focusRequester.requestFocus()
            if (requestedCursorPosition != null) {
                val clamped = requestedCursorPosition.coerceIn(0, textFieldValue.text.length)
                textFieldValue = textFieldValue.copy(selection = TextRange(clamped))
            }
        }
    }

    when (val type = block.type) {
        is BlockType.Heading -> {
            HeadingBlockView(
                level = type.level,
                textFieldValue = textFieldValue,
                focusRequester = focusRequester,
                onValueChange = { newValue ->
                    handleValueChange(block.id, newValue, onIntent) { textFieldValue = it }
                },
                onIntent = onIntent,
                blockId = block.id,
                modifier = modifier
            )
        }
        BlockType.Paragraph -> {
            ParagraphBlockView(
                textFieldValue = textFieldValue,
                focusRequester = focusRequester,
                onValueChange = { newValue ->
                    handleValueChange(block.id, newValue, onIntent) { textFieldValue = it }
                },
                onIntent = onIntent,
                blockId = block.id,
                modifier = modifier
            )
        }
        is BlockType.CodeBlock -> {
            CodeBlockView(
                language = type.language,
                textFieldValue = textFieldValue,
                focusRequester = focusRequester,
                onValueChange = { newValue ->
                    textFieldValue = newValue
                    onIntent(EditorIntent.UpdateBlock(block.id, newValue.text))
                },
                onIntent = onIntent,
                blockId = block.id,
                modifier = modifier
            )
        }
        is BlockType.ListItem -> {
            ListItemBlockView(
                ordered = type.ordered,
                index = type.index,
                textFieldValue = textFieldValue,
                focusRequester = focusRequester,
                onValueChange = { newValue ->
                    handleValueChange(block.id, newValue, onIntent) { textFieldValue = it }
                },
                onIntent = onIntent,
                blockId = block.id,
                modifier = modifier
            )
        }
        BlockType.BlockQuote -> {
            BlockQuoteView(
                textFieldValue = textFieldValue,
                focusRequester = focusRequester,
                onValueChange = { newValue ->
                    handleValueChange(block.id, newValue, onIntent) { textFieldValue = it }
                },
                onIntent = onIntent,
                blockId = block.id,
                modifier = modifier
            )
        }
        BlockType.ThematicBreak -> {
            ThematicBreakView(modifier = modifier)
        }
    }
}

/**
 * Handles text field changes, detecting enter key splits across all keyboards.
 */
private fun handleValueChange(
    blockId: BlockId,
    newValue: TextFieldValue,
    onIntent: (EditorIntent) -> Unit,
    updateState: (TextFieldValue) -> Unit
) {
    if (newValue.text.contains('\n')) {
        val newlineIndex = newValue.text.indexOf('\n')
        onIntent(EditorIntent.SplitBlock(blockId, newlineIndex))
    } else {
        updateState(newValue)
        onIntent(EditorIntent.UpdateBlock(blockId, newValue.text))
    }
}

@Composable
private fun ParagraphBlockView(
    textFieldValue: TextFieldValue,
    focusRequester: FocusRequester,
    onValueChange: (TextFieldValue) -> Unit,
    onIntent: (EditorIntent) -> Unit,
    blockId: BlockId,
    modifier: Modifier = Modifier
) {
    BasicTextField(
        value = textFieldValue,
        onValueChange = onValueChange,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 24.sp
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 16.dp)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                handleKeyEvent(event, textFieldValue, blockId, onIntent)
            }
    )
}

@Composable
private fun HeadingBlockView(
    level: Int,
    textFieldValue: TextFieldValue,
    focusRequester: FocusRequester,
    onValueChange: (TextFieldValue) -> Unit,
    onIntent: (EditorIntent) -> Unit,
    blockId: BlockId,
    modifier: Modifier = Modifier
) {
    val style = when (level) {
        1 -> MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = 28.sp)
        2 -> MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp)
        3 -> MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
        4 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        5 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp)
        else -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }

    BasicTextField(
        value = textFieldValue,
        onValueChange = onValueChange,
        textStyle = style.copy(color = MaterialTheme.colorScheme.primary),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 16.dp)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                handleKeyEvent(event, textFieldValue, blockId, onIntent)
            }
    )
}

@Composable
private fun CodeBlockView(
    language: String?,
    textFieldValue: TextFieldValue,
    focusRequester: FocusRequester,
    onValueChange: (TextFieldValue) -> Unit,
    onIntent: (EditorIntent) -> Unit,
    blockId: BlockId,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val visualTransformation = remember(language, isDark) {
        CodeSyntaxVisualTransformation(
            language = language,
            tokenizer = RegexCodeSyntaxTokenizer(),
            isDarkTheme = isDark
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 16.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        BasicTextField(
            value = textFieldValue,
            onValueChange = onValueChange,
            visualTransformation = visualTransformation,
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    handleKeyEvent(event, textFieldValue, blockId, onIntent)
                }
        )
    }
}

@Composable
private fun ListItemBlockView(
    ordered: Boolean,
    index: Int?,
    textFieldValue: TextFieldValue,
    focusRequester: FocusRequester,
    onValueChange: (TextFieldValue) -> Unit,
    onIntent: (EditorIntent) -> Unit,
    blockId: BlockId,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.Top
    ) {
        val prefix = if (ordered) "${index ?: 1}. " else "• "
        Text(
            text = prefix,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.padding(end = 4.dp)
        )
        BasicTextField(
            value = textFieldValue,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    handleKeyEvent(event, textFieldValue, blockId, onIntent)
                }
        )
    }
}

@Composable
private fun BlockQuoteView(
    textFieldValue: TextFieldValue,
    focusRequester: FocusRequester,
    onValueChange: (TextFieldValue) -> Unit,
    onIntent: (EditorIntent) -> Unit,
    blockId: BlockId,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .background(
                    color = MaterialTheme.colorScheme.secondary,
                    shape = RoundedCornerShape(2.dp)
                )
        )
        BasicTextField(
            value = textFieldValue,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    handleKeyEvent(event, textFieldValue, blockId, onIntent)
                }
        )
    }
}

@Composable
private fun ThematicBreakView(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
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


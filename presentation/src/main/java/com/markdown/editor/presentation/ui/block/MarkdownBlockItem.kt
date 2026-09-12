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
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.markdown.editor.domain.model.BlockId
import com.markdown.editor.domain.model.BlockType
import com.markdown.editor.domain.model.MarkdownBlock
import com.markdown.editor.domain.syntax.RegexCodeSyntaxTokenizer
import com.markdown.editor.presentation.editor.EditorIntent
import com.markdown.editor.presentation.syntax.CodeSyntaxVisualTransformation
import com.markdown.editor.presentation.ui.search.SearchHighlightVisualTransformation

/**
 * Encapsulates search query and active match range for block rendering.
 */
data class BlockSearchHighlight(
    val searchQuery: String = "",
    val isCaseSensitive: Boolean = false,
    val activeMatchRange: IntRange? = null
)

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
    modifier: Modifier = Modifier,
    searchHighlight: BlockSearchHighlight = BlockSearchHighlight()
) {
    val focusRequester = remember { FocusRequester() }

    // Internal text state for smooth typing and immediate cursor feedback.
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

    var lastSentText by remember(block.id) { mutableStateOf(block.rawContent) }

    LaunchedEffect(block.rawContent) {
        if (block.rawContent != textFieldValue.text && block.rawContent != lastSentText) {
            lastSentText = block.rawContent
            val clampedCursor = textFieldValue.selection.start.coerceIn(0, block.rawContent.length)
            textFieldValue = textFieldValue.copy(
                text = block.rawContent,
                selection = TextRange(clampedCursor)
            )
        }
    }

    var lastHandledCursorRequest by remember(block.id) { mutableStateOf<Int?>(null) }

    LaunchedEffect(requestedCursorPosition, isFocused) {
        if (isFocused && requestedCursorPosition != null && requestedCursorPosition != lastHandledCursorRequest) {
            lastHandledCursorRequest = requestedCursorPosition
            val target = requestedCursorPosition.coerceIn(0, textFieldValue.text.length)
            if (textFieldValue.selection.start != target || textFieldValue.selection.end != target) {
                textFieldValue = textFieldValue.copy(selection = TextRange(target))
            }
        }
    }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            focusRequester.requestFocus()
        }
    }

    val isDark = isSystemInDarkTheme()
    val visualTransformation = rememberBlockVisualTransformation(block.type, isDark, searchHighlight)
    val typography = MaterialTheme.typography
    val colorScheme = MaterialTheme.colorScheme
    val textStyle = remember(block.type, typography, colorScheme) {
        resolveBlockTextStyle(block.type, typography, colorScheme)
    }
    val verticalPadding = resolveBlockVerticalPadding(block.type)

    BasicTextField(
        value = textFieldValue,
        onValueChange = { newValue ->
            handleValueChange(
                block = block,
                newValue = newValue,
                onIntent = onIntent,
                updateState = { textFieldValue = it },
                onTextSent = { lastSentText = it }
            )
        },
        textStyle = textStyle,
        cursorBrush = SolidColor(colorScheme.primary),
        visualTransformation = visualTransformation,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = verticalPadding, horizontal = 16.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    onIntent(EditorIntent.RequestFocus(block.id, textFieldValue.selection.start, textFieldValue.selection.end))
                }
            }
            .onPreviewKeyEvent { event ->
                handleKeyEvent(event, textFieldValue, block, onIntent)
            },
        decorationBox = { innerTextField ->
            BlockDecorationBox(blockType = block.type, innerTextField = innerTextField)
        }
    )
}

@Composable
private fun rememberBlockVisualTransformation(
    blockType: BlockType,
    isDark: Boolean,
    searchHighlight: BlockSearchHighlight
): VisualTransformation {
    val baseVisualTransformation: VisualTransformation = remember(blockType, isDark) {
        if (blockType is BlockType.CodeBlock) {
            CodeSyntaxVisualTransformation(
                language = blockType.language,
                tokenizer = RegexCodeSyntaxTokenizer(),
                isDarkTheme = isDark
            )
        } else {
            VisualTransformation.None
        }
    }

    return remember(baseVisualTransformation, searchHighlight) {
        if (searchHighlight.searchQuery.isNotEmpty()) {
            SearchHighlightVisualTransformation(
                searchQuery = searchHighlight.searchQuery,
                isCaseSensitive = searchHighlight.isCaseSensitive,
                activeMatchRange = searchHighlight.activeMatchRange,
                baseTransformation = baseVisualTransformation
            )
        } else {
            baseVisualTransformation
        }
    }
}

private fun resolveBlockTextStyle(
    blockType: BlockType,
    typography: Typography,
    colorScheme: ColorScheme
): TextStyle {
    return when (blockType) {
        is BlockType.Heading -> resolveHeadingTextStyle(blockType.level, typography, colorScheme)
        is BlockType.CodeBlock -> TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            color = colorScheme.onSurfaceVariant
        )
        BlockType.BlockQuote -> typography.bodyLarge.copy(
            fontStyle = FontStyle.Italic,
            color = colorScheme.onSurfaceVariant,
            lineHeight = 24.sp
        )
        else -> typography.bodyLarge.copy(
            color = colorScheme.onSurface,
            lineHeight = 24.sp
        )
    }
}

private fun resolveHeadingTextStyle(
    level: Int,
    typography: Typography,
    colorScheme: ColorScheme
): TextStyle {
    return when (level) {
        1 -> typography.headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = 28.sp, color = colorScheme.primary)
        2 -> typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp, color = colorScheme.primary)
        3 -> typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = colorScheme.primary)
        4 -> typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = colorScheme.primary)
        5 -> typography.titleMedium.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp, color = colorScheme.primary)
        else -> typography.titleSmall.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp, color = colorScheme.primary)
    }
}

private fun resolveBlockVerticalPadding(blockType: BlockType): Dp {
    return when (blockType) {
        is BlockType.Heading, is BlockType.CodeBlock -> 6.dp
        is BlockType.ListItem -> 3.dp
        else -> 4.dp
    }
}

@Composable
private fun BlockDecorationBox(
    blockType: BlockType,
    innerTextField: @Composable () -> Unit
) {
    when (blockType) {
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

/**
 * Handles text field changes, detecting enter key splits across all keyboards.
 * Code blocks are allowed to contain multiple lines without splitting.
 */
private fun handleValueChange(
    block: MarkdownBlock,
    newValue: TextFieldValue,
    onIntent: (EditorIntent) -> Unit,
    updateState: (TextFieldValue) -> Unit,
    onTextSent: (String) -> Unit
) {
    val canContainNewlines = block.type is BlockType.CodeBlock
    if (!canContainNewlines && newValue.text.contains('\n')) {
        val newlineIndex = newValue.text.indexOf('\n')
        onIntent(EditorIntent.SplitBlock(block.id, newlineIndex))
    } else {
        updateState(newValue)
        onTextSent(newValue.text)
        onIntent(EditorIntent.UpdateBlock(block.id, newValue.text))
        onIntent(EditorIntent.RequestFocus(block.id, newValue.selection.start, newValue.selection.end))
    }
}

/**
 * Handles physical/hardware key events for Enter (split) and Backspace (merge).
 */
private fun handleKeyEvent(
    event: androidx.compose.ui.input.key.KeyEvent,
    textFieldValue: TextFieldValue,
    block: MarkdownBlock,
    onIntent: (EditorIntent) -> Unit
): Boolean {
    if (event.type == KeyEventType.KeyDown) {
        if (event.key == Key.Enter) {
            if (block.type !is BlockType.CodeBlock) {
                onIntent(EditorIntent.SplitBlock(block.id, textFieldValue.selection.start))
                return true
            }
            return false // Code block preserves Enter to insert newline
        }
        if (event.key == Key.Backspace && textFieldValue.selection.start == 0 && textFieldValue.selection.end == 0) {
            onIntent(EditorIntent.MergeBlockWithPrevious(block.id))
            return true
        }
    }
    return false
}

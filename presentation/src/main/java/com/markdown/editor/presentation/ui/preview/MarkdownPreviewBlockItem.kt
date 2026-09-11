package com.markdown.editor.presentation.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.markdown.editor.domain.model.BlockType
import com.markdown.editor.domain.model.InlineSpan
import com.markdown.editor.domain.model.MarkdownBlock
import com.markdown.editor.domain.syntax.RegexCodeSyntaxTokenizer
import com.markdown.editor.presentation.syntax.CodeSyntaxHighlighter

/**
 * Keyed preview renderer for a single [MarkdownBlock].
 * Transforms parsed domain block models into rich styled Jetpack Compose components.
 */
@Composable
fun MarkdownPreviewBlockItem(
    block: MarkdownBlock,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant

    val annotatedText = remember(block.plainText, block.rawContent, block.inlines, primaryColor, surfaceVariantColor) {
        buildAnnotatedMarkdown(block, primaryColor, surfaceVariantColor)
    }

    when (val type = block.type) {
        is BlockType.Heading -> {
            PreviewHeading(
                level = type.level,
                annotatedText = annotatedText,
                modifier = modifier
            )
        }
        BlockType.Paragraph -> {
            PreviewParagraph(
                annotatedText = annotatedText,
                modifier = modifier
            )
        }
        is BlockType.CodeBlock -> {
            PreviewCodeBlock(
                code = block.rawContent,
                language = type.language,
                modifier = modifier
            )
        }
        is BlockType.ListItem -> {
            PreviewListItem(
                ordered = type.ordered,
                index = type.index,
                annotatedText = annotatedText,
                modifier = modifier
            )
        }
        BlockType.BlockQuote -> {
            PreviewBlockQuote(
                annotatedText = annotatedText,
                modifier = modifier
            )
        }
        BlockType.ThematicBreak -> {
            PreviewThematicBreak(modifier = modifier)
        }
    }
}

@Composable
private fun PreviewHeading(
    level: Int,
    annotatedText: AnnotatedString,
    modifier: Modifier = Modifier
) {
    val (fontSize, fontWeight) = when (level) {
        1 -> 28.sp to FontWeight.Bold
        2 -> 24.sp to FontWeight.Bold
        3 -> 20.sp to FontWeight.SemiBold
        4 -> 18.sp to FontWeight.SemiBold
        5 -> 16.sp to FontWeight.Medium
        else -> 14.sp to FontWeight.Medium
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(
            text = annotatedText,
            style = TextStyle(
                fontSize = fontSize,
                fontWeight = fontWeight,
                color = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.fillMaxWidth()
        )
        if (level <= 2) {
            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 1.dp
            )
        }
    }
}

@Composable
private fun PreviewParagraph(
    annotatedText: AnnotatedString,
    modifier: Modifier = Modifier
) {
    Text(
        text = annotatedText,
        style = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 24.sp
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

@Suppress("DEPRECATION")
@Composable
private fun PreviewCodeBlock(
    code: String,
    language: String?,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    val isDark = isSystemInDarkTheme()
    val highlightedCode = remember(code, language, isDark) {
        CodeSyntaxHighlighter.highlight(
            code = code,
            language = language,
            tokenizer = RegexCodeSyntaxTokenizer(),
            isDarkTheme = isDark
        )
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = (language ?: "text").lowercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(code))
                    },
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(2.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = highlightedCode,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun PreviewListItem(
    ordered: Boolean,
    index: Int?,
    annotatedText: AnnotatedString,
    modifier: Modifier = Modifier
) {
    val prefix = if (ordered) "${index ?: 1}. " else "• "

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = prefix,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.width(24.dp)
        )
        Text(
            text = annotatedText,
            style = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PreviewBlockQuote(
    annotatedText: AnnotatedString,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)
            )
            .border(
                width = 4.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                shape = RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp)
            )
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 8.dp)
    ) {
        Text(
            text = annotatedText,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        )
    }
}

@Composable
private fun PreviewThematicBreak(modifier: Modifier = Modifier) {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    )
}

/**
 * Builds an [AnnotatedString] interpreting parsed [InlineSpan]s over [MarkdownBlock.plainText].
 */
private fun buildAnnotatedMarkdown(
    block: MarkdownBlock,
    primaryColor: Color,
    surfaceVariantColor: Color
): AnnotatedString {
    val text = if (block.plainText.isNotEmpty()) block.plainText else block.rawContent
    return buildAnnotatedString {
        append(text)
        for (span in block.inlines) {
            val start = span.start.coerceIn(0, text.length)
            val end = span.end.coerceIn(start, text.length)
            if (start >= end) continue

            when (span) {
                is InlineSpan.Bold -> addStyle(
                    SpanStyle(fontWeight = FontWeight.Bold),
                    start,
                    end
                )
                is InlineSpan.Italic -> addStyle(
                    SpanStyle(fontStyle = FontStyle.Italic),
                    start,
                    end
                )
                is InlineSpan.InlineCode -> addStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = surfaceVariantColor,
                        fontSize = 13.sp
                    ),
                    start,
                    end
                )
                is InlineSpan.Strikethrough -> addStyle(
                    SpanStyle(textDecoration = TextDecoration.LineThrough),
                    start,
                    end
                )
                is InlineSpan.Link -> {
                    addStyle(
                        SpanStyle(
                            color = primaryColor,
                            textDecoration = TextDecoration.Underline
                        ),
                        start,
                        end
                    )
                    addStringAnnotation(
                        tag = "URL",
                        annotation = span.destination,
                        start = start,
                        end = end
                    )
                }
            }
        }
    }
}

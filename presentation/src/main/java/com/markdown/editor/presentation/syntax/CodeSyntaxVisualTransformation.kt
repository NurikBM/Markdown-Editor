package com.markdown.editor.presentation.syntax

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.markdown.editor.domain.syntax.CodeSyntaxTokenizer

/**
 * [VisualTransformation] that applies real-time syntax highlighting to code blocks in [androidx.compose.foundation.text.BasicTextField].
 *
 * Maintains an exact 1:1 character offset mapping without mutating underlying document strings.
 */
class CodeSyntaxVisualTransformation(
    private val language: String?,
    private val tokenizer: CodeSyntaxTokenizer,
    private val isDarkTheme: Boolean
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = CodeSyntaxHighlighter.highlight(
            code = text.text,
            language = language,
            tokenizer = tokenizer,
            isDarkTheme = isDarkTheme
        )
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}


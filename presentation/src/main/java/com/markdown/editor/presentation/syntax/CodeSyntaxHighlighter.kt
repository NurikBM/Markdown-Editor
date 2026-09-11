package com.markdown.editor.presentation.syntax

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.markdown.editor.domain.syntax.CodeSyntaxTokenizer
import com.markdown.editor.domain.syntax.SyntaxTokenType

/**
 * Maps domain [SyntaxTokenType]s to Compose [AnnotatedString] with theme-aware styling.
 */
object CodeSyntaxHighlighter {

    /**
     * Applies syntax highlighting styles over [code] based on the specified [language].
     */
    fun highlight(
        code: String,
        language: String?,
        tokenizer: CodeSyntaxTokenizer,
        isDarkTheme: Boolean
    ): AnnotatedString {
        if (code.isEmpty()) return AnnotatedString(code)

        val tokens = tokenizer.tokenize(code, language)
        val builder = AnnotatedString.Builder(code)

        for (token in tokens) {
            val style = getStyleForTokenType(token.type, isDarkTheme)
            if (style != null && token.start < token.end && token.end <= code.length) {
                builder.addStyle(style, token.start, token.end)
            }
        }

        return builder.toAnnotatedString()
    }

    /**
     * Returns the [SpanStyle] for the given [type] based on dark/light theme.
     */
    fun getStyleForTokenType(type: SyntaxTokenType, isDarkTheme: Boolean): SpanStyle? {
        return when (type) {
            SyntaxTokenType.KEYWORD -> SpanStyle(
                color = if (isDarkTheme) Color(0xFFCF85F4) else Color(0xFF8E24AA),
                fontWeight = FontWeight.Bold
            )
            SyntaxTokenType.TYPE -> SpanStyle(
                color = if (isDarkTheme) Color(0xFF4DD0E1) else Color(0xFF00838F),
                fontWeight = FontWeight.Medium
            )
            SyntaxTokenType.STRING_LITERAL -> SpanStyle(
                color = if (isDarkTheme) Color(0xFFA5D6A7) else Color(0xFF2E7D32)
            )
            SyntaxTokenType.NUMBER_LITERAL -> SpanStyle(
                color = if (isDarkTheme) Color(0xFF90CAF9) else Color(0xFF1565C0)
            )
            SyntaxTokenType.COMMENT -> SpanStyle(
                color = if (isDarkTheme) Color(0xFF9E9E9E) else Color(0xFF757575),
                fontStyle = FontStyle.Italic
            )
            SyntaxTokenType.FUNCTION -> SpanStyle(
                color = if (isDarkTheme) Color(0xFFFFB74D) else Color(0xFFE65100),
                fontWeight = FontWeight.Medium
            )
            SyntaxTokenType.PUNCTUATION -> SpanStyle(
                color = if (isDarkTheme) Color(0xFFB0BEC5) else Color(0xFF546E7A)
            )
            SyntaxTokenType.PLAIN -> null
        }
    }
}


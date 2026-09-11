package com.markdown.editor.presentation.syntax

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import com.markdown.editor.domain.syntax.RegexCodeSyntaxTokenizer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CodeSyntaxHighlighterTest {

    private lateinit var tokenizer: RegexCodeSyntaxTokenizer

    @BeforeEach
    fun setUp() {
        tokenizer = RegexCodeSyntaxTokenizer()
    }

    @Test
    fun `highlight empty code returns empty AnnotatedString`() {
        val result = CodeSyntaxHighlighter.highlight("", "kotlin", tokenizer, isDarkTheme = false)
        assertEquals("", result.text)
        assertTrue(result.spanStyles.isEmpty())
    }

    @Test
    fun `highlight kotlin code applies span styles to keywords and strings`() {
        val code = "val name = \"Antigravity\""
        val highlighted = CodeSyntaxHighlighter.highlight(code, "kotlin", tokenizer, isDarkTheme = false)

        assertEquals(code, highlighted.text)
        assertTrue(highlighted.spanStyles.isNotEmpty())

        // Keyword "val" has a style
        val valStyle = highlighted.spanStyles.find { it.start == 0 && it.end == 3 }
        assertTrue(valStyle != null)

        // String "\"Antigravity\"" has a style
        val strStyle = highlighted.spanStyles.find { it.start == 11 && it.end == 24 }
        assertTrue(strStyle != null)
    }

    @Test
    fun `visual transformation produces transformed text with Identity offset mapping`() {
        val code = "fun test(): Int = 42"
        val transformation = CodeSyntaxVisualTransformation(
            language = "kotlin",
            tokenizer = tokenizer,
            isDarkTheme = true
        )

        val transformed = transformation.filter(AnnotatedString(code))
        assertEquals(code, transformed.text.text)
        assertEquals(OffsetMapping.Identity, transformed.offsetMapping)
        assertTrue(transformed.text.spanStyles.isNotEmpty())
    }

    @Test
    fun `dark and light themes produce distinct colors for keywords`() {
        val darkStyle = CodeSyntaxHighlighter.getStyleForTokenType(
            com.markdown.editor.domain.syntax.SyntaxTokenType.KEYWORD,
            isDarkTheme = true
        )
        val lightStyle = CodeSyntaxHighlighter.getStyleForTokenType(
            com.markdown.editor.domain.syntax.SyntaxTokenType.KEYWORD,
            isDarkTheme = false
        )

        assertTrue(darkStyle != null)
        assertTrue(lightStyle != null)
        assertTrue(darkStyle!!.color != lightStyle!!.color)
    }
}


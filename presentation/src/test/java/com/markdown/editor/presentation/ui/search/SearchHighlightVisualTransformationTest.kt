package com.markdown.editor.presentation.ui.search

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchHighlightVisualTransformationTest {

    @Test
    fun `empty query returns original text unchanged without spans`() {
        val transformation = SearchHighlightVisualTransformation(searchQuery = "")
        val input = AnnotatedString("Hello world")
        val result = transformation.filter(input)

        assertEquals("Hello world", result.text.text)
        assertTrue(result.text.spanStyles.isEmpty())
    }

    @Test
    fun `highlights all occurrences of query case-insensitively by default`() {
        val transformation = SearchHighlightVisualTransformation(
            searchQuery = "test",
            isCaseSensitive = false
        )
        val input = AnnotatedString("This is a Test and another test.")
        val result = transformation.filter(input)

        assertEquals("This is a Test and another test.", result.text.text)
        assertEquals(2, result.text.spanStyles.size)

        val firstSpan = result.text.spanStyles[0]
        assertEquals(10, firstSpan.start)
        assertEquals(14, firstSpan.end)

        val secondSpan = result.text.spanStyles[1]
        assertEquals(27, secondSpan.start)
        assertEquals(31, secondSpan.end)
    }

    @Test
    fun `case sensitive query only matches exact case`() {
        val transformation = SearchHighlightVisualTransformation(
            searchQuery = "Test",
            isCaseSensitive = true
        )
        val input = AnnotatedString("This is a Test and another test.")
        val result = transformation.filter(input)

        assertEquals(1, result.text.spanStyles.size)
        val span = result.text.spanStyles[0]
        assertEquals(10, span.start)
        assertEquals(14, span.end)
    }

    @Test
    fun `active match receives distinct active styling`() {
        val activeColor = Color(0xFFFF9800)
        val transformation = SearchHighlightVisualTransformation(
            searchQuery = "find",
            activeMatchRange = 8..12,
            activeHighlightColor = activeColor
        )
        val input = AnnotatedString("find me find you")
        val result = transformation.filter(input)

        assertEquals(2, result.text.spanStyles.size)
        val firstSpan = result.text.spanStyles[0]
        val secondSpan = result.text.spanStyles[1]

        // First match is 0..4 (regular highlight)
        assertEquals(0, firstSpan.start)
        assertEquals(4, firstSpan.end)

        // Second match is 8..12 (active match)
        assertEquals(8, secondSpan.start)
        assertEquals(12, secondSpan.end)
        assertEquals(activeColor, secondSpan.item.background)
    }
}


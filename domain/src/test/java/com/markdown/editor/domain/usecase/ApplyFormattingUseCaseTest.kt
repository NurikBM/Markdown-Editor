package com.markdown.editor.domain.usecase

import com.markdown.editor.domain.model.MarkdownFormatAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ApplyFormattingUseCaseTest {

    private val useCase = ApplyFormattingUseCase()

    @Test
    fun `bold wraps selected text`() {
        val result = useCase(
            content = "Hello World",
            selectionStart = 6,
            selectionEnd = 11,
            action = MarkdownFormatAction.BOLD
        )
        assertEquals("Hello **World**", result.newContent)
        assertEquals(8, result.cursorPosition)
        assertEquals(13, result.selectionEnd)
    }

    @Test
    fun `bold inserts empty markers when nothing selected`() {
        val result = useCase(
            content = "Hello ",
            selectionStart = 6,
            selectionEnd = 6,
            action = MarkdownFormatAction.BOLD
        )
        assertEquals("Hello ****", result.newContent)
        assertEquals(8, result.cursorPosition)
        assertEquals(8, result.selectionEnd)
    }

    @Test
    fun `bold toggles off when selection is already wrapped`() {
        val result = useCase(
            content = "Hello **World**",
            selectionStart = 8,
            selectionEnd = 13,
            action = MarkdownFormatAction.BOLD
        )
        assertEquals("Hello World", result.newContent)
        assertEquals(6, result.cursorPosition)
        assertEquals(11, result.selectionEnd)
    }

    @Test
    fun `italic wraps selected text`() {
        val result = useCase(
            content = "Markdown is awesome",
            selectionStart = 12,
            selectionEnd = 19,
            action = MarkdownFormatAction.ITALIC
        )
        assertEquals("Markdown is *awesome*", result.newContent)
        assertEquals(13, result.cursorPosition)
        assertEquals(20, result.selectionEnd)
    }

    @Test
    fun `strikethrough wraps selected text`() {
        val result = useCase(
            content = "old text",
            selectionStart = 0,
            selectionEnd = 3,
            action = MarkdownFormatAction.STRIKETHROUGH
        )
        assertEquals("~~old~~ text", result.newContent)
    }

    @Test
    fun `inline code wraps text`() {
        val result = useCase(
            content = "val x = 10",
            selectionStart = 4,
            selectionEnd = 5,
            action = MarkdownFormatAction.INLINE_CODE
        )
        assertEquals("val `x` = 10", result.newContent)
        assertEquals(5, result.cursorPosition)
        assertEquals(6, result.selectionEnd)
    }

    @Test
    fun `code block wraps text with triple backticks`() {
        val result = useCase(
            content = "println(1)",
            selectionStart = 0,
            selectionEnd = 10,
            action = MarkdownFormatAction.CODE_BLOCK
        )
        assertEquals("```\nprintln(1)\n```", result.newContent)
    }

    @Test
    fun `quote toggles prefix on and off`() {
        val onResult = useCase(
            content = "Quote me",
            selectionStart = 0,
            selectionEnd = 0,
            action = MarkdownFormatAction.QUOTE
        )
        assertEquals("> Quote me", onResult.newContent)

        val offResult = useCase(
            content = "> Quote me",
            selectionStart = 2,
            selectionEnd = 2,
            action = MarkdownFormatAction.QUOTE
        )
        assertEquals("Quote me", offResult.newContent)
    }

    @Test
    fun `bullet list toggles prefix`() {
        val onResult = useCase(
            content = "Item",
            selectionStart = 0,
            selectionEnd = 0,
            action = MarkdownFormatAction.BULLET_LIST
        )
        assertEquals("- Item", onResult.newContent)

        val offResult = useCase(
            content = "- Item",
            selectionStart = 2,
            selectionEnd = 2,
            action = MarkdownFormatAction.BULLET_LIST
        )
        assertEquals("Item", offResult.newContent)
    }

    @Test
    fun `numbered list toggles prefix`() {
        val onResult = useCase(
            content = "Item",
            selectionStart = 0,
            selectionEnd = 0,
            action = MarkdownFormatAction.NUMBERED_LIST
        )
        assertEquals("1. Item", onResult.newContent)

        val offResult = useCase(
            content = "1. Item",
            selectionStart = 3,
            selectionEnd = 3,
            action = MarkdownFormatAction.NUMBERED_LIST
        )
        assertEquals("Item", offResult.newContent)
    }

    @Test
    fun `heading cycles level from 1 to 3 and reverts to plain`() {
        val h1 = useCase(
            content = "Title",
            selectionStart = 0,
            selectionEnd = 0,
            action = MarkdownFormatAction.HEADING
        )
        assertEquals("# Title", h1.newContent)

        val h2 = useCase(
            content = "# Title",
            selectionStart = 0,
            selectionEnd = 0,
            action = MarkdownFormatAction.HEADING
        )
        assertEquals("## Title", h2.newContent)

        val h3 = useCase(
            content = "## Title",
            selectionStart = 0,
            selectionEnd = 0,
            action = MarkdownFormatAction.HEADING
        )
        assertEquals("### Title", h3.newContent)

        val plain = useCase(
            content = "### Title",
            selectionStart = 0,
            selectionEnd = 0,
            action = MarkdownFormatAction.HEADING
        )
        assertEquals("Title", plain.newContent)
    }

    @Test
    fun `link wraps selected text with url target`() {
        val result = useCase(
            content = "Visit Google here",
            selectionStart = 6,
            selectionEnd = 12,
            action = MarkdownFormatAction.LINK
        )
        assertEquals("Visit [Google](url) here", result.newContent)
        assertEquals(15, result.cursorPosition)
        assertEquals(18, result.selectionEnd)
    }

    @Test
    fun `horizontal rule inserts divider`() {
        val result = useCase(
            content = "",
            selectionStart = 0,
            selectionEnd = 0,
            action = MarkdownFormatAction.HORIZONTAL_RULE
        )
        assertEquals("---", result.newContent)
    }
}


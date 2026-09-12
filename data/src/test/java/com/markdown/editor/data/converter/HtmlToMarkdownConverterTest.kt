package com.markdown.editor.data.converter

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HtmlToMarkdownConverterTest {

    private val converter = HtmlToMarkdownConverter()

    @Test
    fun `convert translates HTML tags into Markdown elements`() = runTest {
        val html = """
            <!DOCTYPE html>
            <html>
            <head><title>My Article</title></head>
            <body>
                <h1>Main Heading</h1>
                <p>This is a paragraph with <strong>bold text</strong> and <em>italic text</em>.</p>
                <ul>
                    <li>First bullet</li>
                    <li>Second bullet</li>
                </ul>
                <table>
                    <tr><th>Topic</th><th>Status</th></tr>
                    <tr><td>Design</td><td>Done</td></tr>
                </table>
                <p><a href="https://example.com">Visit site</a></p>
            </body>
            </html>
        """.trimIndent()

        val result = converter.convert("Article.html", html.byteInputStream())

        assertFalse(result.isBestEffort)
        assertTrue(result.title == "My Article")
        assertTrue(result.markdownContent.contains("# Main Heading"))
        assertTrue(result.markdownContent.contains("**bold text** and *italic text*"))
        assertTrue(result.markdownContent.contains("- First bullet"))
        assertTrue(result.markdownContent.contains("- Second bullet"))
        assertTrue(result.markdownContent.contains("| Topic | Status |"))
        assertTrue(result.markdownContent.contains("| --- | --- |"))
        assertTrue(result.markdownContent.contains("| Design | Done |"))
        assertTrue(result.markdownContent.contains("[Visit site](https://example.com)"))
    }
}


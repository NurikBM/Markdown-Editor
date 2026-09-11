package com.markdown.editor.domain.usecase

import com.markdown.editor.domain.model.BlockId
import com.markdown.editor.domain.model.BlockType
import com.markdown.editor.domain.model.MarkdownBlock
import com.markdown.editor.domain.model.MarkdownDocument
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ExportHtmlUseCaseTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var useCase: ExportHtmlUseCase

    @BeforeEach
    fun setUp() {
        useCase = ExportHtmlUseCase(testDispatcher)
    }

    @Test
    fun `execute standalone exports complete HTML document with styling`() = runTest(testDispatcher) {
        val document = MarkdownDocument(
            id = "doc-1",
            title = "My Test Document",
            blocks = listOf(
                MarkdownBlock(
                    id = BlockId("1"),
                    type = BlockType.Heading(1),
                    rawContent = "# Hello World"
                ),
                MarkdownBlock(
                    id = BlockId("2"),
                    type = BlockType.Paragraph,
                    rawContent = "This is a paragraph with **bold** text."
                ),
                MarkdownBlock(
                    id = BlockId("3"),
                    type = BlockType.CodeBlock(language = "kotlin"),
                    rawContent = "```kotlin\nval x = 42\n```"
                )
            )
        )

        val result = useCase.execute(document, standalone = true)
        assertTrue(result.isSuccess)

        val html = result.getOrThrow()
        assertTrue(html.contains("<!DOCTYPE html>"))
        assertTrue(html.contains("<title>My Test Document</title>"))
        assertTrue(html.contains("<h1>Hello World</h1>"))
        assertTrue(html.contains("<strong>bold</strong>"))
        assertTrue(html.contains("<code class=\"language-kotlin\">val x = 42"))
        assertTrue(html.contains("@media print"))
    }

    @Test
    fun `execute non-standalone exports semantic body HTML only`() = runTest(testDispatcher) {
        val document = MarkdownDocument(
            id = "doc-2",
            title = "Snippet",
            blocks = listOf(
                MarkdownBlock(
                    id = BlockId("1"),
                    type = BlockType.Paragraph,
                    rawContent = "Simple text."
                )
            )
        )

        val result = useCase.execute(document, standalone = false)
        assertTrue(result.isSuccess)

        val html = result.getOrThrow()
        assertEquals("<p>Simple text.</p>\n", html)
        assertTrue(!html.contains("<!DOCTYPE html>"))
        assertTrue(!html.contains("<head>"))
    }

    @Test
    fun `execute escapes special characters in title`() = runTest(testDispatcher) {
        val document = MarkdownDocument(
            id = "doc-3",
            title = "Tom & Jerry <Special> \"Edition\"",
            blocks = emptyList()
        )

        val result = useCase.execute(document, standalone = true)
        assertTrue(result.isSuccess)

        val html = result.getOrThrow()
        assertTrue(html.contains("<title>Tom &amp; Jerry &lt;Special&gt; &quot;Edition&quot;</title>"))
    }

    @Test
    fun `execute with empty document produces valid HTML`() = runTest(testDispatcher) {
        val document = MarkdownDocument(
            id = "doc-empty",
            title = "Empty",
            blocks = emptyList()
        )

        val result = useCase.execute(document, standalone = true)
        assertTrue(result.isSuccess)

        val html = result.getOrThrow()
        assertTrue(html.contains("<!DOCTYPE html>"))
        assertTrue(html.contains("<title>Empty</title>"))
    }
}


package com.markdown.editor.domain.usecase

import com.markdown.editor.domain.model.BlockId
import com.markdown.editor.domain.model.BlockType
import com.markdown.editor.domain.model.MarkdownBlock
import com.markdown.editor.domain.model.MarkdownDocument
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GenerateTableOfContentsUseCaseTest {

    private lateinit var useCase: GenerateTableOfContentsUseCase

    @BeforeEach
    fun setUp() {
        useCase = GenerateTableOfContentsUseCase()
    }

    @Test
    fun `invoke with multi-level headings extracts correct levels, titles, and block indices`() {
        val blocks = listOf(
            MarkdownBlock(
                id = BlockId("h1"),
                rawContent = "# Chapter 1: Introduction",
                type = BlockType.Heading(1),
                plainText = "Chapter 1: Introduction"
            ),
            MarkdownBlock(
                id = BlockId("p1"),
                rawContent = "Some introduction paragraph text.",
                type = BlockType.Paragraph,
                plainText = "Some introduction paragraph text."
            ),
            MarkdownBlock(
                id = BlockId("h2"),
                rawContent = "## 1.1 Motivation",
                type = BlockType.Heading(2),
                plainText = "1.1 Motivation"
            ),
            MarkdownBlock(
                id = BlockId("code1"),
                rawContent = "```kotlin\nval x = 1\n```",
                type = BlockType.CodeBlock("kotlin"),
                plainText = "val x = 1"
            ),
            MarkdownBlock(
                id = BlockId("h3"),
                rawContent = "### 1.1.1 Prior Work",
                type = BlockType.Heading(3),
                plainText = "1.1.1 Prior Work"
            )
        )

        val toc = useCase(blocks)

        assertEquals(3, toc.size)

        assertEquals(BlockId("h1"), toc[0].blockId)
        assertEquals(1, toc[0].level)
        assertEquals("Chapter 1: Introduction", toc[0].title)
        assertEquals(0, toc[0].blockIndex)

        assertEquals(BlockId("h2"), toc[1].blockId)
        assertEquals(2, toc[1].level)
        assertEquals("1.1 Motivation", toc[1].title)
        assertEquals(2, toc[1].blockIndex)

        assertEquals(BlockId("h3"), toc[2].blockId)
        assertEquals(3, toc[2].level)
        assertEquals("1.1.1 Prior Work", toc[2].title)
        assertEquals(4, toc[2].blockIndex)
    }

    @Test
    fun `invoke with document containing no headings returns empty list`() {
        val blocks = listOf(
            MarkdownBlock(
                id = BlockId("p1"),
                rawContent = "Paragraph 1",
                type = BlockType.Paragraph,
                plainText = "Paragraph 1"
            ),
            MarkdownBlock(
                id = BlockId("quote1"),
                rawContent = "> A quote",
                type = BlockType.BlockQuote,
                plainText = "A quote"
            )
        )

        val toc = useCase(blocks)
        assertTrue(toc.isEmpty())
    }

    @Test
    fun `invoke handles blank plainText by falling back to stripped rawContent or fallback title`() {
        val blocks = listOf(
            MarkdownBlock(
                id = BlockId("h1"),
                rawContent = "# Fallback Title",
                type = BlockType.Heading(1),
                plainText = ""
            ),
            MarkdownBlock(
                id = BlockId("h2"),
                rawContent = "## ",
                type = BlockType.Heading(2),
                plainText = ""
            )
        )

        val toc = useCase(blocks)
        assertEquals(2, toc.size)
        assertEquals("Fallback Title", toc[0].title)
        assertEquals("Untitled Section", toc[1].title)
    }

    @Test
    fun `invoke with MarkdownDocument overload produces identical result`() {
        val doc = MarkdownDocument(
            id = "doc-test",
            title = "Test Doc",
            blocks = listOf(
                MarkdownBlock(
                    id = BlockId("h1"),
                    rawContent = "# Heading",
                    type = BlockType.Heading(1),
                    plainText = "Heading"
                )
            )
        )

        val toc = useCase(doc)
        assertEquals(1, toc.size)
        assertEquals("Heading", toc[0].title)
        assertEquals(0, toc[0].blockIndex)
    }
}


package com.markdown.editor.domain.usecase

import com.markdown.editor.domain.model.BlockId
import com.markdown.editor.domain.model.BlockType
import com.markdown.editor.domain.model.MarkdownBlock
import com.markdown.editor.domain.model.MarkdownDocument
import com.markdown.editor.domain.parser.CommonmarkBlockParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FindReplaceUseCaseTest {

    private val parser = CommonmarkBlockParser()
    private lateinit var findUseCase: FindInDocumentUseCase
    private lateinit var replaceUseCase: ReplaceInDocumentUseCase

    @BeforeEach
    fun setUp() {
        findUseCase = FindInDocumentUseCase()
        replaceUseCase = ReplaceInDocumentUseCase(parser)
    }

    @Test
    fun `findUseCase locates all occurrences across multiple blocks with accurate offsets`() {
        val doc = MarkdownDocument(
            id = "doc-1",
            title = "Search Test",
            blocks = listOf(
                MarkdownBlock(
                    id = BlockId("b0"),
                    rawContent = "# Android Markdown Editor",
                    type = BlockType.Heading(1),
                    plainText = "Android Markdown Editor"
                ),
                MarkdownBlock(
                    id = BlockId("b1"),
                    rawContent = "Markdown is great. Another markdown block.",
                    type = BlockType.Paragraph,
                    plainText = "Markdown is great. Another markdown block."
                )
            )
        )

        val matches = findUseCase(doc, "Markdown", isCaseSensitive = false)

        assertEquals(3, matches.size)

        // First match in heading
        assertEquals(BlockId("b0"), matches[0].blockId)
        assertEquals(0, matches[0].blockIndex)
        assertEquals(10, matches[0].startIndex)
        assertEquals(18, matches[0].endIndex)
        assertEquals("Markdown", matches[0].matchText)

        // Second match in paragraph (case-insensitive "Markdown")
        assertEquals(BlockId("b1"), matches[1].blockId)
        assertEquals(1, matches[1].blockIndex)
        assertEquals(0, matches[1].startIndex)
        assertEquals(8, matches[1].endIndex)

        // Third match in paragraph ("markdown")
        assertEquals(BlockId("b1"), matches[2].blockId)
        assertEquals(1, matches[2].blockIndex)
        assertEquals(27, matches[2].startIndex)
        assertEquals(35, matches[2].endIndex)
        assertEquals("markdown", matches[2].matchText)
    }

    @Test
    fun `findUseCase respects case sensitivity flag`() {
        val doc = MarkdownDocument(
            id = "doc-1",
            title = "Case Test",
            blocks = listOf(
                MarkdownBlock(
                    id = BlockId("b0"),
                    rawContent = "Kotlin is good, kotlin is everywhere, KOTLIN is powerful",
                    type = BlockType.Paragraph
                )
            )
        )

        val caseSensitiveMatches = findUseCase(doc, "Kotlin", isCaseSensitive = true)
        assertEquals(1, caseSensitiveMatches.size)
        assertEquals(0, caseSensitiveMatches[0].startIndex)

        val caseInsensitiveMatches = findUseCase(doc, "Kotlin", isCaseSensitive = false)
        assertEquals(3, caseInsensitiveMatches.size)
    }

    @Test
    fun `findUseCase returns empty list for blank query or when query not found`() {
        val doc = MarkdownDocument(
            id = "doc-1",
            title = "Empty Test",
            blocks = listOf(
                MarkdownBlock(id = BlockId("b0"), rawContent = "Hello World", type = BlockType.Paragraph)
            )
        )

        assertTrue(findUseCase(doc, "").isEmpty())
        assertTrue(findUseCase(doc, "NonExistent").isEmpty())
    }

    @Test
    fun `replaceUseCase replaceSingle correctly updates specific match and re-parses block`() {
        val doc = MarkdownDocument(
            id = "doc-1",
            title = "Replace Single Test",
            blocks = listOf(
                MarkdownBlock(
                    id = BlockId("b0"),
                    rawContent = "The red fox jumps over the red dog.",
                    type = BlockType.Paragraph
                )
            )
        )

        val matches = findUseCase(doc, "red", isCaseSensitive = false)
        assertEquals(2, matches.size)

        // Replace the second "red" with "blue"
        val updatedDoc = replaceUseCase.replaceSingle(doc, matches[1], "blue")

        assertEquals(
            "The red fox jumps over the blue dog.",
            updatedDoc.blocks[0].rawContent
        )
    }

    @Test
    fun `replaceUseCase replaceAll replaces all occurrences across multiple blocks`() {
        val doc = MarkdownDocument(
            id = "doc-1",
            title = "Replace All Test",
            blocks = listOf(
                MarkdownBlock(
                    id = BlockId("b0"),
                    rawContent = "Alpha beta Alpha",
                    type = BlockType.Paragraph
                ),
                MarkdownBlock(
                    id = BlockId("b1"),
                    rawContent = "No match here",
                    type = BlockType.Paragraph
                ),
                MarkdownBlock(
                    id = BlockId("b2"),
                    rawContent = "gamma alpha",
                    type = BlockType.Paragraph
                )
            )
        )

        val updatedDoc = replaceUseCase.replaceAll(
            document = doc,
            query = "Alpha",
            replacement = "Omega",
            isCaseSensitive = false
        )

        assertEquals("Omega beta Omega", updatedDoc.blocks[0].rawContent)
        assertEquals("No match here", updatedDoc.blocks[1].rawContent)
        assertEquals("gamma Omega", updatedDoc.blocks[2].rawContent)
    }
}


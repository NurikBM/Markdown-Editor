package com.markdown.editor.domain.parser

import com.markdown.editor.domain.model.BlockId
import com.markdown.editor.domain.model.BlockType
import com.markdown.editor.domain.model.InlineSpan
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CommonmarkBlockParserTest {

    private lateinit var parser: MarkdownBlockParser

    @BeforeEach
    fun setUp() {
        parser = CommonmarkBlockParser()
    }

    @Test
    fun `parses empty document into single empty paragraph block`() {
        val document = parser.parseDocument("")
        assertEquals(1, document.blocks.size)
        assertEquals(BlockType.Paragraph, document.blocks.first().type)
        assertEquals("", document.blocks.first().plainText)
    }

    @Test
    fun `parses headings correctly across levels`() {
        val markdown = """
            # Heading 1
            ## Heading 2
            ### Heading 3
        """.trimIndent()

        val doc = parser.parseDocument(markdown)
        assertEquals(3, doc.blocks.size)

        val h1 = doc.blocks[0]
        assertEquals(BlockType.Heading(1), h1.type)
        assertEquals("Heading 1", h1.plainText)
        assertEquals("# Heading 1", h1.rawContent)

        val h2 = doc.blocks[1]
        assertEquals(BlockType.Heading(2), h2.type)
        assertEquals("Heading 2", h2.plainText)
        assertEquals("## Heading 2", h2.rawContent)

        val h3 = doc.blocks[2]
        assertEquals(BlockType.Heading(3), h3.type)
        assertEquals("Heading 3", h3.plainText)
        assertEquals("### Heading 3", h3.rawContent)
    }

    @Test
    fun `parses fenced code block with language`() {
        val markdown = """
            ```kotlin
            val x = 42
            println(x)
            ```
        """.trimIndent()

        val doc = parser.parseDocument(markdown)
        assertEquals(1, doc.blocks.size)

        val block = doc.blocks[0]
        val type = assertInstanceOf(BlockType.CodeBlock::class.java, block.type)
        assertEquals("kotlin", type.language)
        assertEquals("val x = 42\nprintln(x)", block.plainText)
    }

    @Test
    fun `parses bullet and ordered list items`() {
        val markdown = """
            - Item A
            - Item B
            
            1. First
            2. Second
        """.trimIndent()

        val doc = parser.parseDocument(markdown)
        assertEquals(4, doc.blocks.size)

        val itemA = doc.blocks[0]
        val typeA = assertInstanceOf(BlockType.ListItem::class.java, itemA.type)
        assertFalse(typeA.ordered)
        assertEquals("Item A", itemA.plainText)

        val itemB = doc.blocks[1]
        val typeB = assertInstanceOf(BlockType.ListItem::class.java, itemB.type)
        assertFalse(typeB.ordered)
        assertEquals("Item B", itemB.plainText)

        val item1 = doc.blocks[2]
        val type1 = assertInstanceOf(BlockType.ListItem::class.java, item1.type)
        assertTrue(type1.ordered)
        assertEquals(1, type1.index)
        assertEquals("First", item1.plainText)

        val item2 = doc.blocks[3]
        val type2 = assertInstanceOf(BlockType.ListItem::class.java, item2.type)
        assertTrue(type2.ordered)
        assertEquals(2, type2.index)
        assertEquals("Second", item2.plainText)
    }

    @Test
    fun `parses blockquote and thematic break`() {
        val markdown = """
            > This is a quote
            
            ---
        """.trimIndent()

        val doc = parser.parseDocument(markdown)
        assertEquals(2, doc.blocks.size)

        val quote = doc.blocks[0]
        assertEquals(BlockType.BlockQuote, quote.type)
        assertEquals("This is a quote", quote.plainText)

        val hr = doc.blocks[1]
        assertEquals(BlockType.ThematicBreak, hr.type)
    }

    @Test
    fun `extracts rich inline formatting spans`() {
        val markdown = "This is **bold** and *italic* and `code` and [Google](https://google.com)"

        val doc = parser.parseDocument(markdown)
        assertEquals(1, doc.blocks.size)

        val block = doc.blocks[0]
        assertEquals(4, block.inlines.size)

        val bold = block.inlines.filterIsInstance<InlineSpan.Bold>().first()
        assertEquals("bold", block.plainText.substring(bold.start, bold.end))

        val italic = block.inlines.filterIsInstance<InlineSpan.Italic>().first()
        assertEquals("italic", block.plainText.substring(italic.start, italic.end))

        val code = block.inlines.filterIsInstance<InlineSpan.InlineCode>().first()
        assertEquals("code", block.plainText.substring(code.start, code.end))

        val link = block.inlines.filterIsInstance<InlineSpan.Link>().first()
        assertEquals("Google", block.plainText.substring(link.start, link.end))
        assertEquals("https://google.com", link.destination)
    }

    @Test
    fun `incremental updateBlock updates only target block preserving other block identities`() {
        val initialMarkdown = """
            # Document Title
            
            Paragraph that will not change.
            
            Paragraph to be updated.
            
            - Final bullet item
        """.trimIndent()

        val doc = parser.parseDocument(initialMarkdown)
        assertEquals(4, doc.blocks.size)

        val originalBlock0Id = doc.blocks[0].id
        val originalBlock1Id = doc.blocks[1].id
        val targetBlockId = doc.blocks[2].id
        val originalBlock3Id = doc.blocks[3].id

        val updatedDoc = parser.updateBlock(
            existingDocument = doc,
            targetBlockId = targetBlockId,
            newRawContent = "### Now an updated H3 heading!"
        )

        assertEquals(4, updatedDoc.blocks.size)
        // Verify untouched blocks retain exact identities and content
        assertEquals(originalBlock0Id, updatedDoc.blocks[0].id)
        assertEquals("Document Title", updatedDoc.blocks[0].plainText)

        assertEquals(originalBlock1Id, updatedDoc.blocks[1].id)
        assertEquals("Paragraph that will not change.", updatedDoc.blocks[1].plainText)

        assertEquals(originalBlock3Id, updatedDoc.blocks[3].id)
        assertEquals("Final bullet item", updatedDoc.blocks[3].plainText)

        // Verify target block updated its type and content while keeping its ID
        val updatedBlock = updatedDoc.blocks[2]
        assertEquals(targetBlockId, updatedBlock.id)
        assertEquals(BlockType.Heading(3), updatedBlock.type)
        assertEquals("Now an updated H3 heading!", updatedBlock.plainText)
        assertEquals("### Now an updated H3 heading!", updatedBlock.rawContent)
    }
}


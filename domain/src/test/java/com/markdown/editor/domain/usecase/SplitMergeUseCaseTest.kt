package com.markdown.editor.domain.usecase

import com.markdown.editor.domain.diff.DiffCalculator
import com.markdown.editor.domain.diff.DiffResult
import com.markdown.editor.domain.model.BlockId
import com.markdown.editor.domain.model.BlockType
import com.markdown.editor.domain.model.DocumentSnapshot
import com.markdown.editor.domain.model.MarkdownBlock
import com.markdown.editor.domain.model.MarkdownDocument
import com.markdown.editor.domain.parser.CommonmarkBlockParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SplitMergeUseCaseTest {

    private val parser = CommonmarkBlockParser()
    private lateinit var splitBlockUseCase: SplitBlockUseCase
    private lateinit var mergeBlockUseCase: MergeBlockUseCase
    private lateinit var undoBlockUseCase: UndoBlockUseCase
    private lateinit var redoBlockUseCase: RedoBlockUseCase

    // Simple mock diff calculator for testing domain use cases
    private val mockDiffCalculator = object : DiffCalculator {
        override fun computeDiff(oldText: String, newText: String): DiffResult {
            return DiffResult(forwardDiff = newText, reverseDiff = oldText, hasChanges = oldText != newText)
        }

        override fun applyPatch(sourceText: String, unifiedDiff: String): Result<String> {
            return Result.success(unifiedDiff)
        }
    }

    @BeforeEach
    fun setUp() {
        splitBlockUseCase = SplitBlockUseCase(parser)
        mergeBlockUseCase = MergeBlockUseCase(parser)
        undoBlockUseCase = UndoBlockUseCase(mockDiffCalculator, parser)
        redoBlockUseCase = RedoBlockUseCase(mockDiffCalculator, parser)
    }

    @Test
    fun `splitBlock splits text into two distinct blocks preserving original left id`() {
        val block1 = MarkdownBlock(
            id = BlockId("block-1"),
            rawContent = "Hello World",
            type = BlockType.Paragraph
        )
        val document = MarkdownDocument(id = "doc-1", blocks = listOf(block1))

        val result = splitBlockUseCase(document, BlockId("block-1"), cursorPosition = 5)

        assertTrue(result is SplitResult.Success)
        val success = result as SplitResult.Success
        val updatedBlocks = success.document.blocks

        assertEquals(2, updatedBlocks.size)
        assertEquals(BlockId("block-1"), updatedBlocks[0].id)
        assertEquals("Hello", updatedBlocks[0].rawContent)

        assertEquals(success.newBlockId, updatedBlocks[1].id)
        assertNotEquals(BlockId("block-1"), updatedBlocks[1].id)
        assertEquals(" World", updatedBlocks[1].rawContent)
    }

    @Test
    fun `splitBlock with nonexistent block returns unchanged`() {
        val document = MarkdownDocument(id = "doc-1", blocks = emptyList())
        val result = splitBlockUseCase(document, BlockId("missing"), cursorPosition = 0)
        assertTrue(result is SplitResult.Unchanged)
    }

    @Test
    fun `mergeBlock merges second block into first block and calculates cursor position`() {
        val block1 = MarkdownBlock(
            id = BlockId("b-1"),
            rawContent = "First Part",
            type = BlockType.Paragraph
        )
        val block2 = MarkdownBlock(
            id = BlockId("b-2"),
            rawContent = " Second Part",
            type = BlockType.Paragraph
        )
        val document = MarkdownDocument(id = "doc-1", blocks = listOf(block1, block2))

        val result = mergeBlockUseCase(document, BlockId("b-2"))

        assertTrue(result is MergeResult.Success)
        val success = result as MergeResult.Success

        assertEquals(1, success.document.blocks.size)
        assertEquals(BlockId("b-1"), success.targetBlockId)
        assertEquals("First Part Second Part", success.document.blocks[0].rawContent)
        assertEquals("First Part".length, success.cursorPosition)
    }

    @Test
    fun `mergeBlock on first block returns unchanged`() {
        val block1 = MarkdownBlock(id = BlockId("b-1"), rawContent = "First", type = BlockType.Paragraph)
        val document = MarkdownDocument(id = "doc-1", blocks = listOf(block1))

        val result = mergeBlockUseCase(document, BlockId("b-1"))
        assertTrue(result is MergeResult.Unchanged)
    }

    @Test
    fun `undoBlock applies reverse diff to target block`() {
        val block = MarkdownBlock(
            id = BlockId("b-1"),
            rawContent = "Modified text",
            type = BlockType.Paragraph
        )
        val document = MarkdownDocument(id = "doc-1", blocks = listOf(block))
        val snapshot = DocumentSnapshot(
            documentId = "doc-1",
            blockId = BlockId("b-1"),
            forwardDiff = "Modified text",
            reverseDiff = "Original text"
        )

        val result = undoBlockUseCase(document, snapshot)
        assertTrue(result.isSuccess)
        val updatedDoc = result.getOrThrow()
        assertEquals("Original text", updatedDoc.blocks[0].rawContent)
    }

    @Test
    fun `redoBlock applies forward diff to target block`() {
        val block = MarkdownBlock(
            id = BlockId("b-1"),
            rawContent = "Original text",
            type = BlockType.Paragraph
        )
        val document = MarkdownDocument(id = "doc-1", blocks = listOf(block))
        val snapshot = DocumentSnapshot(
            documentId = "doc-1",
            blockId = BlockId("b-1"),
            forwardDiff = "Redone text",
            reverseDiff = "Original text"
        )

        val result = redoBlockUseCase(document, snapshot)
        assertTrue(result.isSuccess)
        val updatedDoc = result.getOrThrow()
        assertEquals("Redone text", updatedDoc.blocks[0].rawContent)
    }
}


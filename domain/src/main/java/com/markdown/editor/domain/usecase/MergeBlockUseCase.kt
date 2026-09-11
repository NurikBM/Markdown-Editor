package com.markdown.editor.domain.usecase

import com.markdown.editor.domain.model.BlockId
import com.markdown.editor.domain.model.MarkdownDocument
import com.markdown.editor.domain.parser.MarkdownBlockParser

sealed interface MergeResult {
    data class Success(
        val document: MarkdownDocument,
        val targetBlockId: BlockId,
        val cursorPosition: Int
    ) : MergeResult

    data class Unchanged(val document: MarkdownDocument) : MergeResult
}

/**
 * Merges a markdown block with its preceding block.
 * Triggered on Backspace keypress at index 0 of a block.
 */
class MergeBlockUseCase(
    private val parser: MarkdownBlockParser
) {

    operator fun invoke(
        document: MarkdownDocument,
        blockId: BlockId
    ): MergeResult {
        val blockIndex = document.blocks.indexOfFirst { it.id == blockId }
        if (blockIndex <= 0) return MergeResult.Unchanged(document)

        val prevBlock = document.blocks[blockIndex - 1]
        val targetBlock = document.blocks[blockIndex]

        val targetCursorPosition = prevBlock.rawContent.length
        val mergedRawContent = prevBlock.rawContent + targetBlock.rawContent

        val updatedPrevBlock = parser.parseBlock(mergedRawContent, prevBlock.id)

        val newBlocks = document.blocks.toMutableList().apply {
            set(blockIndex - 1, updatedPrevBlock)
            removeAt(blockIndex)
        }

        return MergeResult.Success(
            document = document.copy(blocks = newBlocks),
            targetBlockId = prevBlock.id,
            cursorPosition = targetCursorPosition
        )
    }
}


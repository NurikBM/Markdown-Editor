package com.markdown.editor.domain.usecase

import com.markdown.editor.domain.model.BlockId
import com.markdown.editor.domain.model.MarkdownDocument
import com.markdown.editor.domain.parser.MarkdownBlockParser

sealed interface SplitResult {
    data class Success(
        val document: MarkdownDocument,
        val newBlockId: BlockId,
        val splitIndex: Int
    ) : SplitResult

    data class Unchanged(val document: MarkdownDocument) : SplitResult
}

/**
 * Splits a markdown block into two discrete blocks at [cursorPosition].
 * Triggered on Enter keypress in editor UI.
 */
class SplitBlockUseCase(
    private val parser: MarkdownBlockParser
) {

    operator fun invoke(
        document: MarkdownDocument,
        blockId: BlockId,
        cursorPosition: Int
    ): SplitResult {
        val blockIndex = document.blocks.indexOfFirst { it.id == blockId }
        if (blockIndex == -1) return SplitResult.Unchanged(document)

        val targetBlock = document.blocks[blockIndex]
        val clampedPosition = cursorPosition.coerceIn(0, targetBlock.rawContent.length)

        val leftContent = targetBlock.rawContent.take(clampedPosition)
        val rightContent = targetBlock.rawContent.drop(clampedPosition)

        val leftBlock = parser.parseBlock(leftContent, targetBlock.id)
        val newBlockId = BlockId()
        val rightBlock = parser.parseBlock(rightContent, newBlockId)

        val newBlocks = document.blocks.toMutableList().apply {
            set(blockIndex, leftBlock)
            add(blockIndex + 1, rightBlock)
        }

        return SplitResult.Success(
            document = document.copy(blocks = newBlocks),
            newBlockId = newBlockId,
            splitIndex = blockIndex + 1
        )
    }
}


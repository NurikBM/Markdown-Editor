package com.markdown.editor.domain.usecase

import com.markdown.editor.domain.model.BlockId
import com.markdown.editor.domain.model.BlockType
import com.markdown.editor.domain.model.MarkdownDocument
import com.markdown.editor.domain.parser.MarkdownBlockParser

sealed interface SplitResult {
    data class Success(
        val document: MarkdownDocument,
        val newBlockId: BlockId,
        val splitIndex: Int,
        val cursorPosition: Int = 0
    ) : SplitResult

    data class Unchanged(val document: MarkdownDocument) : SplitResult
}

/**
 * Splits a markdown block into two discrete blocks at [cursorPosition] with smart prefix continuation
 * for quotes (`> `) and lists (`- `, `1. `), as well as an exit-on-empty rule that resets an empty
 * prefix block to a plain paragraph.
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
        val content = targetBlock.rawContent

        // 1. Exit-on-empty rule: If user hits Enter on an empty quote, list, or heading prefix,
        // revert the block to a plain empty paragraph rather than adding another blank item.
        val isEmptyQuote = content.matches(Regex("^\\s*>+\\s*$"))
        val isEmptyBulletList = content.matches(Regex("^\\s*[*+-]\\s*$"))
        val isEmptyOrderedList = content.matches(Regex("^\\s*\\d+\\.\\s*$"))
        val isEmptyHeading = content.matches(Regex("^\\s*#{1,6}\\s*$"))

        if (isEmptyQuote || isEmptyBulletList || isEmptyOrderedList || isEmptyHeading) {
            val resetBlock = parser.parseBlock("", targetBlock.id)
            val newBlocks = document.blocks.toMutableList().apply {
                set(blockIndex, resetBlock)
            }
            return SplitResult.Success(
                document = document.copy(blocks = newBlocks),
                newBlockId = targetBlock.id,
                splitIndex = blockIndex,
                cursorPosition = 0
            )
        }

        // 2. Normal split with smart prefix continuation
        val clampedPosition = cursorPosition.coerceIn(0, content.length)
        val leftContent = content.take(clampedPosition)
        val rightContent = content.drop(clampedPosition)

        val isQuote = targetBlock.type is BlockType.BlockQuote || leftContent.trimStart().startsWith(">")
        val isOrderedList = leftContent.trimStart().matches(Regex("^(\\d+)\\.\\s*.*"))
        val isBulletList = leftContent.trimStart().matches(Regex("^([*+-])\\s*.*"))

        val (newRightContent, targetCursorPosition) = when {
            isQuote -> {
                val rightText = if (rightContent.trimStart().startsWith(">")) rightContent else "> $rightContent"
                rightText to 2
            }
            isOrderedList -> {
                val match = Regex("^(\\d+)\\.\\s*").find(leftContent.trimStart())
                val currentNum = match?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val nextPrefix = "${currentNum + 1}. "
                val remainder = if (match != null) rightContent.removePrefix(match.value) else rightContent
                (nextPrefix + remainder) to nextPrefix.length
            }
            isBulletList -> {
                val match = Regex("^([*+-])\\s*").find(leftContent.trimStart())
                val marker = match?.groupValues?.get(1) ?: "-"
                val nextPrefix = "$marker "
                val remainder = if (match != null) rightContent.removePrefix(match.value) else rightContent
                (nextPrefix + remainder) to nextPrefix.length
            }
            else -> {
                rightContent to 0
            }
        }

        val leftBlock = parser.parseBlock(leftContent, targetBlock.id)
        val newBlockId = BlockId()
        val rightBlock = parser.parseBlock(newRightContent, newBlockId)

        val newBlocks = document.blocks.toMutableList().apply {
            set(blockIndex, leftBlock)
            add(blockIndex + 1, rightBlock)
        }

        return SplitResult.Success(
            document = document.copy(blocks = newBlocks),
            newBlockId = newBlockId,
            splitIndex = blockIndex + 1,
            cursorPosition = targetCursorPosition
        )
    }
}

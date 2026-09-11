package com.markdown.editor.domain.usecase

import com.markdown.editor.domain.diff.DiffCalculator
import com.markdown.editor.domain.error.DomainError
import com.markdown.editor.domain.error.asException
import com.markdown.editor.domain.model.DocumentSnapshot
import com.markdown.editor.domain.model.MarkdownDocument
import com.markdown.editor.domain.parser.MarkdownBlockParser

/**
 * Applies a reverse diff snapshot to undo a previous block modification.
 */
class UndoBlockUseCase(
    private val diffCalculator: DiffCalculator,
    private val parser: MarkdownBlockParser
) {

    operator fun invoke(
        document: MarkdownDocument,
        snapshot: DocumentSnapshot
    ): Result<MarkdownDocument> {
        val blockIndex = document.blocks.indexOfFirst { it.id == snapshot.blockId }
        if (blockIndex == -1) {
            return Result.failure(
                DomainError.PatchConflict.BlockMismatch(
                    snapshot.blockId.value,
                    "Target block not found in document"
                ).asException()
            )
        }

        val targetBlock = document.blocks[blockIndex]
        val patchResult = diffCalculator.applyPatch(targetBlock.rawContent, snapshot.reverseDiff)

        return patchResult.map { patchedContent ->
            val updatedBlock = parser.parseBlock(patchedContent, targetBlock.id)
            val updatedBlocks = document.blocks.toMutableList().apply {
                set(blockIndex, updatedBlock)
            }
            document.copy(blocks = updatedBlocks)
        }
    }
}


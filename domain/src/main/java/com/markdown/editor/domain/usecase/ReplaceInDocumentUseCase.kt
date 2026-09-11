package com.markdown.editor.domain.usecase

import com.markdown.editor.domain.model.FindMatch
import com.markdown.editor.domain.model.MarkdownDocument
import com.markdown.editor.domain.parser.MarkdownBlockParser

/**
 * Pure Kotlin domain use case executing single or bulk replace operations across document blocks.
 * Incremental re-parsing ensures modified blocks have up-to-date AST structures and block types.
 */
class ReplaceInDocumentUseCase(
    private val parser: MarkdownBlockParser
) {

    /**
     * Replaces a specific [FindMatch] occurrence with [replacement] text.
     * Returns an updated [MarkdownDocument] with the target block incrementally re-parsed.
     */
    fun replaceSingle(
        document: MarkdownDocument,
        match: FindMatch,
        replacement: String
    ): MarkdownDocument {
        if (match.blockIndex !in document.blocks.indices) return document
        val targetBlock = document.blocks[match.blockIndex]
        if (targetBlock.id != match.blockId) return document

        val content = targetBlock.rawContent
        if (match.startIndex > content.length || match.endIndex > content.length) return document

        val newRawContent = buildString {
            append(content.substring(0, match.startIndex))
            append(replacement)
            append(content.substring(match.endIndex))
        }

        val updatedBlock = parser.parseBlock(newRawContent, targetBlock.id)
        val updatedBlocks = document.blocks.toMutableList().apply {
            set(match.blockIndex, updatedBlock)
        }

        return document.copy(blocks = updatedBlocks)
    }

    /**
     * Replaces all occurrences of [query] across all blocks with [replacement] text.
     * Incrementally re-parses only the blocks that contained matches.
     */
    fun replaceAll(
        document: MarkdownDocument,
        query: String,
        replacement: String,
        isCaseSensitive: Boolean = false
    ): MarkdownDocument {
        if (query.isEmpty()) return document

        var hasAnyChanges = false
        val updatedBlocks = document.blocks.map { block ->
            val oldContent = block.rawContent
            val newContent = oldContent.replace(query, replacement, ignoreCase = !isCaseSensitive)
            if (newContent != oldContent) {
                hasAnyChanges = true
                parser.parseBlock(newContent, block.id)
            } else {
                block
            }
        }

        return if (hasAnyChanges) document.copy(blocks = updatedBlocks) else document
    }
}


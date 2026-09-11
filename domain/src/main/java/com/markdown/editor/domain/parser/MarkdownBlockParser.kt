package com.markdown.editor.domain.parser

import com.markdown.editor.domain.model.BlockId
import com.markdown.editor.domain.model.MarkdownBlock
import com.markdown.editor.domain.model.MarkdownDocument
import java.util.UUID

/**
 * Interface defining block-level parsing and incremental mutation operations.
 */
interface MarkdownBlockParser {

    /**
     * Parses a full Markdown string into a [MarkdownDocument] composed of distinct [MarkdownBlock]s.
     */
    fun parseDocument(
        rawMarkdown: String,
        documentId: String = UUID.randomUUID().toString(),
        title: String = "Untitled"
    ): MarkdownDocument

    /**
     * Parses a single block string into a [MarkdownBlock], preserving or generating [existingId].
     */
    fun parseBlock(
        rawBlockContent: String,
        existingId: BlockId = BlockId()
    ): MarkdownBlock

    /**
     * Incrementally updates a specific block in [existingDocument] with [newRawContent].
     * Only the target block is re-parsed; all other blocks preserve their identities.
     */
    fun updateBlock(
        existingDocument: MarkdownDocument,
        targetBlockId: BlockId,
        newRawContent: String
    ): MarkdownDocument
}


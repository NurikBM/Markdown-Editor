package com.markdown.editor.domain.model

/**
 * Immutable domain entity representing a single item in the document's Table of Contents.
 *
 * @property blockId Identifier of the corresponding heading block.
 * @property level Heading depth level from 1 to 6 (e.g. H1 = 1, H2 = 2).
 * @property title Cleaned plain-text representation of the heading title.
 * @property blockIndex The 0-based position of this heading block in the document's block sequence.
 */
data class TableOfContentsItem(
    val blockId: BlockId,
    val level: Int,
    val title: String,
    val blockIndex: Int
) {
    init {
        require(level in 1..6) { "Heading level must be between 1 and 6, but was $level" }
        require(blockIndex >= 0) { "Block index must be non-negative, but was $blockIndex" }
    }
}


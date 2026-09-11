package com.markdown.editor.domain.model

/**
 * Immutable domain entity representing a search match occurrence within a Markdown document block.
 *
 * @property blockId Unique identifier of the matching block.
 * @property blockIndex The 0-based index of the block within the document's block list.
 * @property startIndex Character start index of the match within [MarkdownBlock.rawContent] (inclusive).
 * @property endIndex Character end index of the match within [MarkdownBlock.rawContent] (exclusive).
 * @property matchText The exact matched substring.
 */
data class FindMatch(
    val blockId: BlockId,
    val blockIndex: Int,
    val startIndex: Int,
    val endIndex: Int,
    val matchText: String
) {
    init {
        require(blockIndex >= 0) { "Block index must be non-negative, but was $blockIndex" }
        require(startIndex >= 0) { "Start index must be non-negative, but was $startIndex" }
        require(endIndex >= startIndex) { "End index ($endIndex) must be greater than or equal to start index ($startIndex)" }
    }

    /**
     * Length of the matched text.
     */
    val length: Int get() = endIndex - startIndex
}


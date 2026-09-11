package com.markdown.editor.domain.model

/**
 * Immutable domain entity representing a single decomposed Markdown block.
 *
 * @property id Unique persistent identifier. Stable across content edits for the same block.
 * @property rawContent The full raw markdown string for this block (e.g., "# Heading" or "- List item").
 * @property type The parsed structure type of this block.
 * @property inlines List of rich text inline spans within [plainText].
 * @property plainText Rendered plain text representation stripped of block-level markdown markers.
 */
data class MarkdownBlock(
    val id: BlockId = BlockId(),
    val rawContent: String,
    val type: BlockType,
    val inlines: List<InlineSpan> = emptyList(),
    val plainText: String = rawContent
)


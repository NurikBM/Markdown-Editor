package com.markdown.editor.domain.model

import java.util.UUID

/**
 * Domain aggregate root representing an entire Markdown document composed of discrete blocks.
 *
 * @property id Unique document identifier.
 * @property title The title of the document.
 * @property blocks Ordered collection of [MarkdownBlock] instances.
 */
data class MarkdownDocument(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Untitled",
    val blocks: List<MarkdownBlock> = emptyList()
) {
    /**
     * Serializes the document blocks into a continuous Markdown document string.
     */
    val rawContent: String
        get() = blocks.joinToString("\n\n") { it.rawContent }
}


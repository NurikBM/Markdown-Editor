package com.markdown.editor.domain.model

/**
 * Sealed hierarchy defining rich inline style spans within a Markdown block.
 * Indices [start] and [end] are character offsets (0-indexed, exclusive end) within the block's text.
 */
sealed interface InlineSpan {
    val start: Int
    val end: Int

    data class Bold(override val start: Int, override val end: Int) : InlineSpan
    data class Italic(override val start: Int, override val end: Int) : InlineSpan
    data class InlineCode(override val start: Int, override val end: Int) : InlineSpan
    data class Link(
        override val start: Int,
        override val end: Int,
        val destination: String,
        val title: String? = null
    ) : InlineSpan
    data class Strikethrough(override val start: Int, override val end: Int) : InlineSpan
}


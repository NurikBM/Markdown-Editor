package com.markdown.editor.domain.model

/**
 * Sealed hierarchy defining supported Markdown block types.
 */
sealed interface BlockType {
    data class Heading(val level: Int) : BlockType {
        init {
            require(level in 1..6) { "Heading level must be between 1 and 6, but was $level" }
        }
    }

    data object Paragraph : BlockType

    data class CodeBlock(val language: String? = null) : BlockType

    data class ListItem(val ordered: Boolean, val index: Int? = null) : BlockType

    data object BlockQuote : BlockType

    data object ThematicBreak : BlockType
}


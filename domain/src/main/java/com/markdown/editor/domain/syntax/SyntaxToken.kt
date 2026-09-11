package com.markdown.editor.domain.syntax

/**
 * Represents a token in a source code block with its semantic type and character range [start, end).
 */
data class SyntaxToken(
    val type: SyntaxTokenType,
    val start: Int,
    val end: Int
) {
    init {
        require(start >= 0) { "Token start index must be non-negative, but was $start" }
        require(end >= start) { "Token end index ($end) must be greater than or equal to start index ($start)" }
    }
}


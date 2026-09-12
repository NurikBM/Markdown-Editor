package com.markdown.editor.domain.syntax

/**
 * Contract for tokenizing source code into syntax tokens for high-performance syntax highlighting.
 */
fun interface CodeSyntaxTokenizer {
    /**
     * Tokenizes [code] for the given [language].
     *
     * @param code The raw source code to tokenize.
     * @param language The programming or markup language identifier (e.g., "kotlin", "java", "python", "json").
     * @return Ordered, non-overlapping list of [SyntaxToken]s.
     */
    fun tokenize(code: String, language: String?): List<SyntaxToken>
}

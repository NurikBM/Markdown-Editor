package com.markdown.editor.domain.usecase

import com.markdown.editor.domain.model.FindMatch
import com.markdown.editor.domain.model.MarkdownBlock
import com.markdown.editor.domain.model.MarkdownDocument

/**
 * Pure Kotlin domain use case searching for text occurrences across document blocks.
 * Operates across block boundaries and supports case-sensitive and case-insensitive matching.
 */
class FindInDocumentUseCase {

    /**
     * Finds all occurrences of [query] within the given [blocks].
     */
    operator fun invoke(
        blocks: List<MarkdownBlock>,
        query: String,
        isCaseSensitive: Boolean = false
    ): List<FindMatch> {
        if (query.isEmpty()) return emptyList()

        val matches = mutableListOf<FindMatch>()

        for (blockIndex in blocks.indices) {
            val block = blocks[blockIndex]
            val content = block.rawContent
            if (content.isEmpty()) continue

            var searchIndex = 0
            while (searchIndex < content.length) {
                val foundIndex = content.indexOf(
                    string = query,
                    startIndex = searchIndex,
                    ignoreCase = !isCaseSensitive
                )
                if (foundIndex == -1) break

                val endIndex = foundIndex + query.length
                val matchedText = content.substring(foundIndex, endIndex)

                matches.add(
                    FindMatch(
                        blockId = block.id,
                        blockIndex = blockIndex,
                        startIndex = foundIndex,
                        endIndex = endIndex,
                        matchText = matchedText
                    )
                )
                searchIndex = endIndex
            }
        }

        return matches
    }

    /**
     * Convenience overload for searching within a [MarkdownDocument].
     */
    operator fun invoke(
        document: MarkdownDocument,
        query: String,
        isCaseSensitive: Boolean = false
    ): List<FindMatch> = invoke(document.blocks, query, isCaseSensitive)
}


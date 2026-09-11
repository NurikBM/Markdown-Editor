package com.markdown.editor.domain.usecase

import com.markdown.editor.domain.model.BlockType
import com.markdown.editor.domain.model.MarkdownBlock
import com.markdown.editor.domain.model.MarkdownDocument
import com.markdown.editor.domain.model.TableOfContentsItem

/**
 * Pure Kotlin domain use case that generates a structured Table of Contents (ToC)
 * directly from parsed AST Heading blocks.
 */
class GenerateTableOfContentsUseCase {

    /**
     * Extracts all heading blocks from [blocks] and constructs a list of [TableOfContentsItem]
     * preserving depth level, title, and document block indices for fast navigation.
     */
    operator fun invoke(blocks: List<MarkdownBlock>): List<TableOfContentsItem> {
        return blocks.mapIndexedNotNull { index, block ->
            val headingType = block.type as? BlockType.Heading ?: return@mapIndexedNotNull null
            val cleanTitle = block.plainText.trim().ifEmpty {
                block.rawContent.trimStart('#', ' ').trim().ifEmpty { "Untitled Section" }
            }
            TableOfContentsItem(
                blockId = block.id,
                level = headingType.level,
                title = cleanTitle,
                blockIndex = index
            )
        }
    }

    /**
     * Convenience overload for extracting Table of Contents directly from a [MarkdownDocument].
     */
    operator fun invoke(document: MarkdownDocument): List<TableOfContentsItem> = invoke(document.blocks)
}


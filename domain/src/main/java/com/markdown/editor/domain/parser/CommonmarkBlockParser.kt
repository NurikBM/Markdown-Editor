package com.markdown.editor.domain.parser

import com.markdown.editor.domain.model.BlockId
import com.markdown.editor.domain.model.BlockType
import com.markdown.editor.domain.model.InlineSpan
import com.markdown.editor.domain.model.MarkdownBlock
import com.markdown.editor.domain.model.MarkdownDocument
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Link
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.Parser
import java.util.UUID

/**
 * Production implementation of [MarkdownBlockParser] powered by commonmark-java.
 * Decomposes Markdown documents into discrete keyed blocks and supports incremental single-block updates.
 */
class CommonmarkBlockParser : MarkdownBlockParser {

    private val parser: Parser = Parser.builder()
        .includeSourceSpans(IncludeSourceSpans.BLOCKS)
        .build()

    override fun parseDocument(
        rawMarkdown: String,
        documentId: String,
        title: String
    ): MarkdownDocument {
        if (rawMarkdown.isBlank()) {
            val emptyBlock = MarkdownBlock(
                id = BlockId(),
                rawContent = rawMarkdown,
                type = BlockType.Paragraph,
                inlines = emptyList(),
                plainText = ""
            )
            return MarkdownDocument(
                id = documentId,
                title = title,
                blocks = listOf(emptyBlock)
            )
        }

        val documentNode = parser.parse(rawMarkdown)
        val blocks = mutableListOf<MarkdownBlock>()

        var currentNode: Node? = documentNode.firstChild
        while (currentNode != null) {
            when (currentNode) {
                is BulletList -> processBulletList(currentNode, rawMarkdown, blocks)
                is OrderedList -> processOrderedList(currentNode, rawMarkdown, blocks)
                else -> {
                    val blockType = determineBlockType(currentNode)
                    blocks.add(createBlockFromNode(currentNode, rawMarkdown, blockType))
                }
            }
            currentNode = currentNode.next
        }

        return MarkdownDocument(
            id = documentId,
            title = title,
            blocks = if (blocks.isEmpty()) {
                listOf(MarkdownBlock(rawContent = rawMarkdown, type = BlockType.Paragraph, plainText = rawMarkdown))
            } else {
                blocks
            }
        )
    }

    private fun processBulletList(list: BulletList, rawMarkdown: String, blocks: MutableList<MarkdownBlock>) {
        var itemNode: Node? = list.firstChild
        while (itemNode != null) {
            if (itemNode is ListItem) {
                blocks.add(createBlockFromNode(itemNode, rawMarkdown, BlockType.ListItem(ordered = false)))
            }
            itemNode = itemNode.next
        }
    }

    private fun processOrderedList(list: OrderedList, rawMarkdown: String, blocks: MutableList<MarkdownBlock>) {
        var itemNode: Node? = list.firstChild
        var index = list.startNumber
        while (itemNode != null) {
            if (itemNode is ListItem) {
                blocks.add(createBlockFromNode(itemNode, rawMarkdown, BlockType.ListItem(ordered = true, index = index++)))
            }
            itemNode = itemNode.next
        }
    }

    override fun parseBlock(rawBlockContent: String, existingId: BlockId): MarkdownBlock {
        if (rawBlockContent.isBlank()) {
            return MarkdownBlock(
                id = existingId,
                rawContent = rawBlockContent,
                type = BlockType.Paragraph,
                inlines = emptyList(),
                plainText = ""
            )
        }

        val doc = parser.parse(rawBlockContent)
        val firstChild = doc.firstChild
        if (firstChild == null) {
            return MarkdownBlock(
                id = existingId,
                rawContent = rawBlockContent,
                type = BlockType.Paragraph,
                inlines = emptyList(),
                plainText = rawBlockContent
            )
        }

        return when (firstChild) {
            is BulletList -> {
                val item = firstChild.firstChild
                if (item is ListItem) {
                    createBlockFromNode(item, rawBlockContent, BlockType.ListItem(ordered = false), existingId)
                } else {
                    createBlockFromNode(firstChild, rawBlockContent, BlockType.Paragraph, existingId)
                }
            }
            is OrderedList -> {
                val item = firstChild.firstChild
                if (item is ListItem) {
                    createBlockFromNode(item, rawBlockContent, BlockType.ListItem(ordered = true, index = firstChild.startNumber), existingId)
                } else {
                    createBlockFromNode(firstChild, rawBlockContent, BlockType.Paragraph, existingId)
                }
            }
            else -> {
                val type = determineBlockType(firstChild)
                createBlockFromNode(firstChild, rawBlockContent, type, existingId)
            }
        }
    }

    override fun updateBlock(
        existingDocument: MarkdownDocument,
        targetBlockId: BlockId,
        newRawContent: String
    ): MarkdownDocument {
        val updatedBlocks = existingDocument.blocks.map { block ->
            if (block.id == targetBlockId) {
                parseBlock(newRawContent, targetBlockId)
            } else {
                block
            }
        }
        return existingDocument.copy(blocks = updatedBlocks)
    }

    private fun determineBlockType(node: Node): BlockType {
        return when (node) {
            is Heading -> BlockType.Heading(level = node.level)
            is FencedCodeBlock -> BlockType.CodeBlock(language = node.info?.takeIf { it.isNotBlank() })
            is IndentedCodeBlock -> BlockType.CodeBlock(language = null)
            is BlockQuote -> BlockType.BlockQuote
            is ThematicBreak -> BlockType.ThematicBreak
            else -> BlockType.Paragraph
        }
    }

    private fun extractRawContent(node: Node, fullMarkdown: String): String {
        val spans = node.sourceSpans
        if (!spans.isNullOrEmpty()) {
            val first = spans.first()
            val last = spans.last()
            val start = first.inputIndex
            val end = last.inputIndex + last.length
            if (start in 0..fullMarkdown.length && end in start..fullMarkdown.length) {
                return fullMarkdown.substring(start, end).trimEnd('\r', '\n')
            }
        }
        return fullMarkdown
    }

    private fun createBlockFromNode(
        node: Node,
        fullMarkdown: String,
        blockType: BlockType,
        blockId: BlockId = BlockId()
    ): MarkdownBlock {
        val raw = extractRawContent(node, fullMarkdown)

        return when (node) {
            is FencedCodeBlock, is IndentedCodeBlock -> {
                val literal = if (node is FencedCodeBlock) node.literal else (node as IndentedCodeBlock).literal
                MarkdownBlock(
                    id = blockId,
                    rawContent = raw,
                    type = blockType,
                    inlines = emptyList(),
                    plainText = literal?.trimEnd('\r', '\n') ?: ""
                )
            }
            is ThematicBreak -> {
                MarkdownBlock(
                    id = blockId,
                    rawContent = raw,
                    type = blockType,
                    inlines = emptyList(),
                    plainText = ""
                )
            }
            else -> {
                val builder = StringBuilder()
                val inlines = mutableListOf<InlineSpan>()
                collectInlines(node, builder, inlines)
                val plainText = builder.toString()
                MarkdownBlock(
                    id = blockId,
                    rawContent = raw,
                    type = blockType,
                    inlines = inlines,
                    plainText = plainText
                )
            }
        }
    }

    private fun collectInlines(
        node: Node,
        plainText: StringBuilder,
        inlines: MutableList<InlineSpan>
    ) {
        var child = node.firstChild
        while (child != null) {
            when (child) {
                is Text -> {
                    plainText.append(child.literal)
                }
                is SoftLineBreak -> {
                    plainText.append("\n")
                }
                is HardLineBreak -> {
                    plainText.append("\n")
                }
                is Code -> {
                    val start = plainText.length
                    plainText.append(child.literal)
                    val end = plainText.length
                    inlines.add(InlineSpan.InlineCode(start, end))
                }
                is Emphasis -> {
                    val start = plainText.length
                    collectInlines(child, plainText, inlines)
                    val end = plainText.length
                    inlines.add(InlineSpan.Italic(start, end))
                }
                is StrongEmphasis -> {
                    val start = plainText.length
                    collectInlines(child, plainText, inlines)
                    val end = plainText.length
                    inlines.add(InlineSpan.Bold(start, end))
                }
                is Link -> {
                    val start = plainText.length
                    collectInlines(child, plainText, inlines)
                    val end = plainText.length
                    inlines.add(InlineSpan.Link(start, end, child.destination, child.title))
                }
                else -> {
                    collectInlines(child, plainText, inlines)
                }
            }
            child = child.next
        }
    }
}


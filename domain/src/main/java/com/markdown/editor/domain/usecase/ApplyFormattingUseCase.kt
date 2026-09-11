package com.markdown.editor.domain.usecase

import com.markdown.editor.domain.model.MarkdownFormatAction

/**
 * Result of applying a formatting action to a block's content.
 */
data class FormattingResult(
    val newContent: String,
    val cursorPosition: Int,
    val selectionEnd: Int = cursorPosition
)

/**
 * Pure domain use case that applies markdown formatting (bold, italic, list markers, code blocks, etc.)
 * to text based on cursor position or active selection range.
 */
class ApplyFormattingUseCase {

    operator fun invoke(
        content: String,
        selectionStart: Int,
        selectionEnd: Int,
        action: MarkdownFormatAction
    ): FormattingResult {
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, content.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(0, content.length)
        val selectedText = content.substring(start, end)

        return when (action) {
            MarkdownFormatAction.BOLD -> applyWrap(content, start, end, selectedText, "**", "**")
            MarkdownFormatAction.ITALIC -> applyWrap(content, start, end, selectedText, "*", "*")
            MarkdownFormatAction.STRIKETHROUGH -> applyWrap(content, start, end, selectedText, "~~", "~~")
            MarkdownFormatAction.INLINE_CODE -> applyWrap(content, start, end, selectedText, "`", "`")
            MarkdownFormatAction.CODE_BLOCK -> applyCodeBlock(content, start, end, selectedText)
            MarkdownFormatAction.QUOTE -> applyLinePrefix(content, start, end, "> ")
            MarkdownFormatAction.BULLET_LIST -> applyLinePrefix(content, start, end, "- ")
            MarkdownFormatAction.NUMBERED_LIST -> applyNumberedList(content, start, end)
            MarkdownFormatAction.HEADING -> cycleHeading(content, start, end)
            MarkdownFormatAction.LINK -> applyLink(content, start, end, selectedText)
            MarkdownFormatAction.HORIZONTAL_RULE -> applyHorizontalRule(content, start, end)
        }
    }

    private fun applyWrap(
        content: String,
        start: Int,
        end: Int,
        selectedText: String,
        prefix: String,
        suffix: String
    ): FormattingResult {
        val pLen = prefix.length
        val sLen = suffix.length

        // Check if selection is already wrapped by prefix/suffix -> toggle off
        val isWrapped = start >= pLen && end <= content.length - sLen &&
                content.substring(start - pLen, start) == prefix &&
                content.substring(end, end + sLen) == suffix

        if (isWrapped) {
            val unwrapped = content.substring(0, start - pLen) + selectedText + content.substring(end + sLen)
            val newStart = start - pLen
            val newEnd = newStart + selectedText.length
            return FormattingResult(unwrapped, newStart, newEnd)
        }

        // Check if selectedText itself starts with prefix and ends with suffix -> toggle off
        if (selectedText.length >= pLen + sLen &&
            selectedText.startsWith(prefix) && selectedText.endsWith(suffix)
        ) {
            val unwrappedText = selectedText.substring(pLen, selectedText.length - sLen)
            val newContent = content.substring(0, start) + unwrappedText + content.substring(end)
            return FormattingResult(newContent, start, start + unwrappedText.length)
        }

        if (start == end) {
            // Nothing selected: insert prefix + suffix and place cursor between them
            val newContent = content.substring(0, start) + prefix + suffix + content.substring(end)
            val newCursor = start + pLen
            return FormattingResult(newContent, newCursor, newCursor)
        } else {
            // Wrap selected text
            val newContent = content.substring(0, start) + prefix + selectedText + suffix + content.substring(end)
            val newStart = start + pLen
            val newEnd = newStart + selectedText.length
            return FormattingResult(newContent, newStart, newEnd)
        }
    }

    private fun applyCodeBlock(
        content: String,
        start: Int,
        end: Int,
        selectedText: String
    ): FormattingResult {
        if (start == end) {
            val prefix = if (content.isEmpty() || content.endsWith("\n")) "```\n" else "\n```\n"
            val suffix = "\n```"
            val newContent = content.substring(0, start) + prefix + suffix + content.substring(end)
            val newCursor = start + prefix.length
            return FormattingResult(newContent, newCursor, newCursor)
        } else {
            val newContent = content.substring(0, start) + "```\n$selectedText\n```" + content.substring(end)
            return FormattingResult(newContent, start + 4, start + 4 + selectedText.length)
        }
    }

    private fun applyLinePrefix(
        content: String,
        start: Int,
        end: Int,
        prefix: String
    ): FormattingResult {
        val trimmed = content.trimStart()
        val leadingSpaces = content.takeWhile { it == ' ' }

        return if (trimmed.startsWith(prefix)) {
            // Toggle OFF: remove prefix
            val newContent = leadingSpaces + trimmed.removePrefix(prefix)
            val newCursor = (start - prefix.length).coerceAtLeast(leadingSpaces.length)
            FormattingResult(newContent, newCursor, newCursor)
        } else {
            // Strip any alternative list/quote prefix if present
            val stripped = trimmed
                .removePrefix("- ")
                .removePrefix("* ")
                .removePrefix("+ ")
                .removePrefix("> ")
                .replace(Regex("^\\d+\\.\\s*"), "")

            val newContent = leadingSpaces + prefix + stripped
            val newCursor = (start + prefix.length).coerceIn(0, newContent.length)
            FormattingResult(newContent, newCursor, newCursor)
        }
    }

    private fun applyNumberedList(
        content: String,
        start: Int,
        end: Int
    ): FormattingResult {
        val trimmed = content.trimStart()
        val leadingSpaces = content.takeWhile { it == ' ' }
        val numberedRegex = Regex("^\\d+\\.\\s*")

        return if (numberedRegex.containsMatchIn(trimmed)) {
            // Toggle OFF
            val newContent = leadingSpaces + trimmed.replaceFirst(numberedRegex, "")
            FormattingResult(newContent, leadingSpaces.length, leadingSpaces.length)
        } else {
            // Strip alternative bullet or quote
            val stripped = trimmed
                .removePrefix("- ")
                .removePrefix("* ")
                .removePrefix("+ ")
                .removePrefix("> ")
            val newContent = leadingSpaces + "1. " + stripped
            val newCursor = (start + 3).coerceIn(0, newContent.length)
            FormattingResult(newContent, newCursor, newCursor)
        }
    }

    private fun cycleHeading(
        content: String,
        start: Int,
        end: Int
    ): FormattingResult {
        val headingRegex = Regex("^(#{1,6})\\s*")
        val match = headingRegex.find(content)

        return if (match != null) {
            val level = match.groupValues[1].length
            val remainder = content.substring(match.range.last + 1)
            if (level < 3) {
                // # -> ## -> ###
                val nextHashes = "#".repeat(level + 1)
                val newContent = "$nextHashes $remainder"
                FormattingResult(newContent, (start + 1).coerceIn(0, newContent.length))
            } else {
                // ### -> plain paragraph (remove heading)
                FormattingResult(remainder, (start - match.value.length).coerceAtLeast(0))
            }
        } else {
            // Plain -> # Heading
            val newContent = "# $content"
            FormattingResult(newContent, (start + 2).coerceIn(0, newContent.length))
        }
    }

    private fun applyLink(
        content: String,
        start: Int,
        end: Int,
        selectedText: String
    ): FormattingResult {
        return if (start == end) {
            val linkText = "[link](url)"
            val newContent = content.substring(0, start) + linkText + content.substring(end)
            FormattingResult(newContent, start + 1, start + 5)
        } else {
            val newContent = content.substring(0, start) + "[$selectedText](url)" + content.substring(end)
            val urlStart = start + selectedText.length + 3
            val urlEnd = urlStart + 3
            FormattingResult(newContent, urlStart, urlEnd)
        }
    }

    private fun applyHorizontalRule(
        content: String,
        start: Int,
        end: Int
    ): FormattingResult {
        val newContent = "---"
        return FormattingResult(newContent, 3, 3)
    }
}


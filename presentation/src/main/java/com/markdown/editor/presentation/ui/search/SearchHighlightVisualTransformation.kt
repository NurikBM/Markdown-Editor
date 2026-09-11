package com.markdown.editor.presentation.ui.search

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * VisualTransformation that highlights all occurrences of a search query in a text field,
 * with special accent styling for the active match.
 *
 * Supports chaining over a [baseTransformation] (such as code syntax highlighting)
 * and maintains an exact 1:1 character offset mapping without mutating underlying strings.
 */
class SearchHighlightVisualTransformation(
    private val searchQuery: String,
    private val isCaseSensitive: Boolean = false,
    private val activeMatchRange: IntRange? = null,
    private val baseTransformation: VisualTransformation = VisualTransformation.None,
    private val highlightColor: Color = Color(0x66FFEB3B), // Soft translucent yellow
    private val activeHighlightColor: Color = Color(0xFFFF9800), // Vibrant orange
    private val activeTextColor: Color = Color.White
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        val baseTransformed = baseTransformation.filter(text)
        if (searchQuery.isEmpty()) {
            return baseTransformed
        }

        val rawText = baseTransformed.text.text
        val builder = AnnotatedString.Builder(baseTransformed.text)

        val step = searchQuery.length.coerceAtLeast(1)
        var startIndex = 0
        while (startIndex < rawText.length) {
            val foundIndex = rawText.indexOf(searchQuery, startIndex, ignoreCase = !isCaseSensitive)
            if (foundIndex == -1) break
            val endIndex = foundIndex + searchQuery.length

            val isActive = activeMatchRange != null &&
                    activeMatchRange.first == foundIndex &&
                    activeMatchRange.last == endIndex

            if (isActive) {
                builder.addStyle(
                    SpanStyle(background = activeHighlightColor, color = activeTextColor),
                    foundIndex,
                    endIndex
                )
            } else {
                builder.addStyle(
                    SpanStyle(background = highlightColor),
                    foundIndex,
                    endIndex
                )
            }
            startIndex = foundIndex + step
        }

        return TransformedText(builder.toAnnotatedString(), baseTransformed.offsetMapping)
    }
}


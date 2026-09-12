package com.markdown.editor.data.converter

import android.content.Context
import com.markdown.editor.domain.converter.ConvertedDocument
import com.markdown.editor.domain.error.DomainError
import com.markdown.editor.domain.error.asException
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStreamWriter
import java.io.Writer

/**
 * Intelligent PDF to Markdown converter using pdfbox-android.
 * Performs best-effort structural reconstruction by inspecting glyph coordinates,
 * font sizes for headings, font weights for bold/italic, and bullet markers for lists.
 */
class PdfToMarkdownConverter(
    private val context: Context
) {

    @Volatile
    private var isInitialized = false

    private fun ensureInitialized() {
        if (!isInitialized) {
            synchronized(this) {
                if (!isInitialized) {
                    PDFBoxResourceLoader.init(context.applicationContext)
                    isInitialized = true
                }
            }
        }
    }

    fun convert(fileName: String, inputStream: InputStream): ConvertedDocument {
        ensureInitialized()
        val title = fileName.substringBeforeLast('.')

        val document = try {
            PDDocument.load(inputStream)
        } catch (e: Exception) {
            val message = e.message ?: ""
            if (message.contains("password", ignoreCase = true) || message.contains("encrypted", ignoreCase = true)) {
                throw DomainError.Conversion.PasswordProtected(fileName).asException()
            }
            throw DomainError.Conversion.CorruptedFile(fileName, "Failed to parse PDF: ${e.message}").asException()
        }

        document.use { doc ->
            if (doc.isEncrypted) {
                throw DomainError.Conversion.PasswordProtected(fileName).asException()
            }

            val numPages = doc.numberOfPages
            if (numPages == 0) {
                throw DomainError.Conversion.EmptyDocument(fileName).asException()
            }

            val fullDocumentMarkdown = StringBuilder()

            for (pageIndex in 1..numPages) {
                val stripper = MarkdownPdfStripper()
                stripper.sortByPosition = true
                stripper.startPage = pageIndex
                stripper.endPage = pageIndex

                val baos = ByteArrayOutputStream()
                val writer = OutputStreamWriter(baos, Charsets.UTF_8)
                stripper.writeText(doc, writer)
                writer.flush()

                val pageMarkdown = baos.toString(Charsets.UTF_8.name()).trim()
                if (pageMarkdown.isNotEmpty()) {
                    if (numPages > 1) {
                        if (pageIndex > 1) {
                            fullDocumentMarkdown.append("\n\n---\n\n")
                        }
                        fullDocumentMarkdown.append("## Page ").append(pageIndex).append("\n\n")
                    }
                    fullDocumentMarkdown.append(pageMarkdown)
                }
            }

            val finalContent = fullDocumentMarkdown.toString().trim()
            if (finalContent.isEmpty()) {
                return ConvertedDocument(
                    title = title,
                    markdownContent = "# $title\n\n> [!NOTE]\n> This PDF contains scanned images or non-extractable glyphs with no embedded text layer.",
                    isBestEffort = true,
                    warningMessage = "This PDF appears to be a scanned image without an embedded text layer."
                )
            }

            return ConvertedDocument(
                title = title,
                markdownContent = finalContent,
                isBestEffort = true,
                warningMessage = "PDF imported with best-effort layout. Please review headings, lists, and tables."
            )
        }
    }

    /**
     * Custom text stripper examining font size, font style, and line spacing
     * to reconstruct Markdown blocks and inline formatting.
     */
    private class MarkdownPdfStripper : PDFTextStripper() {

        private val lineBuffer = StringBuilder()
        private val outputBuilder = StringBuilder()
        private var currentLinePositions = mutableListOf<TextPosition>()

        // Document or page level base font size (modal size)
        private var baseFontSize = 11.0f

        override fun writeString(text: String, textPositions: List<TextPosition>) {
            if (textPositions.isEmpty()) return
            currentLinePositions.addAll(textPositions)
        }

        override fun writeLineSeparator() {
            if (currentLinePositions.isNotEmpty()) {
                processLine(currentLinePositions)
                currentLinePositions.clear()
            }
            super.writeLineSeparator()
        }

        override fun writeText(doc: PDDocument, outputStream: Writer) {
            super.writeText(doc, outputStream)
            // Process any trailing line
            if (currentLinePositions.isNotEmpty()) {
                processLine(currentLinePositions)
                currentLinePositions.clear()
            }
            outputStream.write(outputBuilder.toString())
        }

        private fun processLine(positions: List<TextPosition>) {
            if (positions.isEmpty()) return

            // Compute dominant font size for this line
            val avgFontSize = positions.map { it.fontSizeInPt }.average().toFloat()
            val lineRawText = positions.joinToString("") { it.unicode }.trim()
            if (lineRawText.isEmpty()) return

            val isShortLine = lineRawText.length < 80
            val isHeading1 = isShortLine && avgFontSize >= 18.0f
            val isHeading2 = isShortLine && avgFontSize in 14.5f..17.9f
            val isHeading3 = isShortLine && avgFontSize in 12.5f..14.4f

            // Format inline runs (bold, italic)
            val formattedLine = formatInlineRuns(positions)

            val bulletRegex = Regex("""^([•–—▪*\-]|\d+[\.\)])\s+(.*)$""")
            val bulletMatch = bulletRegex.find(formattedLine)

            when {
                isHeading1 -> {
                    outputBuilder.append("# ").append(lineRawText).append("\n\n")
                }
                isHeading2 -> {
                    outputBuilder.append("## ").append(lineRawText).append("\n\n")
                }
                isHeading3 -> {
                    outputBuilder.append("### ").append(lineRawText).append("\n\n")
                }
                bulletMatch != null -> {
                    val marker = bulletMatch.groupValues[1]
                    val content = bulletMatch.groupValues[2]
                    val isNumbered = marker.first().isDigit()
                    val prefix = if (isNumbered) marker else "-"
                    outputBuilder.append(prefix).append(" ").append(content).append("\n")
                }
                else -> {
                    outputBuilder.append(formattedLine).append("\n\n")
                }
            }
        }

        private fun formatInlineRuns(positions: List<TextPosition>): String {
            val sb = StringBuilder()
            var currentRun = StringBuilder()
            var currentIsBold = false
            var currentIsItalic = false

            fun flushRun() {
                if (currentRun.isEmpty()) return
                val text = currentRun.toString()
                currentRun.setLength(0)

                val leading = text.takeWhile { it.isWhitespace() }
                val trailing = text.takeLastWhile { it.isWhitespace() }
                val trimmed = text.trim()

                if (trimmed.isEmpty()) {
                    sb.append(text)
                    return
                }

                var formatted = trimmed
                if (currentIsBold && currentIsItalic) formatted = "***$formatted***"
                else if (currentIsBold) formatted = "**$formatted**"
                else if (currentIsItalic) formatted = "*$formatted*"

                sb.append(leading).append(formatted).append(trailing)
            }

            for (pos in positions) {
                val isBold = isBold(pos)
                val isItalic = isItalic(pos)

                if (isBold != currentIsBold || isItalic != currentIsItalic) {
                    flushRun()
                    currentIsBold = isBold
                    currentIsItalic = isItalic
                }
                currentRun.append(pos.unicode)
            }
            flushRun()

            return sb.toString().trim()
        }

        private fun isBold(pos: TextPosition): Boolean {
            val font = pos.font ?: return false
            val name = font.name ?: ""
            if (name.contains("Bold", ignoreCase = true) ||
                name.contains("Black", ignoreCase = true) ||
                name.contains("Heavy", ignoreCase = true)
            ) return true

            val descriptor = font.fontDescriptor
            if (descriptor != null) {
                if (descriptor.isForceBold) return true
                if (descriptor.fontWeight >= 700) return true
            }
            return false
        }

        private fun isItalic(pos: TextPosition): Boolean {
            val font = pos.font ?: return false
            val name = font.name ?: ""
            if (name.contains("Italic", ignoreCase = true) ||
                name.contains("Oblique", ignoreCase = true)
            ) return true

            val descriptor = font.fontDescriptor
            if (descriptor != null) {
                if (descriptor.isItalic) return true
            }
            return false
        }
    }
}


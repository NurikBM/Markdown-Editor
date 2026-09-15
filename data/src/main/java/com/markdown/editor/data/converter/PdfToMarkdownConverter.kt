package com.markdown.editor.data.converter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.markdown.editor.data.ocr.MlKitDocumentOcrScanner
import com.markdown.editor.domain.converter.ConvertedDocument
import com.markdown.editor.domain.converter.DocumentConverter
import com.markdown.editor.domain.error.DomainError
import com.markdown.editor.domain.error.asException
import com.markdown.editor.domain.ocr.DocumentOcrScanner
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStreamWriter
import java.io.Writer

/**
 * Intelligent PDF to Markdown converter using pdfbox-android with on-device OCR fallback.
 * Performs structural text extraction, and for scanned/image-only PDFs, falls back to
 * rendering pages via [PdfRenderer] and running [DocumentOcrScanner].
 */
class PdfToMarkdownConverter(
    private val context: Context,
    private val ocrScanner: DocumentOcrScanner? = null
) : DocumentConverter {

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

    override fun canConvert(extension: String): Boolean {
        return extension.equals("pdf", ignoreCase = true) || extension.endsWith(".pdf", ignoreCase = true)
    }

    override suspend fun convert(fileName: String, inputStream: InputStream): ConvertedDocument {
        ensureInitialized()
        val title = fileName.substringBeforeLast('.')
        val pdfBytes = inputStream.readBytes()
        val document = loadPdfDocument(fileName, ByteArrayInputStream(pdfBytes))

        document.use { doc ->
            if (doc.isEncrypted) {
                throw DomainError.Conversion.PasswordProtected(fileName).asException()
            }

            val numPages = doc.numberOfPages
            if (numPages == 0) {
                throw DomainError.Conversion.EmptyDocument(fileName).asException()
            }

            val finalContent = buildMultiPageMarkdown(doc, numPages)
            if (finalContent.isBlank()) {
                val ocrContent = tryOcrPages(pdfBytes, title)
                if (!ocrContent.isNullOrBlank()) {
                    return ConvertedDocument(
                        title = title,
                        markdownContent = ocrContent,
                        isBestEffort = true,
                        warningMessage = "Text recognized from scanned PDF pages via Google ML Kit OCR."
                    )
                }

                return ConvertedDocument(
                    title = title,
                    markdownContent = "# $title\n\n> [!NOTE]\n> Pure-text PDF extraction found no embedded text layer in this document. If this is a scanned document or photograph, please use the **Scan Document (OCR with Google ML Kit)** tool to extract text directly from image pages.",
                    isBestEffort = true,
                    warningMessage = "This PDF has no embedded text layer. Use Scan Document (OCR) for scanned images."
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

    private suspend fun tryOcrPages(pdfBytes: ByteArray, title: String): String? {
        val scanner = ocrScanner ?: return null
        val tempFile = File.createTempFile("pdf_scan_", ".pdf", context.cacheDir)
        return try {
            tempFile.writeBytes(pdfBytes)
            val pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY) ?: return null
            pfd.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    val pageCount = renderer.pageCount
                    if (pageCount == 0) return null

                    val pageContents = mutableListOf<String>()
                    for (pageIndex in 0 until pageCount) {
                        val pageText = ocrSinglePage(renderer, pageIndex, scanner, title)
                        if (pageText.isNotBlank()) {
                            pageContents.add(pageText)
                        }
                    }

                    if (pageContents.isEmpty()) null else pageContents.joinToString("\n\n---\n\n")
                }
            }
        } catch (_: Exception) {
            null
        } finally {
            if (!tempFile.delete()) {
                tempFile.deleteOnExit()
            }
        }
    }

    private suspend fun ocrSinglePage(
        renderer: PdfRenderer,
        pageIndex: Int,
        scanner: DocumentOcrScanner,
        title: String
    ): String {
        return try {
            renderer.openPage(pageIndex).use { page ->
                val width = (page.width * 2).coerceAtMost(2048)
                val height = (page.height * 2).coerceAtMost(2048)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                val pageTitle = "$title Page ${pageIndex + 1}"
                if (scanner is MlKitDocumentOcrScanner) {
                    val result = scanner.recognizeBitmap(bitmap, pageTitle)
                    bitmap.recycle()
                    result.getOrNull()?.markdownContent ?: ""
                } else {
                    val baos = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                    bitmap.recycle()
                    val result = scanner.recognizeText(baos.toByteArray(), pageTitle)
                    result.getOrNull()?.markdownContent ?: ""
                }
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun loadPdfDocument(fileName: String, inputStream: InputStream): PDDocument {
        return try {
            PDDocument.load(inputStream)
        } catch (e: Exception) {
            val message = e.message ?: ""
            if (message.contains("password", ignoreCase = true) || message.contains("encrypted", ignoreCase = true)) {
                throw DomainError.Conversion.PasswordProtected(fileName).asException()
            }
            throw DomainError.Conversion.CorruptedFile(fileName, "Failed to parse PDF: ${e.message}").asException()
        }
    }

    private fun extractPageMarkdown(doc: PDDocument, pageIndex: Int): String {
        val stripper = MarkdownPdfStripper().apply {
            sortByPosition = true
            startPage = pageIndex
            endPage = pageIndex
        }
        val baos = ByteArrayOutputStream()
        val writer = OutputStreamWriter(baos, Charsets.UTF_8)
        stripper.writeText(doc, writer)
        writer.flush()
        return baos.toString(Charsets.UTF_8.name()).trim()
    }

    private fun buildMultiPageMarkdown(doc: PDDocument, numPages: Int): String {
        val fullDocumentMarkdown = StringBuilder()
        for (pageIndex in 1..numPages) {
            val pageMarkdown = extractPageMarkdown(doc, pageIndex)
            if (pageMarkdown.isEmpty()) continue

            if (numPages > 1) {
                if (pageIndex > 1) {
                    fullDocumentMarkdown.append("\n\n---\n\n")
                }
                fullDocumentMarkdown.append("## Page ").append(pageIndex).append("\n\n")
            }
            fullDocumentMarkdown.append(pageMarkdown)
        }
        return fullDocumentMarkdown.toString().trim()
    }

    /**
     * Custom text stripper examining font size, font style, and line spacing
     * to reconstruct Markdown blocks and inline formatting.
     */
    private class MarkdownPdfStripper : PDFTextStripper() {

        private val outputBuilder = StringBuilder()
        private val currentLinePositions = mutableListOf<TextPosition>()

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
            if (currentLinePositions.isNotEmpty()) {
                processLine(currentLinePositions)
                currentLinePositions.clear()
            }
            outputStream.write(outputBuilder.toString())
        }

        private fun processLine(positions: List<TextPosition>) {
            if (positions.isEmpty()) return

            val avgFontSize = positions.map { it.fontSizeInPt }.average().toFloat()
            val lineRawText = positions.joinToString("") { it.unicode }.trim()
            if (lineRawText.isEmpty()) return

            val isShortLine = lineRawText.length < 80
            val isHeading1 = isShortLine && avgFontSize >= 18.0f
            val isHeading2 = isShortLine && avgFontSize in 14.5f..17.9f
            val isHeading3 = isShortLine && avgFontSize in 12.5f..14.4f

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
            val currentRun = StringBuilder()
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

                val formatted = when {
                    currentIsBold && currentIsItalic -> "***$trimmed***"
                    currentIsBold -> "**$trimmed**"
                    currentIsItalic -> "*$trimmed*"
                    else -> trimmed
                }

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
            return descriptor != null && (descriptor.isForceBold || descriptor.fontWeight >= 700)
        }

        private fun isItalic(pos: TextPosition): Boolean {
            val font = pos.font ?: return false
            val name = font.name ?: ""
            if (name.contains("Italic", ignoreCase = true) ||
                name.contains("Oblique", ignoreCase = true)
            ) return true

            val descriptor = font.fontDescriptor
            return descriptor != null && descriptor.isItalic
        }
    }
}

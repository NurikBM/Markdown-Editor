package com.markdown.editor.data.converter

import android.content.Context
import com.markdown.editor.domain.converter.ConvertedDocument
import com.markdown.editor.domain.converter.DocumentConverter
import com.markdown.editor.domain.error.DomainError
import com.markdown.editor.domain.error.asException
import java.io.InputStream

/**
 * Composite converter routing supported external file formats to their specialized converters.
 */
class CompositeDocumentConverter(
    context: Context,
    private val docxConverter: DocxToMarkdownConverter = DocxToMarkdownConverter(),
    private val xlsxConverter: XlsxToMarkdownConverter = XlsxToMarkdownConverter(),
    private val csvConverter: CsvToMarkdownConverter = CsvToMarkdownConverter(),
    private val htmlConverter: HtmlToMarkdownConverter = HtmlToMarkdownConverter(),
    private val pdfConverter: PdfToMarkdownConverter = PdfToMarkdownConverter(context)
) : DocumentConverter {

    private val supportedExtensions = setOf(
        "docx",
        "xlsx",
        "pdf",
        "html", "htm",
        "csv", "tsv",
        "json", "xml", "yaml", "yml"
    )

    override fun canConvert(extension: String): Boolean {
        return supportedExtensions.contains(extension.lowercase())
    }

    override suspend fun convert(fileName: String, inputStream: InputStream): ConvertedDocument {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val baseTitle = fileName.substringBeforeLast('.')

        return when (extension) {
            "docx" -> docxConverter.convert(fileName, inputStream)
            "xlsx" -> xlsxConverter.convert(fileName, inputStream)
            "pdf" -> pdfConverter.convert(fileName, inputStream)
            "html", "htm" -> htmlConverter.convert(fileName, inputStream)
            "csv", "tsv" -> csvConverter.convert(fileName, inputStream)
            "json" -> {
                val content = inputStream.bufferedReader().readText().trim()
                ConvertedDocument(
                    title = baseTitle,
                    markdownContent = "# $baseTitle\n\n```json\n$content\n```",
                    isBestEffort = false
                )
            }
            "xml" -> {
                val content = inputStream.bufferedReader().readText().trim()
                ConvertedDocument(
                    title = baseTitle,
                    markdownContent = "# $baseTitle\n\n```xml\n$content\n```",
                    isBestEffort = false
                )
            }
            "yaml", "yml" -> {
                val content = inputStream.bufferedReader().readText().trim()
                ConvertedDocument(
                    title = baseTitle,
                    markdownContent = "# $baseTitle\n\n```yaml\n$content\n```",
                    isBestEffort = false
                )
            }
            else -> throw DomainError.Conversion.UnsupportedFormat(extension).asException()
        }
    }
}


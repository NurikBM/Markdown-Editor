package com.markdown.editor.data.converter

import com.markdown.editor.domain.converter.ConvertedDocument
import com.markdown.editor.domain.converter.DocumentConverter
import com.markdown.editor.domain.error.DomainError
import com.markdown.editor.domain.error.asException
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Converter extracting structured Markdown from Microsoft Word (.docx) documents.
 * Word documents use Office OpenXML (ZIP archive containing word/document.xml).
 */
class DocxToMarkdownConverter : DocumentConverter {

    override fun canConvert(extension: String): Boolean {
        return extension.equals("docx", ignoreCase = true) || extension.endsWith(".docx", ignoreCase = true)
    }

    override suspend fun convert(fileName: String, inputStream: InputStream): ConvertedDocument {
        val title = fileName.removeSuffix(".docx").removeSuffix(".DOCX")
        val documentXmlBytes = extractDocumentXml(inputStream)
            ?: throw DomainError.Conversion.CorruptedFile(fileName, "Could not find word/document.xml in DOCX archive").asException()

        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(documentXmlBytes.inputStream())

        val bodyNode = doc.getElementsByTagNameNS("*", "body").item(0) as? Element
            ?: throw DomainError.Conversion.CorruptedFile(fileName, "Missing body element in document.xml").asException()

        val sb = StringBuilder()
        val children = bodyNode.childNodes

        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue
            val element = child as Element

            when (element.localName) {
                "p" -> {
                    val paragraphText = parseParagraph(element)
                    if (paragraphText.isNotBlank()) {
                        sb.append(paragraphText).append("\n\n")
                    }
                }
                "tbl" -> {
                    val tableText = parseTable(element)
                    if (tableText.isNotBlank()) {
                        sb.append(tableText).append("\n\n")
                    }
                }
            }
        }

        val result = sb.toString().trim()
        if (result.isEmpty()) {
            return ConvertedDocument(
                title = title,
                markdownContent = "# $title\n\n*(Empty document)*",
                isBestEffort = false
            )
        }

        return ConvertedDocument(
            title = title,
            markdownContent = result,
            isBestEffort = false
        )
    }

    private fun extractDocumentXml(inputStream: InputStream): ByteArray? {
        val zip = ZipInputStream(inputStream)
        var entry = zip.nextEntry
        while (entry != null) {
            if (entry.name == "word/document.xml") {
                return zip.readBytes()
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
        return null
    }

    private fun parseParagraph(pElement: Element): String {
        val headingPrefix = getHeadingPrefix(pElement)
        val isListItem = isListItem(pElement)

        val textBuilder = StringBuilder()
        val children = pElement.childNodes

        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue
            val element = child as Element

            when (element.localName) {
                "r" -> {
                    val runText = parseRun(element)
                    textBuilder.append(runText)
                }
                "hyperlink" -> {
                    val linkText = parseHyperlink(element)
                    textBuilder.append(linkText)
                }
            }
        }

        val content = textBuilder.toString().trim()
        if (content.isEmpty()) return ""

        return when {
            headingPrefix.isNotEmpty() -> "$headingPrefix $content"
            isListItem -> "- $content"
            else -> content
        }
    }

    private fun getHeadingPrefix(pElement: Element): String {
        val pPrList = pElement.getElementsByTagNameNS("*", "pPr")
        if (pPrList.length == 0) return ""
        val pPr = pPrList.item(0) as Element

        val pStyleList = pPr.getElementsByTagNameNS("*", "pStyle")
        if (pStyleList.length == 0) return ""
        val pStyle = pStyleList.item(0) as Element
        val styleVal = pStyle.getAttributeNS("*", "val").ifEmpty { pStyle.getAttribute("w:val") }

        return when {
            styleVal.contains("Heading1", ignoreCase = true) || styleVal == "1" -> "#"
            styleVal.contains("Heading2", ignoreCase = true) || styleVal == "2" -> "##"
            styleVal.contains("Heading3", ignoreCase = true) || styleVal == "3" -> "###"
            styleVal.contains("Heading4", ignoreCase = true) || styleVal == "4" -> "####"
            styleVal.contains("Heading5", ignoreCase = true) || styleVal == "5" -> "#####"
            styleVal.contains("Heading6", ignoreCase = true) || styleVal == "6" -> "######"
            styleVal.contains("Title", ignoreCase = true) -> "#"
            styleVal.contains("Subtitle", ignoreCase = true) -> "##"
            else -> ""
        }
    }

    private fun isListItem(pElement: Element): Boolean {
        val numPrList = pElement.getElementsByTagNameNS("*", "numPr")
        return numPrList.length > 0
    }

    private data class RunStyle(
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
        val isStrike: Boolean = false,
        val isCode: Boolean = false
    )

    private fun extractRunStyle(rElement: Element): RunStyle {
        val rPrList = rElement.getElementsByTagNameNS("*", "rPr")
        if (rPrList.length == 0) return RunStyle()

        val rPr = rPrList.item(0) as Element
        val isBold = rPr.getElementsByTagNameNS("*", "b").length > 0
        val isItalic = rPr.getElementsByTagNameNS("*", "i").length > 0
        val isStrike = rPr.getElementsByTagNameNS("*", "strike").length > 0

        var isCode = false
        val rStyleList = rPr.getElementsByTagNameNS("*", "rStyle")
        if (rStyleList.length > 0) {
            val styleVal = (rStyleList.item(0) as Element).getAttribute("w:val")
            if (styleVal.contains("Code", ignoreCase = true)) {
                isCode = true
            }
        }
        return RunStyle(isBold, isItalic, isStrike, isCode)
    }

    private fun extractRunText(rElement: Element): String {
        val sb = StringBuilder()
        val children = rElement.childNodes

        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue
            val element = child as Element

            when (element.localName) {
                "t" -> sb.append(element.textContent)
                "tab" -> sb.append("    ")
                "br" -> sb.append("\n")
            }
        }
        return sb.toString()
    }

    private fun formatRunText(text: String, style: RunStyle): String {
        if (text.isEmpty()) return ""

        val leadingSpaces = text.takeWhile { it.isWhitespace() }
        val trailingSpaces = text.takeLastWhile { it.isWhitespace() }
        val trimmed = text.trim()

        if (trimmed.isEmpty()) return text

        var formatted = trimmed
        if (style.isCode) {
            formatted = "`$formatted`"
        } else {
            formatted = when {
                style.isBold && style.isItalic -> "***$formatted***"
                style.isBold -> "**$formatted**"
                style.isItalic -> "*$formatted*"
                else -> formatted
            }
            if (style.isStrike) {
                formatted = "~~$formatted~~"
            }
        }

        return "$leadingSpaces$formatted$trailingSpaces"
    }

    private fun parseRun(rElement: Element): String {
        val style = extractRunStyle(rElement)
        val text = extractRunText(rElement)
        return formatRunText(text, style)
    }

    private fun parseHyperlink(linkElement: Element): String {
        val textBuilder = StringBuilder()
        val rList = linkElement.getElementsByTagNameNS("*", "r")
        for (i in 0 until rList.length) {
            val r = rList.item(i) as Element
            textBuilder.append(parseRun(r))
        }
        return textBuilder.toString().trim()
    }

    private fun parseTableCell(tc: Element): String {
        val cellParagraphs = mutableListOf<String>()
        val pList = tc.getElementsByTagNameNS("*", "p")
        for (k in 0 until pList.length) {
            val p = pList.item(k) as Element
            val pText = parseParagraph(p)
            if (pText.isNotBlank()) cellParagraphs.add(pText)
        }
        val cellContent = cellParagraphs.joinToString("<br>").replace("|", "\\|")
        return cellContent.ifBlank { " " }
    }

    private fun parseTableRow(tr: Element): List<String> {
        val cells = mutableListOf<String>()
        val tcList = tr.getElementsByTagNameNS("*", "tc")
        for (j in 0 until tcList.length) {
            cells.add(parseTableCell(tcList.item(j) as Element))
        }
        return cells
    }

    private fun formatMarkdownTable(rows: List<List<String>>): String {
        if (rows.isEmpty()) return ""

        val maxColumns = rows.maxOf { it.size }
        val normalizedRows = rows.map { row ->
            if (row.size < maxColumns) row + List(maxColumns - row.size) { " " } else row
        }

        val sb = StringBuilder()
        val header = normalizedRows.first()
        sb.append("| ").append(header.joinToString(" | ")).append(" |\n")
        sb.append("|").append(List(maxColumns) { " --- |" }.joinToString("")).append("\n")

        for (rowIndex in 1 until normalizedRows.size) {
            val row = normalizedRows[rowIndex]
            sb.append("| ").append(row.joinToString(" | ")).append(" |\n")
        }

        return sb.toString().trimEnd()
    }

    private fun parseTable(tblElement: Element): String {
        val rows = mutableListOf<List<String>>()
        val trList = tblElement.getElementsByTagNameNS("*", "tr")
        for (i in 0 until trList.length) {
            val cells = parseTableRow(trList.item(i) as Element)
            if (cells.isNotEmpty()) rows.add(cells)
        }
        return formatMarkdownTable(rows)
    }
}

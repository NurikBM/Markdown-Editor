package com.markdown.editor.data.converter

import com.markdown.editor.domain.converter.ConvertedDocument
import com.markdown.editor.domain.error.DomainError
import com.markdown.editor.domain.error.asException
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Converter extracting structured Markdown tables from Microsoft Excel (.xlsx) workbooks.
 * Excel files are Office OpenXML ZIP archives containing shared strings and sheet data.
 */
class XlsxToMarkdownConverter {

    fun convert(fileName: String, inputStream: InputStream): ConvertedDocument {
        val title = fileName.removeSuffix(".xlsx").removeSuffix(".XLSX")
        val zipEntries = extractZipEntries(inputStream)

        val sharedStrings = parseSharedStrings(zipEntries["xl/sharedStrings.xml"])
        val sheetNames = parseSheetNames(zipEntries["xl/workbook.xml"])

        val sheetEntries = zipEntries.keys
            .filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
            .sortedBy { extractSheetIndex(it) }

        if (sheetEntries.isEmpty()) {
            throw DomainError.Conversion.CorruptedFile(fileName, "No worksheets found in XLSX archive").asException()
        }

        val sb = StringBuilder()

        for ((index, sheetKey) in sheetEntries.withIndex()) {
            val sheetName = sheetNames.getOrNull(index) ?: "Sheet ${index + 1}"
            val sheetXml = zipEntries[sheetKey] ?: continue
            val tableMarkdown = parseSheetToMarkdownTable(sheetXml, sharedStrings)

            if (tableMarkdown.isNotBlank()) {
                if (sheetEntries.size > 1) {
                    sb.append("## ").append(sheetName).append("\n\n")
                }
                sb.append(tableMarkdown).append("\n\n")
            }
        }

        val result = sb.toString().trim()
        if (result.isEmpty()) {
            return ConvertedDocument(
                title = title,
                markdownContent = "# $title\n\n*(Empty spreadsheet)*",
                isBestEffort = false
            )
        }

        return ConvertedDocument(
            title = title,
            markdownContent = result,
            isBestEffort = false
        )
    }

    private fun extractZipEntries(inputStream: InputStream): Map<String, ByteArray> {
        val map = mutableMapOf<String, ByteArray>()
        val zip = ZipInputStream(inputStream)
        var entry = zip.nextEntry
        while (entry != null) {
            val name = entry.name
            if (name == "xl/sharedStrings.xml" || name == "xl/workbook.xml" ||
                (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml"))
            ) {
                map[name] = zip.readBytes()
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
        return map
    }

    private fun parseSharedStrings(xmlBytes: ByteArray?): List<String> {
        if (xmlBytes == null) return emptyList()
        val list = mutableListOf<String>()
        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(ByteArrayInputStream(xmlBytes))

            val siList = doc.getElementsByTagNameNS("*", "si")
            for (i in 0 until siList.length) {
                val si = siList.item(i) as Element
                val textBuilder = StringBuilder()
                val tList = si.getElementsByTagNameNS("*", "t")
                for (j in 0 until tList.length) {
                    textBuilder.append(tList.item(j).textContent)
                }
                list.add(textBuilder.toString())
            }
        } catch (_: Exception) {
            // Graceful fallback on malformed shared strings
        }
        return list
    }

    private fun parseSheetNames(xmlBytes: ByteArray?): List<String> {
        if (xmlBytes == null) return emptyList()
        val names = mutableListOf<String>()
        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = true
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(ByteArrayInputStream(xmlBytes))

            val sheetList = doc.getElementsByTagNameNS("*", "sheet")
            for (i in 0 until sheetList.length) {
                val sheet = sheetList.item(i) as Element
                val name = sheet.getAttribute("name").ifEmpty {
                    sheet.getAttributeNS("*", "name")
                }
                if (name.isNotEmpty()) names.add(name)
            }
        } catch (_: Exception) {
            // Graceful fallback
        }
        return names
    }

    private fun extractSheetIndex(sheetKey: String): Int {
        val numberPart = sheetKey.removePrefix("xl/worksheets/sheet").removeSuffix(".xml")
        return numberPart.toIntOrNull() ?: 0
    }

    private fun parseSheetToMarkdownTable(xmlBytes: ByteArray, sharedStrings: List<String>): String {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(ByteArrayInputStream(xmlBytes))

        val rowList = doc.getElementsByTagNameNS("*", "row")
        if (rowList.length == 0) return ""

        val rawRows = mutableListOf<Map<Int, String>>()
        var maxColIndex = 0

        for (i in 0 until rowList.length) {
            val rowElement = rowList.item(i) as Element
            val cellMap = mutableMapOf<Int, String>()
            val cList = rowElement.getElementsByTagNameNS("*", "c")

            for (j in 0 until cList.length) {
                val c = cList.item(j) as Element
                val rRef = c.getAttribute("r")
                val colIndex = columnRefToIndex(rRef)
                val cellType = c.getAttribute("t")

                val cellValue = parseCellValue(c, cellType, sharedStrings)
                if (cellValue.isNotBlank()) {
                    cellMap[colIndex] = cellValue.replace("|", "\\|").replace("\n", " ")
                    if (colIndex > maxColIndex) {
                        maxColIndex = colIndex
                    }
                }
            }

            if (cellMap.isNotEmpty()) {
                rawRows.add(cellMap)
            }
        }

        if (rawRows.isEmpty()) return ""

        val numColumns = maxColIndex + 1
        val grid = rawRows.map { cellMap ->
            (0 until numColumns).map { col -> cellMap[col] ?: " " }
        }

        val sb = StringBuilder()
        val header = grid.first()
        sb.append("| ").append(header.joinToString(" | ")).append(" |\n")
        sb.append("|").append(List(numColumns) { " --- |" }.joinToString("")).append("\n")

        for (i in 1 until grid.size) {
            val row = grid[i]
            sb.append("| ").append(row.joinToString(" | ")).append(" |\n")
        }

        return sb.toString().trimEnd()
    }

    private fun parseCellValue(cElement: Element, cellType: String, sharedStrings: List<String>): String {
        val vList = cElement.getElementsByTagNameNS("*", "v")
        val vText = if (vList.length > 0) vList.item(0).textContent.trim() else ""

        return when (cellType) {
            "s" -> {
                val index = vText.toIntOrNull() ?: -1
                if (index in sharedStrings.indices) sharedStrings[index] else vText
            }
            "inlineStr" -> {
                val tList = cElement.getElementsByTagNameNS("*", "t")
                if (tList.length > 0) tList.item(0).textContent.trim() else ""
            }
            "b" -> if (vText == "1") "TRUE" else "FALSE"
            else -> vText
        }
    }

    private fun columnRefToIndex(cellRef: String): Int {
        val letters = cellRef.takeWhile { it.isLetter() }.uppercase()
        if (letters.isEmpty()) return 0
        var result = 0
        for (char in letters) {
            result = result * 26 + (char - 'A' + 1)
        }
        return (result - 1).coerceAtLeast(0)
    }
}


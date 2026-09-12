package com.markdown.editor.data.converter

import com.markdown.editor.domain.converter.ConvertedDocument
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Converter transforming Comma-Separated Values (.csv) and Tab-Separated Values (.tsv) into Markdown tables.
 */
class CsvToMarkdownConverter {

    fun convert(fileName: String, inputStream: InputStream): ConvertedDocument {
        val title = fileName.substringBeforeLast('.')
        val isTsv = fileName.endsWith(".tsv", ignoreCase = true)
        val delimiter = if (isTsv) '\t' else ','

        val rows = parseDelimited(inputStream, delimiter)
        if (rows.isEmpty()) {
            return ConvertedDocument(
                title = title,
                markdownContent = "# $title\n\n*(Empty CSV/TSV table)*",
                isBestEffort = false
            )
        }

        val maxCols = rows.maxOf { it.size }
        val normalizedRows = rows.map { row ->
            if (row.size < maxCols) row + List(maxCols - row.size) { " " } else row
        }

        val sb = StringBuilder()
        val header = normalizedRows.first()
        sb.append("| ").append(header.joinToString(" | ")).append(" |\n")
        sb.append("|").append(List(maxCols) { " --- |" }.joinToString("")).append("\n")

        for (i in 1 until normalizedRows.size) {
            val row = normalizedRows[i]
            sb.append("| ").append(row.joinToString(" | ")).append(" |\n")
        }

        return ConvertedDocument(
            title = title,
            markdownContent = sb.toString().trimEnd(),
            isBestEffort = false
        )
    }

    private fun parseDelimited(inputStream: InputStream, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))

        var line = reader.readLine()
        val currentRecord = StringBuilder()
        var insideQuotes = false

        while (line != null) {
            if (insideQuotes) {
                currentRecord.append("\n").append(line)
            } else {
                currentRecord.setLength(0)
                currentRecord.append(line)
            }

            // Count unescaped quotes in current accumulation
            val quoteCount = currentRecord.count { it == '"' }
            insideQuotes = (quoteCount % 2 != 0)

            if (!insideQuotes) {
                val parsedRow = parseRowTokens(currentRecord.toString(), delimiter)
                if (parsedRow.any { it.isNotBlank() }) {
                    rows.add(parsedRow)
                }
            }

            line = reader.readLine()
        }

        return rows
    }

    private fun parseRowTokens(rowString: String, delimiter: Char): List<String> {
        val tokens = mutableListOf<String>()
        val currentToken = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < rowString.length) {
            val c = rowString[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < rowString.length && rowString[i + 1] == '"') {
                        currentToken.append('"')
                        i++ // Skip escaped quote
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == delimiter && !inQuotes -> {
                    tokens.add(formatCell(currentToken.toString()))
                    currentToken.setLength(0)
                }
                else -> {
                    currentToken.append(c)
                }
            }
            i++
        }
        tokens.add(formatCell(currentToken.toString()))
        return tokens
    }

    private fun formatCell(raw: String): String {
        val cleaned = raw.trim()
            .replace("|", "\\|")
            .replace("\n", "<br>")
        return cleaned.ifBlank { " " }
    }
}


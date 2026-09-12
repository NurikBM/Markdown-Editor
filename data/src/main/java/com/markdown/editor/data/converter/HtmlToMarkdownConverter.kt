package com.markdown.editor.data.converter

import com.markdown.editor.domain.converter.ConvertedDocument
import com.markdown.editor.domain.converter.DocumentConverter
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.io.InputStream

/**
 * Converter translating HTML web pages and markup (.html, .htm) into clean Markdown.
 * Uses Jsoup for DOM hierarchy traversal.
 */
class HtmlToMarkdownConverter : DocumentConverter {

    override fun canConvert(extension: String): Boolean {
        return extension.equals("html", ignoreCase = true) ||
                extension.equals("htm", ignoreCase = true) ||
                extension.endsWith(".html", ignoreCase = true) ||
                extension.endsWith(".htm", ignoreCase = true)
    }

    override suspend fun convert(fileName: String, inputStream: InputStream): ConvertedDocument {
        val title = fileName.substringBeforeLast('.')
        val doc = Jsoup.parse(inputStream, "UTF-8", "")
        val extractedTitle = doc.title().ifBlank { title }

        val body = doc.body()
        val markdown = renderElement(body).trim()

        val finalContent = if (markdown.isEmpty()) {
            "# $extractedTitle\n\n*(Empty HTML document)*"
        } else {
            markdown
        }

        return ConvertedDocument(
            title = extractedTitle,
            markdownContent = finalContent,
            isBestEffort = false
        )
    }

    private fun renderNode(node: Node): String {
        return when (node) {
            is TextNode -> node.text()
            is Element -> renderElement(node)
            else -> ""
        }
    }

    private fun renderHeading(tag: String, element: Element): String {
        val level = tag.substring(1).toIntOrNull() ?: 1
        val prefix = "#".repeat(level)
        return "$prefix ${renderChildren(element).trim()}\n\n"
    }

    private fun renderInlineStyle(tag: String, element: Element): String {
        val text = renderChildren(element).trim()
        if (text.isEmpty()) return ""
        return when (tag) {
            "strong", "b" -> "**$text**"
            "em", "i" -> "*$text*"
            "del", "s", "strike" -> "~~$text~~"
            "a" -> {
                val href = element.attr("href").trim()
                if (href.isNotEmpty()) "[$text]($href)" else text
            }
            else -> text
        }
    }

    private fun renderCode(tag: String, element: Element): String {
        return if (tag == "pre") {
            val codeText = element.wholeText().trim()
            "```\n$codeText\n```\n\n"
        } else {
            if (element.parent()?.tagName()?.lowercase() == "pre") {
                renderChildren(element)
            } else {
                val text = renderChildren(element).trim()
                if (text.isNotEmpty()) "`$text`" else ""
            }
        }
    }

    private fun renderBlockquote(element: Element): String {
        val quoteText = renderChildren(element).trim()
        return if (quoteText.isNotEmpty()) {
            quoteText.lines().joinToString("\n") { "> $it" } + "\n\n"
        } else ""
    }

    private fun renderContainer(element: Element): String {
        val tag = element.tagName().lowercase()
        val text = renderChildren(element).trim()
        val blockTags = setOf("p", "div", "section", "article", "main", "header", "footer")
        return if (text.isNotEmpty() && tag in blockTags) "$text\n\n" else text
    }

    private fun renderElement(element: Element): String {
        val tag = element.tagName().lowercase()
        return when (tag) {
            "h1", "h2", "h3", "h4", "h5", "h6" -> renderHeading(tag, element)
            "strong", "b", "em", "i", "del", "s", "strike", "a" -> renderInlineStyle(tag, element)
            "code", "pre" -> renderCode(tag, element)
            "blockquote" -> renderBlockquote(element)
            "ul", "ol" -> renderList(element, ordered = (tag == "ol"))
            "table" -> renderTable(element)
            "hr" -> "---\n\n"
            "br" -> "\n"
            else -> renderContainer(element)
        }
    }

    private fun renderChildren(element: Element): String {
        val sb = StringBuilder()
        for (child in element.childNodes()) {
            sb.append(renderNode(child))
        }
        return sb.toString()
    }

    private fun renderList(listElement: Element, ordered: Boolean): String {
        val sb = StringBuilder()
        var index = 1
        for (child in listElement.children()) {
            if (child.tagName().lowercase() == "li") {
                val itemText = renderChildren(child).trim()
                if (itemText.isNotEmpty()) {
                    val prefix = if (ordered) "${index++}. " else "- "
                    sb.append(prefix).append(itemText).append("\n")
                }
            }
        }
        return if (sb.isNotEmpty()) sb.append("\n").toString() else ""
    }

    private fun renderTable(tableElement: Element): String {
        val rows = mutableListOf<List<String>>()
        val trElements = tableElement.select("tr")

        for (tr in trElements) {
            val cells = mutableListOf<String>()
            val thOrTd = tr.select("th, td")
            for (cell in thOrTd) {
                val cellText = renderChildren(cell).trim().replace("|", "\\|").replace("\n", "<br>")
                cells.add(cellText.ifBlank { " " })
            }
            if (cells.isNotEmpty()) {
                rows.add(cells)
            }
        }

        if (rows.isEmpty()) return ""

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

        return sb.append("\n").toString()
    }
}

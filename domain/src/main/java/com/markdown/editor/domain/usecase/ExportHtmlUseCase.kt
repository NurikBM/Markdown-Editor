package com.markdown.editor.domain.usecase

import com.markdown.editor.domain.error.DomainError
import com.markdown.editor.domain.error.asException
import com.markdown.editor.domain.model.MarkdownDocument
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

/**
 * Use case that converts a [MarkdownDocument] into clean, semantic HTML.
 * Supports standalone HTML5 documents with responsive modern typography and print CSS,
 * as well as raw body snippets for embedding.
 */
class ExportHtmlUseCase(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val parser: Parser = Parser.builder().build()
    private val htmlRenderer: HtmlRenderer = HtmlRenderer.builder().build()

    /**
     * Renders [document] into HTML.
     *
     * @param document The Markdown document to export.
     * @param standalone If true, wraps output in a full HTML5 document with CSS styling and print support.
     * @return [Result] containing the rendered HTML string, or a typed [DomainError.Export.HtmlExportFailed].
     */
    suspend fun execute(
        document: MarkdownDocument,
        standalone: Boolean = true
    ): Result<String> = withContext(dispatcher) {
        runCatching {
            try {
                val markdownContent = document.rawContent
                val documentNode = parser.parse(markdownContent)
                val bodyHtml = htmlRenderer.render(documentNode)

                if (standalone) {
                    buildStandaloneHtml(document.title, bodyHtml)
                } else {
                    bodyHtml
                }
            } catch (e: Exception) {
                throw DomainError.Export.HtmlExportFailed(
                    message = e.message ?: "Failed to render Markdown to HTML",
                    cause = e
                ).asException()
            }
        }
    }

    private fun buildStandaloneHtml(title: String, bodyHtml: String): String {
        val escapedTitle = title
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <title>$escapedTitle</title>
              <style>
                :root {
                  --text-color: #24292f;
                  --bg-color: #ffffff;
                  --border-color: #d0d7de;
                  --code-bg: #f6f8fa;
                  --quote-color: #57606a;
                  --link-color: #0969da;
                }
                @media (prefers-color-scheme: dark) {
                  :root {
                    --text-color: #e6edf3;
                    --bg-color: #0d1117;
                    --border-color: #30363d;
                    --code-bg: #161b22;
                    --quote-color: #8b949e;
                    --link-color: #2f81f7;
                  }
                }
                body {
                  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Helvetica, Arial, sans-serif;
                  line-height: 1.6;
                  color: var(--text-color);
                  background-color: var(--bg-color);
                  max-width: 820px;
                  margin: 0 auto;
                  padding: 2rem 1.5rem;
                }
                h1, h2, h3, h4, h5, h6 {
                  margin-top: 1.5rem;
                  margin-bottom: 0.75rem;
                  font-weight: 600;
                  line-height: 1.25;
                }
                h1 { font-size: 2rem; border-bottom: 1px solid var(--border-color); padding-bottom: 0.3rem; }
                h2 { font-size: 1.5rem; border-bottom: 1px solid var(--border-color); padding-bottom: 0.3rem; }
                h3 { font-size: 1.25rem; }
                p { margin-top: 0; margin-bottom: 1rem; }
                a { color: var(--link-color); text-decoration: none; }
                a:hover { text-decoration: underline; }
                code {
                  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
                  font-size: 85%;
                  background-color: var(--code-bg);
                  padding: 0.2em 0.4em;
                  border-radius: 6px;
                }
                pre {
                  background-color: var(--code-bg);
                  padding: 1rem;
                  border-radius: 6px;
                  overflow: auto;
                  font-size: 85%;
                  line-height: 1.45;
                  border: 1px solid var(--border-color);
                }
                pre code {
                  background-color: transparent;
                  padding: 0;
                  border-radius: 0;
                }
                blockquote {
                  margin: 0 0 1rem 0;
                  padding: 0 1rem;
                  color: var(--quote-color);
                  border-left: 0.25rem solid var(--border-color);
                }
                ul, ol {
                  margin-top: 0;
                  margin-bottom: 1rem;
                  padding-left: 2rem;
                }
                li { margin-bottom: 0.25rem; }
                hr {
                  height: 0.25rem;
                  padding: 0;
                  margin: 1.5rem 0;
                  background-color: var(--border-color);
                  border: 0;
                }
                @media print {
                  body {
                    max-width: 100%;
                    padding: 0;
                    color: #000;
                    background: #fff;
                  }
                  a { text-decoration: none; color: #000; }
                  pre { border: 1px solid #ccc; page-break-inside: avoid; }
                  blockquote { border-left: 3px solid #ccc; }
                }
              </style>
            </head>
            <body>
            $bodyHtml
            </body>
            </html>
        """.trimIndent()
    }
}


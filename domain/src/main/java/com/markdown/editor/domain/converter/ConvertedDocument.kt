package com.markdown.editor.domain.converter

/**
 * Result of converting an external document (e.g. Word, Excel, PDF, HTML) into Markdown.
 *
 * @property title The suggested title for the document.
 * @property markdownContent The converted markdown text.
 * @property isBestEffort True if conversion used layout/heuristic inference (e.g. PDF).
 * @property warningMessage User-facing advice if heuristic conversion was used.
 */
data class ConvertedDocument(
    val title: String,
    val markdownContent: String,
    val isBestEffort: Boolean = false,
    val warningMessage: String? = null
)


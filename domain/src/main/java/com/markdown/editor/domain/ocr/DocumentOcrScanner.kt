package com.markdown.editor.domain.ocr

import com.markdown.editor.domain.model.MarkdownBlock

/**
 * Result of optical character recognition containing extracted Markdown content and blocks.
 */
data class OcrResult(
    val title: String,
    val markdownContent: String,
    val blocks: List<MarkdownBlock> = emptyList()
)

/**
 * Pure domain contract for on-device document and image text recognition.
 * Completely decoupled from Android platform and ML SDK implementations.
 */
interface DocumentOcrScanner {
    suspend fun recognizeText(imageBytes: ByteArray, defaultTitle: String = "Scanned Note"): Result<OcrResult>
}

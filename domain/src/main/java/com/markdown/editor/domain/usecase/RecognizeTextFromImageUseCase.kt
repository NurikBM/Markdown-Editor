package com.markdown.editor.domain.usecase

import com.markdown.editor.domain.ocr.DocumentOcrScanner
import com.markdown.editor.domain.ocr.OcrResult

/**
 * Domain use case to extract text and structure from image bytes into Markdown blocks.
 */
class RecognizeTextFromImageUseCase(
    private val scanner: DocumentOcrScanner
) {
    suspend operator fun invoke(imageBytes: ByteArray, defaultTitle: String = "Scanned Note"): Result<OcrResult> {
        if (imageBytes.isEmpty()) {
            return Result.failure(IllegalArgumentException("Image data cannot be empty"))
        }
        return scanner.recognizeText(imageBytes, defaultTitle)
    }
}

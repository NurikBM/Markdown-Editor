package com.markdown.editor.domain.usecase

import com.markdown.editor.domain.model.BlockId
import com.markdown.editor.domain.model.BlockType
import com.markdown.editor.domain.model.MarkdownBlock
import com.markdown.editor.domain.ocr.DocumentOcrScanner
import com.markdown.editor.domain.ocr.OcrResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecognizeTextFromImageUseCaseTest {

    private val mockScanner = mockk<DocumentOcrScanner>()
    private val useCase = RecognizeTextFromImageUseCase(mockScanner)

    @Test
    fun `empty image bytes returns failure result`() = runTest {
        val result = useCase(ByteArray(0))
        assertTrue(result.isFailure)
        assertEquals("Image data cannot be empty", result.exceptionOrNull()?.message)
    }

    @Test
    fun `valid image bytes returns scanner success result`() = runTest {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val expectedBlocks = listOf(
            MarkdownBlock(id = BlockId("b1"), rawContent = "Scanned Title", type = BlockType.Heading(1)),
            MarkdownBlock(id = BlockId("b2"), rawContent = "Scanned paragraph content", type = BlockType.Paragraph)
        )
        val expectedResult = OcrResult(
            title = "Receipt Note",
            markdownContent = "# Scanned Title\n\nScanned paragraph content",
            blocks = expectedBlocks
        )

        coEvery { mockScanner.recognizeText(bytes, "Receipt Note") } returns Result.success(expectedResult)

        val result = useCase(bytes, "Receipt Note")
        assertTrue(result.isSuccess)
        val ocrResult = result.getOrNull()
        assertEquals("Receipt Note", ocrResult?.title)
        assertEquals(2, ocrResult?.blocks?.size)
        assertEquals("# Scanned Title\n\nScanned paragraph content", ocrResult?.markdownContent)
    }

    @Test
    fun `scanner failure propagates as failure result`() = runTest {
        val bytes = byteArrayOf(1, 2, 3)
        coEvery { mockScanner.recognizeText(bytes, any()) } returns Result.failure(IllegalStateException("OCR engine error"))

        val result = useCase(bytes)
        assertTrue(result.isFailure)
        assertEquals("OCR engine error", result.exceptionOrNull()?.message)
    }
}

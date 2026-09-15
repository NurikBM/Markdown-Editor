package com.markdown.editor.data.converter

import android.content.Context
import com.markdown.editor.domain.error.DomainException
import com.markdown.editor.domain.ocr.DocumentOcrScanner
import com.markdown.editor.domain.ocr.OcrResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PdfToMarkdownConverterTest {

    private val context: Context = mockk(relaxed = true)
    private val ocrScanner: DocumentOcrScanner = mockk(relaxed = true)
    private val converter = PdfToMarkdownConverter(context, ocrScanner)

    @Test
    fun `canConvert returns true for pdf extensions`() {
        assertTrue(converter.canConvert("pdf"))
        assertTrue(converter.canConvert("PDF"))
        assertTrue(converter.canConvert("document.pdf"))
        assertFalse(converter.canConvert("docx"))
        assertFalse(converter.canConvert("txt"))
    }

    @Test
    fun `convert with invalid PDF stream throws DomainException`() = runTest {
        val corruptedStream = "not a pdf".byteInputStream()
        assertThrows<DomainException> {
            converter.convert("corrupted.pdf", corruptedStream)
        }
    }
}

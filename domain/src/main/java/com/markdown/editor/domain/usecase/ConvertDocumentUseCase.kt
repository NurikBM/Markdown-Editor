package com.markdown.editor.domain.usecase

import com.markdown.editor.domain.converter.ConvertedDocument
import com.markdown.editor.domain.converter.DocumentConverter
import com.markdown.editor.domain.error.DomainError
import com.markdown.editor.domain.error.asException
import java.io.InputStream

/**
 * Use case orchestrating conversion of external files into structured Markdown.
 */
class ConvertDocumentUseCase(
    private val documentConverter: DocumentConverter
) {

    fun canConvert(fileName: String): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return documentConverter.canConvert(extension)
    }

    suspend operator fun invoke(fileName: String, inputStream: InputStream): ConvertedDocument {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (!documentConverter.canConvert(extension)) {
            throw DomainError.Conversion.UnsupportedFormat(extension).asException()
        }
        return documentConverter.convert(fileName, inputStream)
    }
}


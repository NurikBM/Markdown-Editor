package com.markdown.editor.domain.converter

import java.io.InputStream

/**
 * Interface defining conversion contract from external non-markdown file formats into Markdown.
 */
interface DocumentConverter {

    /**
     * Checks if the converter supports the given file extension (without leading dot).
     */
    fun canConvert(extension: String): Boolean

    /**
     * Converts the input stream into a [ConvertedDocument].
     */
    suspend fun convert(fileName: String, inputStream: InputStream): ConvertedDocument
}


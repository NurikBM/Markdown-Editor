package com.markdown.editor.domain.usecase

import com.markdown.editor.domain.model.MarkdownDocument
import com.markdown.editor.domain.parser.MarkdownBlockParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Domain use case to parse an entire Markdown document asynchronously on [dispatcher].
 * Defaults to limited parallelism (2) per concurrency policy.
 */
class ParseDocumentUseCase(
    private val parser: MarkdownBlockParser,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(2)
) {
    suspend operator fun invoke(
        rawMarkdown: String,
        documentId: String = UUID.randomUUID().toString(),
        title: String = "Untitled"
    ): MarkdownDocument = withContext(dispatcher) {
        parser.parseDocument(rawMarkdown, documentId, title)
    }
}


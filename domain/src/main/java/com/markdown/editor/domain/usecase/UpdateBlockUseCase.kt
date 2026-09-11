package com.markdown.editor.domain.usecase

import com.markdown.editor.domain.model.BlockId
import com.markdown.editor.domain.model.MarkdownDocument
import com.markdown.editor.domain.parser.MarkdownBlockParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Domain use case to incrementally update a single Markdown block asynchronously on [dispatcher].
 * Defaults to limited parallelism (2) per concurrency policy.
 */
class UpdateBlockUseCase(
    private val parser: MarkdownBlockParser,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(2)
) {
    suspend operator fun invoke(
        document: MarkdownDocument,
        blockId: BlockId,
        newRawContent: String
    ): MarkdownDocument = withContext(dispatcher) {
        parser.updateBlock(document, blockId, newRawContent)
    }
}


package com.markdown.editor.domain.usecase

import com.markdown.editor.domain.repository.MarkdownRepository

/**
 * Use case toggling or setting the biometric lock protection state on a document.
 */
class ToggleDocumentLockUseCase(
    private val repository: MarkdownRepository
) {
    suspend operator fun invoke(documentId: String, isLocked: Boolean): Result<Unit> {
        return repository.updateLockStatus(documentId, isLocked)
    }
}


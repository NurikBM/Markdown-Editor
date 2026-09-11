package com.markdown.editor.data.autosave

import com.markdown.editor.core.dispatcher.DispatcherProvider
import com.markdown.editor.data.diff.DiffEngine
import com.markdown.editor.domain.model.BlockId
import com.markdown.editor.domain.model.DocumentMetadata
import com.markdown.editor.domain.model.DocumentSnapshot
import com.markdown.editor.domain.model.MarkdownDocument
import com.markdown.editor.domain.repository.MarkdownRepository
import com.markdown.editor.domain.repository.SnapshotRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Encapsulates a pending auto-save mutation request.
 */
data class AutoSaveRequest(
    val document: MarkdownDocument,
    val metadata: DocumentMetadata? = null,
    val modifiedBlockId: BlockId? = null,
    val oldBlockContent: String? = null
)

/**
 * Coordinator responsible for debounced auto-saving of documents and diff snapshots,
 * offloading I/O persistence to [Dispatchers.IO] and diff computation to [diffAndParsing].
 */
class AutoSaveCoordinator(
    private val markdownRepository: MarkdownRepository,
    private val snapshotRepository: SnapshotRepository,
    private val diffEngine: DiffEngine,
    private val dispatcherProvider: DispatcherProvider,
    private val coroutineScope: CoroutineScope,
    private val debounceMillis: Long = DEFAULT_AUTOSAVE_DEBOUNCE_MS
) {

    companion object {
        const val DEFAULT_AUTOSAVE_DEBOUNCE_MS = 500L
    }

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private var debounceJob: Job? = null

    /**
     * Schedules a debounced auto-save operation.
     * Re-scheduling cancels any pending debounced save.
     */
    fun scheduleSave(request: AutoSaveRequest) {
        debounceJob?.cancel()
        debounceJob = coroutineScope.launch(dispatcherProvider.io) {
            delay(debounceMillis)
            executeSave(request)
        }
    }

    /**
     * Immediately executes a save operation, cancelling any pending debounced save.
     * Useful on lifecycle pause or explicit user save.
     */
    suspend fun saveImmediately(request: AutoSaveRequest): Result<Unit> = withContext(dispatcherProvider.io) {
        debounceJob?.cancel()
        executeSave(request)
    }

    /**
     * Cancels any pending scheduled auto-save operation.
     */
    fun cancelPendingSave() {
        debounceJob?.cancel()
    }

    private suspend fun executeSave(request: AutoSaveRequest): Result<Unit> {
        _isSaving.value = true
        return try {
            if (request.modifiedBlockId != null && request.oldBlockContent != null) {
                val newBlock = request.document.blocks.find { it.id == request.modifiedBlockId }
                val newContent = newBlock?.rawContent ?: ""

                val diffResult = withContext(dispatcherProvider.diffAndParsing) {
                    diffEngine.computeDiff(request.oldBlockContent, newContent)
                }

                if (diffResult.hasChanges) {
                    val snapshot = DocumentSnapshot(
                        documentId = request.document.id,
                        blockId = request.modifiedBlockId,
                        forwardDiff = diffResult.forwardDiff,
                        reverseDiff = diffResult.reverseDiff
                    )
                    snapshotRepository.recordSnapshot(snapshot)
                }
            }

            markdownRepository.saveDocument(request.document, request.metadata)
        } finally {
            _isSaving.value = false
        }
    }
}

package com.markdown.editor.domain.error

import com.markdown.editor.domain.model.BlockId

/**
 * Sealed hierarchy defining typed domain errors.
 * Raw platform exceptions must be mapped into these domain errors at architectural boundaries.
 */
sealed interface DomainError {

    sealed interface Storage : DomainError {
        data class PermissionRevoked(val uri: String) : Storage
        data class FileNotFound(val uri: String) : Storage
        data class DiskExhausted(val requiredBytes: Long) : Storage
        data class IoFailure(val message: String, val cause: Throwable? = null) : Storage
    }

    sealed interface Parsing : DomainError {
        data class InvalidSyntax(val message: String, val line: Int? = null) : Parsing
        data class BlockNotFound(val blockId: BlockId) : Parsing
    }

    sealed interface PatchConflict : DomainError {
        data class BlockMismatch(val blockId: String, val expectedHash: String) : PatchConflict
        data class SnapshotNotFound(val snapshotId: String) : PatchConflict
    }

    sealed interface Export : DomainError {
        data class HtmlExportFailed(val message: String, val cause: Throwable? = null) : Export
        data class PdfExportFailed(val message: String, val cause: Throwable? = null) : Export
        data class FileWriteFailed(val destinationPath: String, val message: String) : Export
    }

    sealed interface Conversion : DomainError {
        data class CorruptedFile(val fileName: String, val reason: String) : Conversion
        data class UnsupportedFormat(val extension: String) : Conversion
        data class PasswordProtected(val fileName: String) : Conversion
        data class EmptyDocument(val fileName: String) : Conversion
    }
}

class DomainException(val error: DomainError) : RuntimeException(error.toString())

fun DomainError.asException(): DomainException = DomainException(this)


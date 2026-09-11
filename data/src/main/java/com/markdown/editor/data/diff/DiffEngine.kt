package com.markdown.editor.data.diff

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import com.github.difflib.patch.PatchFailedException

/**
 * Result of computing bidirectional Myers diff between two text versions.
 */
data class DiffResult(
    val forwardDiff: String,
    val reverseDiff: String,
    val hasChanges: Boolean
)

/**
 * Production Myers diff engine utilizing java-diff-utils.
 * Computes forward and backward diffs and applies patches deterministically.
 */
class DiffEngine {

    /**
     * Computes bidirectional patch representations between [oldText] and [newText].
     */
    fun computeDiff(oldText: String, newText: String): DiffResult {
        if (oldText == newText) {
            return DiffResult(forwardDiff = "", reverseDiff = "", hasChanges = false)
        }

        val oldLines = oldText.splitLines()
        val newLines = newText.splitLines()

        val forwardPatch = DiffUtils.diff(oldLines, newLines)
        val reversePatch = DiffUtils.diff(newLines, oldLines)

        val forwardDiffLines = UnifiedDiffUtils.generateUnifiedDiff(
            "original", "revised", oldLines, forwardPatch, 2
        )
        val reverseDiffLines = UnifiedDiffUtils.generateUnifiedDiff(
            "revised", "original", newLines, reversePatch, 2
        )

        return DiffResult(
            forwardDiff = forwardDiffLines.joinToString("\n"),
            reverseDiff = reverseDiffLines.joinToString("\n"),
            hasChanges = true
        )
    }

    /**
     * Applies a unified diff patch to [sourceText].
     */
    fun applyPatch(sourceText: String, unifiedDiff: String): Result<String> {
        if (unifiedDiff.isBlank()) return Result.success(sourceText)

        return try {
            val sourceLines = sourceText.splitLines()
            val diffLines = unifiedDiff.splitLines()
            val patch = UnifiedDiffUtils.parseUnifiedDiff(diffLines)
            val patchedLines = DiffUtils.patch(sourceLines, patch)
            Result.success(patchedLines.joinToString("\n"))
        } catch (e: PatchFailedException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun String.splitLines(): List<String> {
        return if (this.isEmpty()) emptyList() else this.lines()
    }
}


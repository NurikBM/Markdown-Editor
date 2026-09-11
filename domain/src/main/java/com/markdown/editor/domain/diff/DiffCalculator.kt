package com.markdown.editor.domain.diff

/**
 * Result of computing bidirectional diff between two text versions.
 */
data class DiffResult(
    val forwardDiff: String,
    val reverseDiff: String,
    val hasChanges: Boolean
)

/**
 * Abstraction for computing and applying unified diffs.
 */
interface DiffCalculator {

    /**
     * Computes bidirectional forward and reverse unified diffs between [oldText] and [newText].
     */
    fun computeDiff(oldText: String, newText: String): DiffResult

    /**
     * Applies [unifiedDiff] patch to [sourceText].
     */
    fun applyPatch(sourceText: String, unifiedDiff: String): Result<String>
}


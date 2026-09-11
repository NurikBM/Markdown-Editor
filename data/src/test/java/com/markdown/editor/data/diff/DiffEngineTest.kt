package com.markdown.editor.data.diff

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DiffEngineTest {

    private lateinit var diffEngine: DiffEngine

    @BeforeEach
    fun setUp() {
        diffEngine = DiffEngine()
    }

    @Test
    fun `computeDiff with identical content reports no changes`() {
        val text = "Line 1\nLine 2"
        val result = diffEngine.computeDiff(text, text)
        assertFalse(result.hasChanges)
        assertEquals("", result.forwardDiff)
        assertEquals("", result.reverseDiff)
    }

    @Test
    fun `computeDiff and apply forward patch transforms text correctly`() {
        val original = "Line 1\nLine 2\nLine 3"
        val revised = "Line 1\nLine 2 Modified\nLine 3\nLine 4"

        val diffResult = diffEngine.computeDiff(original, revised)
        assertTrue(diffResult.hasChanges)

        val patchResult = diffEngine.applyPatch(original, diffResult.forwardDiff)
        assertTrue(patchResult.isSuccess)
        assertEquals(revised, patchResult.getOrThrow())
    }

    @Test
    fun `rollback with reverse diff restores original text`() {
        val original = "Line A\nLine B"
        val revised = "Line A\nLine B changed\nLine C added"

        val diffResult = diffEngine.computeDiff(original, revised)

        val forwardResult = diffEngine.applyPatch(original, diffResult.forwardDiff)
        assertEquals(revised, forwardResult.getOrThrow())

        val rollbackResult = diffEngine.applyPatch(revised, diffResult.reverseDiff)
        assertTrue(rollbackResult.isSuccess)
        assertEquals(original, rollbackResult.getOrThrow())
    }
}


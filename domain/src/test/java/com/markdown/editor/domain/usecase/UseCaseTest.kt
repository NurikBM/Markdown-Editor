package com.markdown.editor.domain.usecase

import app.cash.turbine.test
import com.markdown.editor.domain.model.BlockType
import com.markdown.editor.domain.parser.CommonmarkBlockParser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UseCaseTest {

    private val parser = CommonmarkBlockParser()
    private val testDispatcher = StandardTestDispatcher()
    private val parseUseCase = ParseDocumentUseCase(parser, testDispatcher)
    private val updateUseCase = UpdateBlockUseCase(parser, testDispatcher)

    @Test
    fun `ParseDocumentUseCase parses markdown asynchronously on dispatcher`() = runTest(testDispatcher) {
        val doc = parseUseCase("# Hello World")
        assertEquals(1, doc.blocks.size)
        assertEquals(BlockType.Heading(1), doc.blocks[0].type)
        assertEquals("Hello World", doc.blocks[0].plainText)
    }

    @Test
    fun `UpdateBlockUseCase updates target block asynchronously on dispatcher`() = runTest(testDispatcher) {
        val doc = parseUseCase("Original block")
        val blockId = doc.blocks[0].id

        val updatedDoc = updateUseCase(doc, blockId, "## Updated Header")
        assertEquals(1, updatedDoc.blocks.size)
        assertEquals(blockId, updatedDoc.blocks[0].id)
        assertEquals(BlockType.Heading(2), updatedDoc.blocks[0].type)
        assertEquals("Updated Header", updatedDoc.blocks[0].plainText)
    }

    @Test
    fun `debounceTyping extension debounces rapid typing emissions with Long and Duration`() = runTest {
        val flowLong = flowOf("a", "ab", "abc").debounceTyping(TYPING_DEBOUNCE_MS)
        flowLong.test {
            assertEquals("abc", awaitItem())
            awaitComplete()
        }

        val flowDuration = flowOf("1", "12", "123").debounceTyping(TYPING_DEBOUNCE_DURATION)
        flowDuration.test {
            assertEquals("123", awaitItem())
            awaitComplete()
        }
    }
}


package com.markdown.editor.domain.usecase

import com.markdown.editor.domain.model.DocumentMetadata
import com.markdown.editor.domain.model.MarkdownDocument
import com.markdown.editor.domain.repository.MarkdownRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ToggleDocumentLockUseCaseTest {

    private val repository: MarkdownRepository = mockk()
    private lateinit var useCase: ToggleDocumentLockUseCase

    @BeforeEach
    fun setUp() {
        useCase = ToggleDocumentLockUseCase(repository)
    }

    @Test
    fun `invoke delegates lock status update to repository`() = runTest {
        coEvery { repository.updateLockStatus("doc-1", true) } returns Result.success(Unit)

        val result = useCase("doc-1", true)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { repository.updateLockStatus("doc-1", true) }
    }

    @Test
    fun `invoke delegates unlock status update to repository`() = runTest {
        coEvery { repository.updateLockStatus("doc-1", false) } returns Result.success(Unit)

        val result = useCase("doc-1", false)

        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { repository.updateLockStatus("doc-1", false) }
    }
}


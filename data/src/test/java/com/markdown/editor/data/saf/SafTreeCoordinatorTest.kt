package com.markdown.editor.data.saf

import android.content.ContentResolver
import android.content.UriPermission
import android.net.Uri
import com.markdown.editor.core.logger.Logger
import com.markdown.editor.data.local.dao.DocumentDao
import com.markdown.editor.data.local.entity.DocumentMetadataEntity
import com.markdown.editor.domain.error.DomainError
import com.markdown.editor.domain.error.DomainException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SafTreeCoordinatorTest {

    private val contentResolver: ContentResolver = mockk(relaxed = true)
    private val documentDao: DocumentDao = mockk(relaxed = true)
    private val logger: Logger = mockk(relaxed = true)

    private lateinit var coordinator: SafTreeCoordinator

    @BeforeEach
    fun setUp() {
        mockkStatic(Uri::class)
        coordinator = SafTreeCoordinator(
            contentResolver = contentResolver,
            documentDao = documentDao,
            logger = logger
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Uri::class)
    }

    @Test
    fun `takePersistableGrant acquires permission and succeeds`() = runTest {
        val uri: Uri = mockk(relaxed = true)
        every { uri.toString() } returns "content://test/tree"
        every { contentResolver.persistedUriPermissions } returns emptyList()

        val result = coordinator.takePersistableGrant(uri, "doc-1")
        assertTrue(result.isSuccess)

        verify {
            contentResolver.takePersistableUriPermission(uri, SafTreeCoordinator.GRANT_FLAGS)
        }
    }

    @Test
    fun `takePersistableGrant marks document unlinked and fails on SecurityException`() = runTest {
        val uri: Uri = mockk(relaxed = true)
        every { uri.toString() } returns "content://test/tree"
        every { contentResolver.persistedUriPermissions } returns emptyList()
        every {
            contentResolver.takePersistableUriPermission(uri, SafTreeCoordinator.GRANT_FLAGS)
        } throws SecurityException("Permission revoked")

        val result = coordinator.takePersistableGrant(uri, "doc-1")
        assertTrue(result.isFailure)

        val ex = result.exceptionOrNull()
        assertTrue(ex is DomainException)
        val domainError = (ex as DomainException).error
        assertTrue(domainError is DomainError.Storage.PermissionRevoked)
        assertEquals("content://test/tree", (domainError as DomainError.Storage.PermissionRevoked).uri)

        coVerify {
            documentDao.markAsUnlinked("doc-1")
        }
    }

    @Test
    fun `enforceLruEvictionIfNeeded evicts LRU document when at or above threshold`() = runTest {
        val mockPermissions = List(120) { mockk<UriPermission>() }
        every { contentResolver.persistedUriPermissions } returns mockPermissions

        val lruEntity = DocumentMetadataEntity(
            documentId = "lru-doc",
            title = "Old Doc",
            treeUri = "content://old/tree",
            lastAccessedTimestamp = 100L
        )
        coEvery { documentDao.getLeastRecentlyUsedTreeGrant() } returns lruEntity

        val parsedUri: Uri = mockk(relaxed = true)
        every { Uri.parse("content://old/tree") } returns parsedUri

        coordinator.enforceLruEvictionIfNeeded()

        verify {
            contentResolver.releasePersistableUriPermission(parsedUri, SafTreeCoordinator.GRANT_FLAGS)
        }
        coVerify {
            documentDao.markAsUnlinked("lru-doc")
        }
    }

    @Test
    fun `enforceLruEvictionIfNeeded does not evict when below threshold`() = runTest {
        val mockPermissions = List(119) { mockk<UriPermission>() }
        every { contentResolver.persistedUriPermissions } returns mockPermissions

        coordinator.enforceLruEvictionIfNeeded()

        coVerify(exactly = 0) {
            documentDao.getLeastRecentlyUsedTreeGrant()
            documentDao.markAsUnlinked(any())
        }
        verify(exactly = 0) {
            contentResolver.releasePersistableUriPermission(any(), any())
        }
    }

    @Test
    fun `releaseGrant releases permission and marks unlinked`() = runTest {
        val uri: Uri = mockk(relaxed = true)
        val result = coordinator.releaseGrant(uri, "doc-to-release")
        assertTrue(result.isSuccess)

        verify {
            contentResolver.releasePersistableUriPermission(uri, SafTreeCoordinator.GRANT_FLAGS)
        }
        coVerify {
            documentDao.markAsUnlinked("doc-to-release")
        }
    }
}


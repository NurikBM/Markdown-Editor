package com.markdown.editor.data.repository

import com.markdown.editor.core.dispatcher.DispatcherProvider
import com.markdown.editor.data.local.dao.SnapshotDao
import com.markdown.editor.data.local.entity.SnapshotEntity
import com.markdown.editor.domain.model.BlockId
import com.markdown.editor.domain.model.DocumentSnapshot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoomSnapshotRepositoryTest {

    private val snapshotDao: SnapshotDao = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    private val dispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
        override val diffAndParsing: CoroutineDispatcher = testDispatcher
    }

    private lateinit var repository: RoomSnapshotRepository

    @BeforeEach
    fun setUp() {
        repository = RoomSnapshotRepository(
            snapshotDao = snapshotDao,
            dispatcherProvider = dispatcherProvider
        )
    }

    @Test
    fun `recordSnapshot inserts snapshot entity`() = runTest(testDispatcher) {
        val snapshot = DocumentSnapshot(
            versionId = "v-1",
            documentId = "doc-1",
            blockId = BlockId("b-1"),
            timestamp = 1000L,
            forwardDiff = "+new",
            reverseDiff = "-old"
        )

        val result = repository.recordSnapshot(snapshot)
        assertTrue(result.isSuccess)

        coVerify {
            snapshotDao.insertSnapshot(
                match {
                    it.versionId == "v-1" &&
                        it.documentId == "doc-1" &&
                        it.blockId == "b-1" &&
                        it.forwardDiff == "+new" &&
                        it.reverseDiff == "-old"
                }
            )
        }
    }

    @Test
    fun `getLatestSnapshot returns mapped snapshot when found`() = runTest(testDispatcher) {
        val entity = SnapshotEntity(
            versionId = "v-latest",
            documentId = "doc-1",
            blockId = "b-1",
            timestamp = 2000L,
            forwardDiff = "+line",
            reverseDiff = "-line"
        )
        coEvery { snapshotDao.getLatestSnapshot("doc-1", "b-1") } returns entity

        val snapshot = repository.getLatestSnapshot("doc-1", BlockId("b-1"))
        assertNotNull(snapshot)
        assertEquals("v-latest", snapshot?.versionId)
        assertEquals(BlockId("b-1"), snapshot?.blockId)
        assertEquals("+line", snapshot?.forwardDiff)
    }

    @Test
    fun `getLatestSnapshot returns null when no snapshot exists`() = runTest(testDispatcher) {
        coEvery { snapshotDao.getLatestSnapshot("doc-1", "b-2") } returns null

        val snapshot = repository.getLatestSnapshot("doc-1", BlockId("b-2"))
        assertNull(snapshot)
    }

    @Test
    fun `getSnapshotsForDocument returns all snapshots mapped`() = runTest(testDispatcher) {
        val entities = listOf(
            SnapshotEntity("v-1", "doc-1", "b-1", 100L, "f1", "r1"),
            SnapshotEntity("v-2", "doc-1", "b-2", 200L, "f2", "r2")
        )
        coEvery { snapshotDao.getSnapshotsForDocument("doc-1") } returns entities

        val list = repository.getSnapshotsForDocument("doc-1")
        assertEquals(2, list.size)
        assertEquals("v-1", list[0].versionId)
        assertEquals("v-2", list[1].versionId)
    }

    @Test
    fun `pruneSnapshots calls pruneOldSnapshots on dao`() = runTest(testDispatcher) {
        val result = repository.pruneSnapshots("doc-1", 50)
        assertTrue(result.isSuccess)
        coVerify { snapshotDao.pruneOldSnapshots("doc-1", 50) }
    }
}


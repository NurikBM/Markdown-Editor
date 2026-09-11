package com.markdown.editor.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.markdown.editor.data.local.entity.BlockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockDao {
    @Query("SELECT * FROM blocks WHERE documentId = :documentId ORDER BY orderIndex ASC")
    fun observeBlocksForDocument(documentId: String): Flow<List<BlockEntity>>

    @Query("SELECT * FROM blocks WHERE documentId = :documentId ORDER BY orderIndex ASC")
    suspend fun getBlocksForDocument(documentId: String): List<BlockEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlocks(blocks: List<BlockEntity>)

    @Query("DELETE FROM blocks WHERE documentId = :documentId")
    suspend fun deleteBlocksForDocument(documentId: String)

    @Transaction
    suspend fun replaceBlocks(documentId: String, blocks: List<BlockEntity>) {
        deleteBlocksForDocument(documentId)
        insertBlocks(blocks)
    }
}


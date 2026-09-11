package com.markdown.editor.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.markdown.editor.data.local.entity.DocumentMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY lastAccessedTimestamp DESC")
    fun observeAll(): Flow<List<DocumentMetadataEntity>>

    @Query("SELECT * FROM documents WHERE documentId = :id")
    suspend fun getById(id: String): DocumentMetadataEntity?

    @Query("SELECT * FROM documents WHERE documentId = :id")
    fun observeById(id: String): Flow<DocumentMetadataEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(document: DocumentMetadataEntity)

    @Query("DELETE FROM documents WHERE documentId = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE documents SET lastAccessedTimestamp = :timestamp WHERE documentId = :id")
    suspend fun updateLastAccessed(id: String, timestamp: Long)

    @Query("SELECT COUNT(*) FROM documents WHERE treeUri IS NOT NULL AND isUnlinked = 0")
    suspend fun countActiveTreeGrants(): Int

    @Query("SELECT * FROM documents WHERE treeUri IS NOT NULL AND isUnlinked = 0 ORDER BY lastAccessedTimestamp ASC LIMIT 1")
    suspend fun getLeastRecentlyUsedTreeGrant(): DocumentMetadataEntity?

    @Query("UPDATE documents SET isUnlinked = 1 WHERE documentId = :id")
    suspend fun markAsUnlinked(id: String)
}


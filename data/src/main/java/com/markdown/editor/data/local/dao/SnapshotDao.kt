package com.markdown.editor.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.markdown.editor.data.local.entity.SnapshotEntity

@Dao
interface SnapshotDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: SnapshotEntity)

    @Query("SELECT * FROM snapshots WHERE documentId = :documentId ORDER BY timestamp DESC")
    suspend fun getSnapshotsForDocument(documentId: String): List<SnapshotEntity>

    @Query("SELECT * FROM snapshots WHERE documentId = :documentId AND blockId = :blockId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestSnapshot(documentId: String, blockId: String): SnapshotEntity?

    @Query("DELETE FROM snapshots WHERE documentId = :documentId AND versionId NOT IN (SELECT versionId FROM snapshots WHERE documentId = :documentId ORDER BY timestamp DESC LIMIT :maxKeep)")
    suspend fun pruneOldSnapshots(documentId: String, maxKeep: Int)
}


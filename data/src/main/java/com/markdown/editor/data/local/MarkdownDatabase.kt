package com.markdown.editor.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.markdown.editor.data.local.dao.BlockDao
import com.markdown.editor.data.local.dao.DocumentDao
import com.markdown.editor.data.local.dao.SnapshotDao
import com.markdown.editor.data.local.entity.BlockEntity
import com.markdown.editor.data.local.entity.DocumentMetadataEntity
import com.markdown.editor.data.local.entity.SnapshotEntity

@Database(
    entities = [
        DocumentMetadataEntity::class,
        BlockEntity::class,
        SnapshotEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class MarkdownDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun blockDao(): BlockDao
    abstract fun snapshotDao(): SnapshotDao
}


package com.markdown.editor.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 2,
    exportSchema = false
)
abstract class MarkdownDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun blockDao(): BlockDao
    abstract fun snapshotDao(): SnapshotDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documents ADD COLUMN isLocked INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}


package com.markdown.editor.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "snapshots",
    foreignKeys = [
        ForeignKey(
            entity = DocumentMetadataEntity::class,
            parentColumns = ["documentId"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("documentId"),
        Index("documentId", "timestamp")
    ]
)
data class SnapshotEntity(
    @PrimaryKey val versionId: String,
    val documentId: String,
    val blockId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val forwardDiff: String,
    val reverseDiff: String
)


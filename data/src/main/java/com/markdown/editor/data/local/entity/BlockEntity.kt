package com.markdown.editor.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "blocks",
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
        Index("documentId", "orderIndex")
    ]
)
data class BlockEntity(
    @PrimaryKey val blockId: String,
    val documentId: String,
    val orderIndex: Int,
    val rawContent: String,
    val blockType: String
)


package com.markdown.editor.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentMetadataEntity(
    @PrimaryKey val documentId: String,
    val title: String,
    val treeUri: String? = null,
    val documentUri: String? = null,
    val lastAccessedTimestamp: Long = System.currentTimeMillis(),
    val lastModifiedTimestamp: Long = System.currentTimeMillis(),
    val isUnlinked: Boolean = false,
    val isLocked: Boolean = false
)


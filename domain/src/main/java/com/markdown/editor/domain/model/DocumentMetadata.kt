package com.markdown.editor.domain.model

/**
 * Metadata representing a stored Markdown document and its storage associations.
 */
data class DocumentMetadata(
    val id: String,
    val title: String,
    val treeUri: String? = null,
    val documentUri: String? = null,
    val lastAccessedTimestamp: Long = System.currentTimeMillis(),
    val lastModifiedTimestamp: Long = System.currentTimeMillis(),
    val isUnlinked: Boolean = false
)


package com.markdown.editor.domain.model

import java.util.UUID

/**
 * Type-safe, immutable unique identifier for a Markdown block.
 */
data class BlockId(val value: String = UUID.randomUUID().toString())


package com.markdown.editor.domain.usecase

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce

/**
 * Standard typing debounce duration per architectural specification (350 ms).
 */
const val TYPING_DEBOUNCE_MS: Long = 350L
val TYPING_DEBOUNCE_DURATION: Duration = TYPING_DEBOUNCE_MS.milliseconds

/**
 * Extension applying the standard typing debounce to an input flow using type-safe [Duration].
 */
@OptIn(FlowPreview::class)
fun <T> Flow<T>.debounceTyping(timeout: Duration = TYPING_DEBOUNCE_DURATION): Flow<T> {
    return debounce(timeout)
}

/**
 * Extension applying typing debounce using milliseconds for raw numeric values.
 */
@OptIn(FlowPreview::class)
fun <T> Flow<T>.debounceTyping(timeoutMillis: Long): Flow<T> {
    return debounce(timeoutMillis.milliseconds)
}


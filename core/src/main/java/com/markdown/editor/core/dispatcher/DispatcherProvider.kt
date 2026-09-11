package com.markdown.editor.core.dispatcher

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Interface abstracting coroutine dispatchers for deterministic execution and test isolation.
 */
interface DispatcherProvider {
    val main: CoroutineDispatcher
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val unconfined: CoroutineDispatcher
    val diffAndParsing: CoroutineDispatcher
}


package com.markdown.editor.core.dispatcher

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Default implementation of [DispatcherProvider] backed by platform dispatchers.
 * Note: [diffAndParsing] is constrained to 2 concurrent threads to prevent UI stutters during AST parsing and Myers diffs.
 */
class DefaultDispatcherProvider : DispatcherProvider {
    override val main: CoroutineDispatcher get() = Dispatchers.Main
    override val io: CoroutineDispatcher get() = Dispatchers.IO
    override val default: CoroutineDispatcher get() = Dispatchers.Default
    override val unconfined: CoroutineDispatcher get() = Dispatchers.Unconfined
    override val diffAndParsing: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(2)
}


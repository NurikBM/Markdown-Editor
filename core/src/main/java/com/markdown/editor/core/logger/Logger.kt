package com.markdown.editor.core.logger

/**
 * Unified logging interface to prevent direct dependencies on android.util.Log or println
 * in core, domain, and data layers.
 */
interface Logger {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String, throwable: Throwable? = null)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}


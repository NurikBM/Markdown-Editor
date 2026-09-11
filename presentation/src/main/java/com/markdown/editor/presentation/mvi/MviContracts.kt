package com.markdown.editor.presentation.mvi

import androidx.compose.runtime.Immutable

/**
 * Marker interface for all MVI UI states.
 * Implementations must be immutable data classes.
 */
@Immutable
interface UiState

/**
 * Marker interface for all MVI UI intents (actions/events originated from user or lifecycle).
 */
interface UiIntent

/**
 * Marker interface for all one-shot side effects (e.g. navigation, snackbars, toast messages).
 */
interface UiEffect


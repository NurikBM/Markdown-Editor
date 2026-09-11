package com.markdown.editor.presentation.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Foundation MVI ViewModel container using native Kotlin Coroutines and StateFlow.
 * Enforces Unidirectional Data Flow (UDF): UiIntent -> Reducer -> UiState / UiEffect.
 *
 * @param State The immutable UI state type extending [UiState].
 * @param Intent The user action / intent type extending [UiIntent].
 * @param Effect The transient single-event effect type extending [UiEffect].
 * @param initialState The initial state instance emitted to observers.
 */
abstract class MviViewModel<State : UiState, Intent : UiIntent, Effect : UiEffect>(
    initialState: State
) : ViewModel() {

    private val _uiState: MutableStateFlow<State> = MutableStateFlow(initialState)
    val uiState: StateFlow<State> = _uiState.asStateFlow()

    private val _uiEffect: Channel<Effect> = Channel(capacity = Channel.BUFFERED)
    val uiEffect: Flow<Effect> = _uiEffect.receiveAsFlow()

    /**
     * Process incoming [intent] dispatched from UI or lifecycle events.
     */
    abstract fun processIntent(intent: Intent)

    /**
     * Atomically mutate current [State] via [reducer] lambda.
     */
    protected fun updateState(reducer: State.() -> State) {
        _uiState.update { current -> current.reducer() }
    }

    /**
     * Asynchronously emit a one-shot [effect] via buffered channel.
     */
    protected fun sendEffect(effect: Effect) {
        viewModelScope.launch {
            _uiEffect.send(effect)
        }
    }
}


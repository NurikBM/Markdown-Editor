package com.markdown.editor.presentation.mvi

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private data class TestState(val count: Int = 0, val text: String = "") : UiState

private sealed interface TestIntent : UiIntent {
    data object Increment : TestIntent
    data class UpdateText(val text: String) : TestIntent
    data class Notify(val message: String) : TestIntent
}

private sealed interface TestEffect : UiEffect {
    data class ShowToast(val message: String) : TestEffect
}

private class TestViewModel : MviViewModel<TestState, TestIntent, TestEffect>(TestState()) {
    override fun processIntent(intent: TestIntent) {
        when (intent) {
            is TestIntent.Increment -> updateState { copy(count = count + 1) }
            is TestIntent.UpdateText -> updateState { copy(text = intent.text) }
            is TestIntent.Notify -> sendEffect(TestEffect.ShowToast(intent.message))
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MviViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is emitted correctly`() = runTest {
        val viewModel = TestViewModel()

        viewModel.uiState.test {
            assertEquals(TestState(count = 0, text = ""), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `processing state intent updates uiState sequentially`() = runTest {
        val viewModel = TestViewModel()

        viewModel.uiState.test {
            assertEquals(TestState(count = 0, text = ""), awaitItem())

            viewModel.processIntent(TestIntent.Increment)
            assertEquals(TestState(count = 1, text = ""), awaitItem())

            viewModel.processIntent(TestIntent.UpdateText("hello"))
            assertEquals(TestState(count = 1, text = "hello"), awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `processing effect intent emits effect to uiEffect flow`() = runTest {
        val viewModel = TestViewModel()

        viewModel.uiEffect.test {
            viewModel.processIntent(TestIntent.Notify("Action completed"))
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem()
            assertEquals(TestEffect.ShowToast("Action completed"), effect)

            cancelAndIgnoreRemainingEvents()
        }
    }
}


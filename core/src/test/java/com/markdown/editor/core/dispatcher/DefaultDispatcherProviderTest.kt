package com.markdown.editor.core.dispatcher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultDispatcherProviderTest {

    private val testDispatcher = StandardTestDispatcher()
    private val provider: DispatcherProvider = DefaultDispatcherProvider()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `main dispatcher returns Main dispatcher`() {
        assertEquals(Dispatchers.Main, provider.main)
    }

    @Test
    fun `io dispatcher returns Dispatchers IO`() {
        assertEquals(Dispatchers.IO, provider.io)
    }

    @Test
    fun `default dispatcher returns Dispatchers Default`() {
        assertEquals(Dispatchers.Default, provider.default)
    }

    @Test
    fun `unconfined dispatcher returns Dispatchers Unconfined`() {
        assertEquals(Dispatchers.Unconfined, provider.unconfined)
    }

    @Test
    fun `diffAndParsing dispatcher is initialized with limited parallelism`() {
        assertNotNull(provider.diffAndParsing)
    }
}


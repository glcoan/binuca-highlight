package com.binuca.highlight.storage

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SerializedSaveQueueTest {
    @Test
    fun `processes overlapping requests one at a time and in order`() = runTest {
        val order = mutableListOf<Int>()
        var active = 0
        var maximumActive = 0
        val queue = SerializedSaveQueue<Int>(this) { value ->
            active += 1
            maximumActive = maxOf(maximumActive, active)
            delay(10)
            order += value
            active -= 1
        }

        queue.submit(10)
        queue.submit(20)
        queue.submit(30)
        advanceUntilIdle()

        assertEquals(listOf(10, 20, 30), order)
        assertEquals(1, maximumActive)
        queue.close()
    }
}

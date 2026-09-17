package com.binuca.highlight.storage

import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

class SerializedSaveQueue<T>(
    scope: CoroutineScope,
    private val process: suspend (T) -> Unit,
) : Closeable {
    private val channel = Channel<T>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (item in channel) process(item)
        }
    }

    fun submit(item: T): Boolean = channel.trySend(item).isSuccess

    override fun close() {
        channel.close()
    }
}

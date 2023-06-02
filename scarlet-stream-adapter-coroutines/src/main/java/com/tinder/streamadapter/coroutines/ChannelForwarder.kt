package com.tinder.streamadapter.coroutines

import com.tinder.scarlet.Stream
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.onClosed
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.trySendBlocking

internal class ChannelForwarder<T>(bufferSize: Int) : Stream.Observer<T> {
    private val channel = Channel<T>(bufferSize, BufferOverflow.DROP_OLDEST)

    fun start(stream: Stream<T>): ReceiveChannel<T> {
        stream.start(this)
        return channel
    }

    override fun onComplete() {
        channel.close()
    }

    override fun onError(throwable: Throwable) {
        channel.close(throwable)
    }

    override fun onNext(data: T) {
        channel.trySendBlocking(data).onClosed {
            // ignore
        }.onFailure {
            // ignore
        }
    }
}

/*
 * © 2018 Match Group, LLC.
 */

package com.tinder.scarlet.lifecycle

import com.tinder.scarlet.Lifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.getOrElse
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import java.util.concurrent.CancellationException
import kotlin.coroutines.CoroutineContext

/**
 * Used to trigger the start and stop of a WebSocket connection.
 */

fun <T> MutableSharedFlow<T>.subscriber(scope: CoroutineScope) =
    LifecycleRegistry.ReactiveSubscriber(
        this,
        requestSize = 1,
        scope = scope
    )

class LifecycleRegistry internal constructor(
    private val upstreamProcessor: MutableSharedFlow<Lifecycle.State>,
    private val downstreamProcessor: MutableSharedFlow<Lifecycle.State>,
    throttleDurationMillis: Long,
    throttleDispatcher: CoroutineContext,
    scope: CoroutineScope
) : Lifecycle by FlowableLifecycle(downstreamProcessor, throttleDispatcher),
    Subscriber<Lifecycle.State> by upstreamProcessor.subscriber(scope) {

    @Suppress("ReactiveStreamsSubscriberImplementation")
    class ReactiveSubscriber<T>(
        private val mutableSharedFlow: MutableSharedFlow<T>,
        private val scope: CoroutineScope,
        private val requestSize: Long
    ) : Subscriber<T> {
        private lateinit var subscription: Subscription

        // This implementation of ReactiveSubscriber always uses "offer" in its onNext implementation and it cannot
        // be reliable with rendezvous channel, so a rendezvous channel is replaced with buffer=1 channel
//        private val channel =
//            Channel<T>(if (capacity == Channel.RENDEZVOUS) 1 else capacity, onBufferOverflow)

//        suspend fun takeNextOrNull(): T? {
//            val result = channel.receiveCatching()
//            result.exceptionOrNull()?.let { throw it }
//            return result.getOrElse { null } // Closed channel
//        }

        override fun onNext(value: T) {
            // Controlled by requestSize
            require(mutableSharedFlow.tryEmit(value)) { "Element $value was not added to channel because it was full, $mutableSharedFlow" }
        }

        override fun onComplete() {
            scope.cancel()
        }

        override fun onError(t: Throwable?) {
            scope.cancel()
        }

        override fun onSubscribe(s: Subscription) {
            subscription = s
            makeRequest()
        }

        fun makeRequest() {
            subscription.request(requestSize)
        }

        fun cancel() {
            subscription.cancel()
        }
    }


    internal constructor (
        throttleTimeoutMillis: Long = 0,
        dispatcher: CoroutineContext,
        scope: CoroutineScope
    ) : this(
        MutableSharedFlow(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST),
        MutableSharedFlow(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST),
        throttleTimeoutMillis,
        dispatcher,
        scope
    )

    constructor(throttleDurationMillis: Long = 0, scope: CoroutineScope) : this(
        throttleDurationMillis,
        Dispatchers.Unconfined,
        scope
    )

    init {
        upstreamProcessor
            .distinctUntilChanged(Lifecycle.State::isEquivalentTo)
            .onEach {
                downstreamProcessor.tryEmit(it)
                if (it == Lifecycle.State.Destroyed) {
                    scope.cancel()
                }
            }.onCompletion {
                upstreamProcessor.tryEmit(Lifecycle.State.Destroyed)

            }
            .catch {
                upstreamProcessor.tryEmit(Lifecycle.State.Destroyed)
            }

//            .compose {
//                if (throttleDurationMillis != 0L) {
//                    it.throttleWithTimeout(
//                        throttleDurationMillis,
//                        TimeUnit.MILLISECONDS,
//                        throttleDispatcher
//                    )
//                } else {
//                    it
//                }
//            }
//            .distinctUntilChanged(Lifecycle.State::isEquivalentTo)
//            .subscribe(LifecycleStateSubscriber())
    }

    override fun onComplete() {
        upstreamProcessor.tryEmit(Lifecycle.State.Destroyed)
    }

    override fun onError(t: Throwable?) {
        upstreamProcessor.tryEmit(Lifecycle.State.Destroyed)
    }

//    private inner class LifecycleStateSubscriber : DisposableSubscriber<Lifecycle.State>() {
//        override fun onNext(state: Lifecycle.State) {
//            downstreamProcessor.tryEmit(state)
//            if (state == Lifecycle.State.Destroyed) {
//                downstreamProcessor.cancel()
//                dispose()
//            }
//        }
//
//        override fun onError(throwable: Throwable) {
//            throw IllegalStateException("Stream is terminated", throwable)
//        }
//
//        override fun onComplete() {
//            throw IllegalStateException("Stream is terminated")
//        }
//    }
}

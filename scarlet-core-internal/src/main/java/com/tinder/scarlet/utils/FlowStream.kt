/*
 * © 2018 Match Group, LLC.
 */

package com.tinder.scarlet.utils

import com.tinder.scarlet.Stream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import org.reactivestreams.Publisher
import kotlinx.coroutines.reactive.asPublisher


class FlowStream<T : Any>(
    private val flow: Flow<T>,
    private val scope: CoroutineScope
) : Stream<T>, Publisher<T> by flow.asPublisher(SupervisorJob()) {

    override fun start(observer: Stream.Observer<T>): Stream.Disposable {
        flow
            .onEach {
                observer.onNext(it)
            }
            .catch { observer.onError(it) }
            .onCompletion {
                observer.onComplete()
            }

        return FlowDisposable(scope)
    }
}

private class FlowDisposable(private val scope: CoroutineScope) : Stream.Disposable {
    override fun dispose() {
        scope.cancel()
    }

    override fun isDisposed() = scope.isActive

}

fun <T : Any> Flow<T>.stream(scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) =
    FlowStream(this, scope) as Stream<T>

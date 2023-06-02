/*
 * © 2018 Match Group, LLC.
 */

package com.tinder.scarlet.internal.connection.subscriber

import com.tinder.scarlet.Event
import com.tinder.scarlet.Stream
import com.tinder.scarlet.WebSocket
import com.tinder.scarlet.internal.connection.Connection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription

internal class WebSocketEventSubscriber(
    private val stateManager: Connection.StateManager,
    private val scope: CoroutineScope
) : Subscriber<WebSocket.Event>, Stream.Disposable {
    override fun onNext(webSocketEvent: WebSocket.Event) =
        stateManager.handleEvent(Event.OnWebSocket.Event(webSocketEvent))

    override fun onComplete() = stateManager.handleEvent(Event.OnWebSocket.Terminate)
    override fun onSubscribe(s: Subscription?) {
        s?.request(1)
    }

    override fun onError(throwable: Throwable) = throw throwable
    override fun dispose() = scope.cancel()

    override fun isDisposed() = scope.isActive
}

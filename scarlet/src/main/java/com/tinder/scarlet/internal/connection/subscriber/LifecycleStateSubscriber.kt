/*
 * © 2018 Match Group, LLC.
 */

package com.tinder.scarlet.internal.connection.subscriber

import com.tinder.scarlet.Event
import com.tinder.scarlet.Lifecycle
import com.tinder.scarlet.internal.connection.Connection
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import java.util.concurrent.atomic.AtomicInteger

internal class LifecycleStateSubscriber(
    private val stateManager: Connection.StateManager
) : Subscriber<Lifecycle.State> {
    private val pendingRequestCount = AtomicInteger()

    lateinit var subscription: Subscription
    override fun onNext(lifecycleState: Lifecycle.State) {
        val value = pendingRequestCount.decrementAndGet()
        if (value < 0) {
            pendingRequestCount.set(0)
        }
        stateManager.handleEvent(Event.OnLifecycle.StateChange(lifecycleState))
    }

    override fun onComplete() = stateManager.handleEvent(Event.OnLifecycle.Terminate)
    override fun onSubscribe(s: Subscription) {
        s.request(1)
        this.subscription = s
    }

    override fun onError(throwable: Throwable) = throw throwable

    fun requestNext() {
        if (pendingRequestCount.get() == 0) {
            pendingRequestCount.incrementAndGet()
            subscription.request(1)
        }

    }
}

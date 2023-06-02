/*
 * © 2018 Match Group, LLC.
 */

package com.tinder.scarlet.internal.connection.subscriber

import com.tinder.scarlet.Event
import com.tinder.scarlet.internal.connection.Connection
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription

internal class RetryTimerSubscriber(
    private val stateManager: Connection.StateManager
) : Subscriber<Long> {
    override fun onNext(t: Long) = stateManager.handleEvent(Event.OnRetry)

    override fun onComplete() {
    }

    override fun onSubscribe(s: Subscription?) {
        s?.request(1)
    }

    override fun onError(throwable: Throwable) = throw throwable
}

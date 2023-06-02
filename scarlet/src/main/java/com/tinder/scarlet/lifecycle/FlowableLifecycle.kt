/*
 * © 2018 Match Group, LLC.
 */

package com.tinder.scarlet.lifecycle

import com.tinder.scarlet.Lifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.asPublisher
import org.reactivestreams.Publisher
import kotlin.coroutines.CoroutineContext

internal class FlowableLifecycle(
    private val flow: Flow<Lifecycle.State>,
    private val dispatcher: CoroutineContext
) : Lifecycle, Publisher<Lifecycle.State> by flow.asPublisher() {

    override fun combineWith(vararg others: Lifecycle): Lifecycle {
        val lifecycles = listOf<Lifecycle>(this) + others
        val timedLifecycleStateFlows = lifecycles.map {
            it.asFlow()
        }

        val flow = combine(timedLifecycleStateFlows) {
            it.toList().combine()
        }.flowOn(dispatcher)

        return FlowableLifecycle(flow, dispatcher)
    }
}

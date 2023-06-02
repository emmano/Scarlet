/*
 * © 2018 Match Group, LLC.
 */

package com.tinder.scarlet.lifecycle

import com.tinder.scarlet.Lifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

internal class DefaultLifecycle(
    private val lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(scope = CoroutineScope(Dispatchers.Unconfined))
) : Lifecycle by lifecycleRegistry {

    init {
        lifecycleRegistry.onNext(Lifecycle.State.Started)
    }
}

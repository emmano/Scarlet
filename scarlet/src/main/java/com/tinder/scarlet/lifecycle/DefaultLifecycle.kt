/*
 * © 2018 Match Group, LLC.
 */

package com.tinder.scarlet.lifecycle

import com.tinder.scarlet.Lifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

internal class DefaultLifecycle(
    private val lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Unconfined))
) : Lifecycle by lifecycleRegistry {

    init {
        lifecycleRegistry.onNext(Lifecycle.State.Started)
    }
}

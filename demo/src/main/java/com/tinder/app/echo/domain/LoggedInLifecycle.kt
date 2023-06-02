/*
 * © 2018 Match Group, LLC.
 */

package com.tinder.app.echo.domain

import com.tinder.app.echo.inject.EchoBotScope
import com.tinder.scarlet.Lifecycle
import com.tinder.scarlet.lifecycle.LifecycleRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

@EchoBotScope
class LoggedInLifecycle constructor(
    authStatusRepository: AuthStatusRepository,
    private val lifecycleRegistry: LifecycleRegistry
) : Lifecycle by lifecycleRegistry {

    @Inject constructor(authStatusRepository: AuthStatusRepository) : this(authStatusRepository, LifecycleRegistry(scope = CoroutineScope(
        Dispatchers.Main)
    ))

    init {
        authStatusRepository.observeAuthStatus()
            .map {
                when (it) {
                    AuthStatus.LOGGED_IN -> Lifecycle.State.Started
                    AuthStatus.LOGGED_OUT -> Lifecycle.State.Stopped.WithReason()
                }
            }
            .subscribe(lifecycleRegistry)
    }
}

/*
 * © 2018 Match Group, LLC.
 */

package com.tinder.scarlet


sealed class State {
    data class WaitingToRetry internal constructor(
        internal val timerDisposable: Stream.Disposable,
        val retryCount: Int,
        val retryInMillis: Long
    ) : State()

    data class Connecting internal constructor(
        internal val session: Session,
        val retryCount: Int
    ) : State()

    data class Connected internal constructor(
        internal val session: Session
    ) : State()

    object Disconnecting : State()

    object Disconnected : State()

    object Destroyed : State()
}

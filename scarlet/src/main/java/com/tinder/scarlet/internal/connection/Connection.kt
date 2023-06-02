/*
 * © 2018 Match Group, LLC.
 */

package com.tinder.scarlet.internal.connection

import com.tinder.StateMachine
import com.tinder.scarlet.Event
import com.tinder.scarlet.Event.OnLifecycle
import com.tinder.scarlet.Event.OnRetry
import com.tinder.scarlet.Event.OnWebSocket
import com.tinder.scarlet.Lifecycle
import com.tinder.scarlet.Message
import com.tinder.scarlet.Session
import com.tinder.scarlet.SideEffect
import com.tinder.scarlet.State
import com.tinder.scarlet.State.Connected
import com.tinder.scarlet.State.Connecting
import com.tinder.scarlet.State.Destroyed
import com.tinder.scarlet.State.Disconnected
import com.tinder.scarlet.State.Disconnecting
import com.tinder.scarlet.State.WaitingToRetry
import com.tinder.scarlet.WebSocket
import com.tinder.scarlet.internal.connection.subscriber.LifecycleStateSubscriber
import com.tinder.scarlet.internal.connection.subscriber.RetryTimerSubscriber
import com.tinder.scarlet.internal.connection.subscriber.WebSocketEventSubscriber
import com.tinder.scarlet.lifecycle.LifecycleRegistry
import com.tinder.scarlet.retry.BackoffStrategy
import com.tinder.StateMachine.Matcher.Companion.any
import com.tinder.StateMachine.Transition.Valid
import com.tinder.scarlet.Stream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.reactive.asFlow
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

internal class Connection(
    val stateManager: StateManager
) {

    fun startForever() = stateManager.subscribe()

    fun observeEvent(): Flow<Event> = stateManager.observeEvent()

    fun send(message: Message): Boolean {
        val state = stateManager.state
        return when (state) {
            is Connected -> state.session.webSocket.send(message)
            else -> false
        }
    }

    internal class StateManager(
        val lifecycle: Lifecycle,
        private val webSocketFactory: WebSocket.Factory,
        private val backoffStrategy: BackoffStrategy,
        private val dispatcher: CoroutineContext,
        private val scope: CoroutineScope
    ) {
        val state: State
            get() = stateMachine.state

        private val lifecycleStateSubscriber = LifecycleStateSubscriber(this)
        private val eventProcessor = MutableSharedFlow<Event>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        private val stateMachine = StateMachine.create<State, Event, SideEffect> {
            state<Disconnected> {
                onEnter {
                    requestNextLifecycleState()
                }
                on(lifecycleStart()) {
                    val webSocketSession = open()
                    transitionTo(Connecting(session = webSocketSession, retryCount = 0))
                }
                on(lifecycleStop()) {
                    // No-op
                    requestNextLifecycleState()
                    dontTransition()
                }
                on<OnLifecycle.Terminate>() {
                    transitionTo(Destroyed)
                }
            }
            state<WaitingToRetry> {
                onEnter {
                    requestNextLifecycleState()
                }
                on<OnRetry> {
                    val webSocketSession = open()
                    transitionTo(
                        Connecting(
                            session = webSocketSession,
                            retryCount = retryCount + 1
                        )
                    )
                }
                on(lifecycleStart()) {
                    // No-op
                    requestNextLifecycleState()
                    dontTransition()
                }
                on(lifecycleStop()) {
                    cancelRetry()
                    transitionTo(Disconnected)
                }
                on<OnLifecycle.Terminate>() {
                    cancelRetry()
                    transitionTo(Destroyed)
                }
            }
            state<Connecting> {
                on(webSocketOpen()) {
                    transitionTo(Connected(session = session))
                }
                on<OnWebSocket.Terminate>() {
                    val backoffDuration = backoffStrategy.backoffDurationMillisAt(retryCount)
                    val timerDisposable = scheduleRetry(backoffDuration, scope)
                    transitionTo(
                        WaitingToRetry(
                            timerDisposable = timerDisposable,
                            retryCount = retryCount,
                            retryInMillis = backoffDuration
                        )
                    )
                }
            }
            state<Connected> {
                onEnter {
                    requestNextLifecycleState()
                }
                on(lifecycleStart()) {
                    // No-op
                    requestNextLifecycleState()
                    dontTransition()
                }
                on(lifecycleStop()) {
                    initiateShutdown(it.state)
                    transitionTo(Disconnecting)
                }
                on<OnLifecycle.Terminate> {
                    session.webSocket.cancel()
                    transitionTo(Destroyed)
                }
                on<OnWebSocket.Terminate>() {
                    val backoffDuration = backoffStrategy.backoffDurationMillisAt(0)
                    val timerDisposable = scheduleRetry(backoffDuration, scope)
                    transitionTo(
                        WaitingToRetry(
                            timerDisposable = timerDisposable,
                            retryCount = 0,
                            retryInMillis = backoffDuration
                        )
                    )
                }
            }
            state<Disconnecting> {
                on<OnWebSocket.Terminate> {
                    transitionTo(Disconnected)
                }
            }
            state<Destroyed> {
                onEnter {
                    lifecycleStateSubscriber
                }
            }
            initialState(Disconnected)
            onTransition { transition ->
                transition.let {
                    if (it is Valid && it.fromState != it.toState) {
                        eventProcessor.tryEmit(Event.OnStateChange(state))
                    }
                }
            }
        }

        class ScopeDisposable(private val coroutineScope: CoroutineScope) : Stream.Disposable {
            override fun dispose() = coroutineScope.cancel()

            override fun isDisposed() = coroutineScope.isActive

        }

        fun <T> CoroutineScope.disposable() = ScopeDisposable(this)

        fun observeEvent(): Flow<Event> = eventProcessor

        fun subscribe() {
            lifecycle.subscribe(lifecycleStateSubscriber)
        }

        fun handleEvent(event: Event) {
            eventProcessor.tryEmit(event)
            stateMachine.transition(event)
        }

        private fun open(): Session {
            val webSocket = webSocketFactory.create()
            val subscriber = WebSocketEventSubscriber(this, scope)
            webSocket.open()
                .asFlow()
                .flowOn(dispatcher)
            return Session(webSocket, subscriber)
        }

        class TickerDisposable(private val scope: CoroutineScope) : Stream.Disposable {
            override fun dispose() {
                scope.cancel()
            }

            override fun isDisposed() = scope.isActive

        }

        fun ticker(interval: Long) = flow {
            delay(interval)
            emit(Unit)
        }

        private fun scheduleRetry(duration: Long, scope: CoroutineScope): Stream.Disposable {
            ticker(duration)
                .onEach {
                    handleEvent(OnRetry)
                }
                .buffer()
                .flowOn(dispatcher)
                .launchIn(scope)

            return TickerDisposable(scope)
        }

        private fun requestNextLifecycleState() = lifecycleStateSubscriber.requestNext()

        private fun Connected.initiateShutdown(state: Lifecycle.State) {
            when (state) {
                is Lifecycle.State.Stopped.WithReason -> session.webSocket.close(state.shutdownReason)
                Lifecycle.State.Stopped.AndAborted -> session.webSocket.cancel()
            }
        }

        private fun WaitingToRetry.cancelRetry() = timerDisposable.dispose()

        private fun lifecycleStart() =
            any<Event, Event.OnLifecycle.StateChange<*>>().where { state == Lifecycle.State.Started }

        private fun lifecycleStop() =
            any<Event, Event.OnLifecycle.StateChange<*>>().where { state is Lifecycle.State.Stopped }

        private fun webSocketOpen() = any<Event, Event.OnWebSocket.Event<*>>()
            .where { event is WebSocket.Event.OnConnectionOpened<*> }
    }

    class Factory(
        private val lifecycle: Lifecycle,
        private val webSocketFactory: WebSocket.Factory,
        private val backoffStrategy: BackoffStrategy,
        private val dispatcher: CoroutineContext,
        private val scope: CoroutineScope
    ) {
        private val sharedLifecycle: Lifecycle by lazy { createSharedLifecycle() }

        fun create(): Connection {
            val stateManager =
                StateManager(sharedLifecycle, webSocketFactory, backoffStrategy, dispatcher, scope)
            return Connection(stateManager)
        }

        private fun createSharedLifecycle() = LifecycleRegistry(scope = scope)
            .apply { lifecycle.subscribe(this) }
    }
}

/*
 * © 2018 Match Group, LLC.
 */

package com.tinder.scarlet



internal data class Session(val webSocket: WebSocket, val webSocketDisposable: Stream.Disposable)

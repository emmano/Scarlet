/*
 * © 2018 Match Group, LLC.
 */

package com.tinder.scarlet.websocket.okhttp

import com.tinder.scarlet.Message
import com.tinder.scarlet.ShutdownReason
import com.tinder.scarlet.WebSocket
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import okhttp3.Response
import okhttp3.WebSocketListener
import okio.ByteString

internal class OkHttpWebSocketEventObserver : WebSocketListener() {
    private val processor = MutableSharedFlow<WebSocket.Event>(1)

    fun observe(): Flow<WebSocket.Event> = processor

    fun terminate() {
//        processor.onComplete() 
    }

    override fun onOpen(webSocket: okhttp3.WebSocket, response: Response)  {
        processor.tryEmit(WebSocket.Event.OnConnectionOpened(webSocket))

    }
    override fun onMessage(webSocket: okhttp3.WebSocket, bytes: ByteString) {
        processor.tryEmit(WebSocket.Event.OnMessageReceived(Message.Bytes(bytes.toByteArray())))
    }

    override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
        processor.tryEmit(WebSocket.Event.OnMessageReceived(Message.Text(text)))
    }

    override fun onClosing(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
        processor.tryEmit(WebSocket.Event.OnConnectionClosing(ShutdownReason(code, reason)))
    }

    override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
        processor.tryEmit(WebSocket.Event.OnConnectionClosed(ShutdownReason(code, reason)))
    }

    override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: Response?) {
        processor.tryEmit(WebSocket.Event.OnConnectionFailed(t))
    }
}

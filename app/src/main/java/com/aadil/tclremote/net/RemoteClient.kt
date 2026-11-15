package com.aadil.tclremote.net

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import javax.net.ssl.SSLSocket

enum class Link { Idle, Connecting, Ready, Closed }

/** Talks to a paired TV on port 6466 and sends key presses. */
class RemoteClient(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: SSLSocket? = null
    private var readerJob: Job? = null

    private val _link = MutableStateFlow(Link.Idle)
    val link: StateFlow<Link> = _link

    suspend fun connect(host: String) = withContext(Dispatchers.IO) {
        close()
        _link.value = Link.Connecting
        val s = try {
            ClientIdentity.openSocket(host, REMOTE_PORT)
        } catch (e: Exception) {
            _link.value = Link.Closed
            throw e
        }
        socket = s
        val ready = CompletableDeferred<Unit>()
        readerJob = scope.launch {
            try {
                readLoop(s, ready)
            } catch (e: Exception) {
                ready.completeExceptionally(e)
            } finally {
                if (socket === s) _link.value = Link.Closed
            }
        }
        try {
            withTimeout(10_000) { ready.await() }
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    /** Sends one short key press. Codes are Android KeyEvent codes. */
    fun key(code: Int) {
        sendAsync {
            message(10) {
                varint(1, code.toLong())
                varint(2, DIRECTION_SHORT)
            }
        }
    }

    /** Asks the TV to open a link, which launches the matching app. */
    fun openLink(url: String) {
        sendAsync {
            message(90) { string(1, url) }
        }
    }

    fun close() {
        readerJob?.cancel()
        readerJob = null
        runCatching { socket?.close() }
        socket = null
        _link.value = Link.Idle
    }

    private fun sendAsync(build: PbWriter.() -> Unit) {
        scope.launch {
            try {
                send(build)
            } catch (e: Exception) {
                _link.value = Link.Closed
            }
        }
    }

    @Synchronized
    private fun send(build: PbWriter.() -> Unit) {
        val s = socket ?: throw IOException("Not connected")
        Framing.write(s.outputStream, PbWriter().apply(build).toByteArray())
    }

    private fun readLoop(s: SSLSocket, ready: CompletableDeferred<Unit>) {
        val input = s.inputStream
        while (true) {
            val msg = PbMessage.parse(Framing.read(input))
            when {
                // The TV announces itself; answer with who we are.
                msg.has(1) -> send {
                    message(1) {
                        varint(1, FEATURES)
                        message(2) {
                            string(1, Build.MODEL)
                            string(2, Build.MANUFACTURER)
                            varint(3, 1)
                            string(4, "1")
                            string(5, context.packageName)
                            string(6, "1.0.0")
                        }
                    }
                }
                // The TV is ready for commands once we confirm we're active.
                msg.has(2) -> {
                    send { message(2) { varint(1, FEATURES) } }
                    _link.value = Link.Ready
                    ready.complete(Unit)
                }
                // Keep-alive: echo the number back.
                msg.has(8) -> {
                    val n = msg.message(8)?.varint(1) ?: 0L
                    send { message(9) { varint(1, n) } }
                }
                msg.has(3) -> throw IOException("The TV reported an error")
            }
        }
    }

    private companion object {
        const val REMOTE_PORT = 6466
        const val FEATURES = 622L
        const val DIRECTION_SHORT = 3L
    }
}

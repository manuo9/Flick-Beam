package com.flickbeam.remote.network

import com.flickbeam.shared.Capability
import com.flickbeam.shared.ControlMessage
import com.flickbeam.shared.Disconnected
import com.flickbeam.shared.Endpoints
import com.flickbeam.shared.ErrorMessage
import com.flickbeam.shared.Hello
import com.flickbeam.shared.HelloResponse
import com.flickbeam.shared.PairRequest
import com.flickbeam.shared.PairRequired
import com.flickbeam.shared.PairSuccess
import com.flickbeam.shared.FlickBeamJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Connects to a TV's /control WebSocket and runs the HELLO handshake.
 *
 * Once a connection has been established, drops are recovered automatically:
 * the connection is re-opened silently while the UI keeps showing "connected".
 * This survives the app being backgrounded (e.g. while picking a file to send),
 * which otherwise tears the socket down.
 */
@Singleton
class ControlConnectionController @Inject constructor(
    private val identity: PhoneIdentityStore,
    private val pairingStore: PhonePairingStore,
    private val lastTvStore: LastTvStore,
) : ConnectionController {

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private var webSocket: WebSocket? = null
    private var currentTv: DiscoveredTv? = null

    // Bumped each time we (re)open, so callbacks from an old socket are ignored.
    private var generation = 0
    private var intentionalDisconnect = false
    private var disconnectedByTv = false
    private var everConnected = false
    private var reconnectAttempts = 0

    override fun connectedTarget(): DiscoveredTv? = currentTv

    override fun lastKnownTv(): DiscoveredTv? = lastTvStore.load()

    override fun connect(tv: DiscoveredTv) {
        intentionalDisconnect = false
        disconnectedByTv = false
        everConnected = false
        reconnectAttempts = 0
        currentTv = tv
        _state.value = ConnectionState.Connecting(tv.name)
        openSocket(tv)
    }

    override fun reconnect() {
        val tv = currentTv ?: return
        connect(tv)
    }

    private fun openSocket(tv: DiscoveredTv) {
        val gen = ++generation
        webSocket?.cancel()

        val request = Request.Builder()
            .url("ws://${tv.host}:${tv.port}${Endpoints.CONTROL}")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (gen != generation) return
                sendHello(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (gen != generation) return
                val message = runCatching {
                    FlickBeamJson.decodeFromString<ControlMessage>(text)
                }.getOrNull() ?: return

                when (message) {
                    is HelloResponse -> {
                        everConnected = true
                        reconnectAttempts = 0
                        currentTv?.let { lastTvStore.save(it) }
                        _state.value = ConnectionState.Connected(message.device, message.version)
                    }
                    is PairRequired -> {
                        val name = currentTv?.name ?: "TV"
                        _state.value = ConnectionState.PairingRequired(name)
                    }
                    is PairSuccess -> {
                        // Key the token by the TV's real device id, not our (possibly
                        // placeholder, e.g. manual-entry) local one, so it's reusable
                        // however we reconnect next time.
                        pairingStore.save(message.tvDeviceId, message.token)
                        currentTv = currentTv?.copy(deviceId = message.tvDeviceId, name = message.name)
                        // Save now, not just on the HelloResponse that follows — if the
                        // app gets killed in that narrow window, we still remember it.
                        currentTv?.let { lastTvStore.save(it) }
                        // Re-handshake now that we have a token, to reach Connected.
                        sendHello(webSocket)
                    }
                    is ErrorMessage -> {
                        // A pairing failure keeps us on the code entry with a hint.
                        val name = currentTv?.name
                        if (message.reason == "pairing_failed" && name != null) {
                            _state.value = ConnectionState.PairingRequired(name, "Incorrect code, try again")
                        } else {
                            _state.value = ConnectionState.Error("${message.code}: ${message.reason}")
                        }
                    }
                    is Disconnected -> disconnectedByTv = true
                    else -> Unit
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (gen != generation) return
                handleDrop(t.message ?: "Connection failed")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (gen != generation) return
                handleDrop("Connection closed")
            }
        })
    }

    private fun handleDrop(reason: String) {
        if (intentionalDisconnect) return
        val tv = currentTv ?: return

        // A deliberate TV-side disconnect must stick — don't silently reconnect,
        // require the user to tap Connect again.
        if (disconnectedByTv) {
            _state.value = ConnectionState.Disconnected(tv.name)
            return
        }

        // Only auto-recover a connection that actually worked; a first-attempt
        // failure is a real error the user should see.
        if (!everConnected) {
            _state.value = ConnectionState.Error(reason)
            return
        }
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            _state.value = ConnectionState.Error("Lost connection to ${tv.name}")
            return
        }

        // Keep showing "connected" and quietly re-open in the background.
        reconnectAttempts++
        scope.launch {
            delay(RECONNECT_DELAY_MS)
            if (!intentionalDisconnect && currentTv != null) {
                openSocket(currentTv!!)
            }
        }
    }

    private fun sendHello(socket: WebSocket) {
        val tv = currentTv ?: return
        val hello = Hello(
            deviceId = identity.deviceId,
            name = identity.deviceName,
            model = identity.model,
            token = pairingStore.token(tv.deviceId),
            capabilities = listOf(
                Capability.SEND,
                Capability.RECEIVE,
                Capability.REMOTE,
                Capability.KEYBOARD,
            ),
        )
        runCatching { socket.send(FlickBeamJson.encodeToString<ControlMessage>(hello)) }
    }

    override fun submitPairingCode(code: String) {
        val socket = webSocket ?: return
        val pair = PairRequest(deviceId = identity.deviceId, code = code)
        runCatching { socket.send(FlickBeamJson.encodeToString<ControlMessage>(pair)) }
    }

    override fun send(message: ControlMessage): Boolean {
        val socket = webSocket ?: return false
        return runCatching {
            socket.send(FlickBeamJson.encodeToString<ControlMessage>(message))
        }.getOrDefault(false)
    }

    override fun disconnect() {
        intentionalDisconnect = true
        generation++
        webSocket?.let { runCatching { it.close(NORMAL_CLOSURE, null) } }
        webSocket = null
        currentTv = null
        everConnected = false
        _state.value = ConnectionState.Idle
    }

    private companion object {
        const val NORMAL_CLOSURE = 1000
        const val MAX_RECONNECT_ATTEMPTS = 10
        const val RECONNECT_DELAY_MS = 1500L
    }
}

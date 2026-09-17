package com.flickbeam.tv.network

import android.content.Context
import android.net.wifi.WifiManager
import com.flickbeam.shared.ControlMessage
import com.flickbeam.shared.LocalNetwork
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the Receive listener. Starting acquires a WiFi multicast lock (so
 * NSD works on the local network), binds the embedded server to a free port, and
 * advertises it. Stopping unwinds all three. Idempotent and safe to call again.
 */
@Singleton
class ReceiverControllerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val identity: DeviceIdentityStore,
    private val nsdRegistrar: NsdRegistrar,
    private val incomingFiles: ReceivedFiles,
    private val pairedDevices: PairedDevices,
) : ReceiverController {

    // In-progress pairing: a code shown on screen, started explicitly from the TV.
    @Volatile
    private var pendingCode: String? = null

    @Volatile
    private var pendingAttempts: Int = 0

    private var pairingJob: Job? = null

    private val pairingAuthority = object : PairingAuthority {
        override fun isPaired(token: String?): Boolean = pairedDevices.isValidToken(token)

        override fun beginPairing() {
            pairingJob?.cancel()
            val code = (100000..999999).random().toString()
            pendingCode = code
            pendingAttempts = 0
            setPairingCode(code, PAIRING_TIMEOUT_SECONDS)
            pairingJob = scope.launch {
                var secondsLeft = PAIRING_TIMEOUT_SECONDS
                while (secondsLeft > 0) {
                    delay(1000)
                    secondsLeft--
                    setPairingCode(pendingCode, secondsLeft)
                }
                cancelPairing()
            }
        }

        override fun completePairing(phoneDeviceId: String, phoneName: String, code: String): String? {
            if (pendingCode == null) return null
            if (code != pendingCode) {
                pendingAttempts++
                if (pendingAttempts >= MAX_PAIRING_ATTEMPTS) cancelPairing()
                return null
            }
            val token = pairedDevices.pair(phoneDeviceId, phoneName)
            cancelPairing()
            return token
        }

        private fun cancelPairing() {
            pairingJob?.cancel()
            pairingJob = null
            pendingCode = null
            pendingAttempts = 0
            setPairingCode(null, null)
        }
    }

    private val _state = MutableStateFlow<ReceiverState>(ReceiverState.Stopped)
    override val state: StateFlow<ReceiverState> = _state.asStateFlow()

    private val _remoteEvents = MutableSharedFlow<ControlMessage>(extraBufferCapacity = 64)
    override val remoteEvents: SharedFlow<ControlMessage> = _remoteEvents.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var server: FlickBeamServer? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    override fun start() {
        if (server != null) return
        _state.value = ReceiverState.Starting
        scope.launch {
            try {
                acquireMulticastLock()
                incomingFiles.cleanupPartials()
                val newServer = FlickBeamServer(identity, incomingFiles, pairingAuthority, serverEvents)
                    .also { server = it }
                // A real socket timeout lets the TV notice dead connections: a dropped
                // upload times out (so its temp file gets cleaned up) and a dropped
                // control channel frees its slot. The live control WebSocket stays open
                // because the phone pings every 20s, well inside this window.
                newServer.start(SOCKET_TIMEOUT_MS, false)
                val port = newServer.listeningPort
                nsdRegistrar.register(port)
                _state.value = ReceiverState.Listening(
                    deviceName = identity.deviceName,
                    host = LocalNetwork.wifiIpv4(),
                    port = port,
                    pairedDeviceName = pairedDevices.pairedDeviceName(),
                )
            } catch (e: Exception) {
                stopInternal()
                _state.value = ReceiverState.Error(e.message ?: "Could not start the receiver")
            }
        }
    }

    override fun stop() {
        scope.launch {
            stopInternal()
            _state.value = ReceiverState.Stopped
        }
    }

    override fun startPairing() {
        scope.launch {
            server?.disconnectActiveClient()
            pairedDevices.clear()
            _state.update { current ->
                if (current is ReceiverState.Listening) {
                    current.copy(connectedPhone = null, pairedDeviceName = null)
                } else {
                    current
                }
            }
            pairingAuthority.beginPairing()
        }
    }

    override fun disconnect() {
        scope.launch {
            server?.disconnectActiveClient()
            setConnectedPhone(null)
        }
    }

    private val serverEvents = object : FlickBeamServer.Events {
        override fun onClientConnected(name: String) = setConnectedPhone(name)
        override fun onClientDisconnected() = setConnectedPhone(null)
        override fun onRemote(message: ControlMessage) {
            _remoteEvents.tryEmit(message)
        }

        override fun onUploadStarted(name: String) = setTransfer("Receiving $name…", 0)
        override fun onUploadProgress(percent: Int) = setTransferProgress(percent)
        override fun onUploadFinished(name: String) = setTransfer("Received $name", null)
        override fun onUploadFailed(name: String) = setTransfer("Failed to receive $name", null)
    }

    private fun setConnectedPhone(name: String?) {
        _state.update { current ->
            if (current !is ReceiverState.Listening) return@update current
            if (name != null) {
                current.copy(connectedPhone = name, pairedDeviceName = name)
            } else {
                current.copy(connectedPhone = null)
            }
        }
    }

    private fun setTransfer(status: String?, progress: Int?) {
        _state.update { current ->
            if (current is ReceiverState.Listening) {
                current.copy(transferStatus = status, transferProgress = progress)
            } else {
                current
            }
        }
    }

    private fun setTransferProgress(progress: Int?) {
        _state.update { current ->
            if (current is ReceiverState.Listening) current.copy(transferProgress = progress) else current
        }
    }

    private fun setPairingCode(code: String?, secondsLeft: Int?) {
        _state.update { current ->
            if (current is ReceiverState.Listening) {
                current.copy(pairingCode = code, pairingSecondsLeft = secondsLeft)
            } else {
                current
            }
        }
    }

    private fun stopInternal() {
        nsdRegistrar.unregister()
        server?.let { runCatching { it.stop() } }
        server = null
        releaseMulticastLock()
    }

    private fun acquireMulticastLock() {
        if (multicastLock != null) return
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifi.createMulticastLock("flickbeam").apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }
    }

    private fun releaseMulticastLock() {
        multicastLock?.let { lock ->
            if (lock.isHeld) runCatching { lock.release() }
        }
        multicastLock = null
    }

    private companion object {
        const val PAIRING_TIMEOUT_SECONDS = 120
        const val MAX_PAIRING_ATTEMPTS = 5

        // Longer than the phone's 20s heartbeat so a healthy connection never times
        // out, but short enough to notice a real drop quickly.
        const val SOCKET_TIMEOUT_MS = 40_000
    }
}

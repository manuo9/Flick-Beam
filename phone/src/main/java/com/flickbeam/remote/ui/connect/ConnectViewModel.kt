package com.flickbeam.remote.ui.connect

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flickbeam.remote.network.ConnectionController
import com.flickbeam.remote.network.ConnectionState
import com.flickbeam.remote.network.DiscoveredTv
import com.flickbeam.remote.network.DiscoveryController
import com.flickbeam.remote.network.CastResult
import com.flickbeam.remote.network.FileSender
import com.flickbeam.remote.network.MediaCastController
import com.flickbeam.remote.network.SendItem
import com.flickbeam.remote.network.SendItemStatus
import com.flickbeam.shared.Back
import com.flickbeam.shared.PlayMedia
import com.flickbeam.shared.PlaySlideshow
import com.flickbeam.shared.DpadDown
import com.flickbeam.shared.DpadLeft
import com.flickbeam.shared.DpadRight
import com.flickbeam.shared.DpadUp
import com.flickbeam.shared.Menu
import com.flickbeam.shared.Ok
import com.flickbeam.shared.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

/** Drives the Connect screen: discovers TVs, connects, remotes, and sends files. */
@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val discovery: DiscoveryController,
    private val connection: ConnectionController,
    private val fileSender: FileSender,
    private val mediaCast: MediaCastController,
) : ViewModel() {

    val devices: StateFlow<List<DiscoveredTv>> = discovery.devices
    val connectionState: StateFlow<ConnectionState> = connection.state

    /** The last TV successfully connected to, for a one-tap reconnect. */
    val lastTv: DiscoveredTv? get() = connection.lastKnownTv()

    private val _sendItems = MutableStateFlow<List<SendItem>>(emptyList())
    val sendItems: StateFlow<List<SendItem>> = _sendItems.asStateFlow()

    private var sendJob: Job? = null

    fun startDiscovery() = discovery.start()

    fun stopDiscovery() = discovery.stop()

    /** Dismiss a stale/duplicate entry from the discovered list. */
    fun forgetDevice(deviceId: String) = discovery.forget(deviceId)

    fun connect(tv: DiscoveredTv) = connection.connect(tv)

    /** Connect directly to a manually-entered address (fallback when discovery fails). */
    fun connectManual(host: String, port: Int) {
        connection.connect(
            DiscoveredTv(
                deviceId = "manual:$host:$port",
                name = host,
                host = host,
                port = port,
            ),
        )
    }

    fun disconnect() = connection.disconnect()

    fun reconnect() = connection.reconnect()

    fun submitPairingCode(code: String) = connection.submitPairingCode(code)

    // Remote-control actions, sent over the open connection. Block bodies so a
    // dropped-connection "false" from send() doesn't leak out as a return value.
    fun up() { connection.send(DpadUp) }
    fun down() { connection.send(DpadDown) }
    fun left() { connection.send(DpadLeft) }
    fun right() { connection.send(DpadRight) }
    fun ok() { connection.send(Ok) }
    fun back() { connection.send(Back) }
    fun menu() { connection.send(Menu) }
    fun sendText(text: String) { connection.send(Text(text)) }

    private val _castError = MutableStateFlow<String?>(null)
    val castError: StateFlow<String?> = _castError.asStateFlow()

    fun dismissCastError() {
        _castError.value = null
    }

    /** Stream picked media to the TV: images become a slideshow, one video/audio plays. */
    fun castMedia(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { mediaCast.cast(uris) }
            if (result == null) {
                _castError.value = "Couldn't read the selected file"
                return@launch
            }
            val sent = when (result) {
                is CastResult.Single -> connection.send(
                    PlayMedia(
                        url = result.info.url,
                        title = result.info.title,
                        mimeType = result.info.mimeType,
                    ),
                )
                is CastResult.Slideshow -> connection.send(PlaySlideshow(urls = result.urls))
            }
            if (!sent) {
                _castError.value = "Not connected to the TV — reconnect and try again"
            }
        }
    }

    /** Queue picked files and start sending them to the TV one after another. */
    fun sendFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _sendItems.value = uris.map { uri ->
            SendItem(
                id = UUID.randomUUID().toString(),
                uri = uri,
                name = fileSender.displayName(uri),
                status = SendItemStatus.Waiting,
            )
        }
        startQueue()
    }

    /** Pull a not-yet-started file out of the queue. */
    fun cancelItem(id: String) {
        _sendItems.update { items ->
            items.map {
                if (it.id == id && it.status is SendItemStatus.Waiting) {
                    it.copy(status = SendItemStatus.Canceled)
                } else {
                    it
                }
            }
        }
    }

    /** Re-queue every failed file and send them again. */
    fun retryFailed() {
        _sendItems.update { items ->
            items.map {
                if (it.status is SendItemStatus.Failed) it.copy(status = SendItemStatus.Waiting) else it
            }
        }
        startQueue()
    }

    /** Clear the finished batch so the picker shows again. */
    fun clearBatch() {
        if (sendJob?.isActive == true) return
        _sendItems.value = emptyList()
    }

    private fun startQueue() {
        if (sendJob?.isActive == true) return
        val target = connection.connectedTarget() ?: return
        sendJob = viewModelScope.launch {
            while (true) {
                val next = _sendItems.value
                    .firstOrNull { it.status is SendItemStatus.Waiting } ?: break
                setStatus(next.id, SendItemStatus.Uploading(0))
                val result = withContext(Dispatchers.IO) {
                    fileSender.send(target, next.uri) { percent ->
                        setStatus(next.id, SendItemStatus.Uploading(percent))
                    }
                }
                setStatus(
                    next.id,
                    result.fold(
                        onSuccess = { SendItemStatus.Sent },
                        onFailure = { SendItemStatus.Failed(it.message ?: "Send failed") },
                    ),
                )
            }
        }
    }

    private fun setStatus(id: String, status: SendItemStatus) {
        _sendItems.update { items ->
            items.map { if (it.id == id) it.copy(status = status) else it }
        }
    }
}

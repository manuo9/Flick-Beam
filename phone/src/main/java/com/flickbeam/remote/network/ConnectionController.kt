package com.flickbeam.remote.network

import com.flickbeam.shared.ControlMessage
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages the phone's control-channel connection to a TV. The ViewModel observes
 * [state] and calls connect/disconnect/send instead of touching the socket directly.
 */
interface ConnectionController {
    val state: StateFlow<ConnectionState>
    fun connect(tv: DiscoveredTv)
    fun disconnect()

    /** Reconnect to the last TV after being deliberately disconnected by it. */
    fun reconnect()

    /** The TV currently connected to, or null. Used for file uploads. */
    fun connectedTarget(): DiscoveredTv?

    /** The last TV this phone successfully connected to, or null. */
    fun lastKnownTv(): DiscoveredTv?

    /** Send a control message (e.g. a remote key press) over the open connection.
     * Returns false if there's no live connection to send it on. */
    fun send(message: ControlMessage): Boolean

    /** Submit the pairing code the user read off the TV. */
    fun submitPairingCode(code: String)
}

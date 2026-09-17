package com.flickbeam.tv.network

import com.flickbeam.shared.ControlMessage
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Starts and stops the TV's "Receive" listener: the embedded server plus NSD
 * advertising. The ViewModel talks to this instead of touching sockets or
 * NsdManager directly. [state] reflects what's happening for the UI, and
 * [remoteEvents] emits remote-control messages (D-pad, OK, Back, text) coming
 * from a connected phone.
 */
interface ReceiverController {
    val state: StateFlow<ReceiverState>
    val remoteEvents: SharedFlow<ControlMessage>
    fun start()
    fun stop()

    /**
     * Start pairing with a new phone. Replaces whatever phone was previously
     * paired: its token is invalidated and its live connection dropped first.
     */
    fun startPairing()

    /** End the current live connection, if any. The pairing itself is kept. */
    fun disconnect()
}

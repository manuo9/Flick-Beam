package com.flickbeam.tv.network

/** What the Receive feature is currently doing, for the UI to show. */
sealed interface ReceiverState {
    /** Not listening. */
    data object Stopped : ReceiverState

    /** Bringing the server and discovery up. */
    data object Starting : ReceiverState

    /**
     * Ready: the server is bound and advertised on the network.
     *
     * [pairedDeviceName] is the phone this TV is paired with, or null if none.
     * [connectedPhone] is that phone's name while it's actually connected right now.
     * [pairingCode]/[pairingSecondsLeft] are set only while a pairing session
     * started from the TV is in progress.
     */
    data class Listening(
        val deviceName: String,
        val host: String?,
        val port: Int,
        val pairedDeviceName: String? = null,
        val connectedPhone: String? = null,
        val transferStatus: String? = null,
        val transferProgress: Int? = null,
        val pairingCode: String? = null,
        val pairingSecondsLeft: Int? = null,
    ) : ReceiverState

    /** Something went wrong while starting. */
    data class Error(val message: String) : ReceiverState
}

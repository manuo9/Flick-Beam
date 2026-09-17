package com.flickbeam.remote.network

/** The state of the phone's control-channel connection to a TV. */
sealed interface ConnectionState {
    /** Not connected to anything. */
    data object Idle : ConnectionState

    /** Opening the connection and doing the handshake. */
    data class Connecting(val tvName: String) : ConnectionState

    /** The TV needs a pairing code entered (shown on the TV screen). */
    data class PairingRequired(val tvName: String, val error: String? = null) : ConnectionState

    /** Handshake done — talking to the TV. */
    data class Connected(val tvName: String, val version: String) : ConnectionState

    /** The TV deliberately disconnected us; tap Connect to resume. */
    data class Disconnected(val tvName: String) : ConnectionState

    /** Couldn't connect or the handshake failed. */
    data class Error(val message: String) : ConnectionState
}

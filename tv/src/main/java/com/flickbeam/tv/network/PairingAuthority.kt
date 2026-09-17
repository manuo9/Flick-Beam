package com.flickbeam.tv.network

/**
 * Decides whether a phone may talk to this TV. Used by the server to gate the
 * control channel and uploads behind pairing.
 */
interface PairingAuthority {
    /** True if the token belongs to the paired phone. */
    fun isPaired(token: String?): Boolean

    /** Start pairing: generate a fresh code and show it on the TV. TV-triggered only. */
    fun beginPairing()

    /** Validate the entered code for the current session; on success issue and return a token. */
    fun completePairing(phoneDeviceId: String, phoneName: String, code: String): String?
}

package com.flickbeam.shared

import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

/** Small helpers for finding this device's address on the local network. */
object LocalNetwork {

    /**
     * Best-effort local IPv4 address (e.g. 192.168.1.5) for showing the user where
     * this device is listening. Returns null if it can't be determined.
     */
    fun wifiIpv4(): String? = runCatching {
        Collections.list(NetworkInterface.getNetworkInterfaces())
            .filter { it.isUp && !it.isLoopback }
            .flatMap { Collections.list(it.inetAddresses) }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()
}

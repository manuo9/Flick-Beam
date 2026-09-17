package com.flickbeam.remote.network

/** A FlickBeam TV found on the local network, ready to connect to. */
data class DiscoveredTv(
    val deviceId: String,
    val name: String,
    val host: String,
    val port: Int,
)

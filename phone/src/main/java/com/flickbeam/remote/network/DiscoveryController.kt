package com.flickbeam.remote.network

import kotlinx.coroutines.flow.StateFlow

/**
 * Finds FlickBeam TVs on the local network. The ViewModel observes [devices] and
 * starts/stops discovery instead of touching NsdManager directly.
 */
interface DiscoveryController {
    val devices: StateFlow<List<DiscoveredTv>>
    fun start()
    fun stop()

    /** Drop one entry from the discovered list — e.g. a stale/duplicate the user dismissed. */
    fun forget(deviceId: String)
}

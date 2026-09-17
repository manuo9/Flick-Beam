package com.flickbeam.remote.network

import kotlinx.coroutines.flow.StateFlow

/**
 * Starts and stops the local server that lets a TV with no FlickBeam installed yet
 * fetch the bundled TV apk from this phone (via a browser or a sideload app).
 */
interface InstallController {
    val state: StateFlow<InstallState>
    fun start()
    fun stop()
}

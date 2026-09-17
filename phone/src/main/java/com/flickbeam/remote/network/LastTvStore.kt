package com.flickbeam.remote.network

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers the last TV this phone successfully connected to (its real device id,
 * name, and address), so reopening the app can offer a one-tap reconnect instead of
 * waiting for discovery or retyping an address.
 */
@Singleton
class LastTvStore @Inject constructor(
    @param:ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("last_tv", Context.MODE_PRIVATE)

    // commit(), not apply(): this already runs off the main thread (WebSocket
    // callback), and we want the write on disk immediately, not racing an app kill.
    fun save(tv: DiscoveredTv) {
        prefs.edit()
            .putString(KEY_DEVICE_ID, tv.deviceId)
            .putString(KEY_NAME, tv.name)
            .putString(KEY_HOST, tv.host)
            .putInt(KEY_PORT, tv.port)
            .commit()
    }

    fun load(): DiscoveredTv? {
        val deviceId = prefs.getString(KEY_DEVICE_ID, null) ?: return null
        val name = prefs.getString(KEY_NAME, null) ?: return null
        val host = prefs.getString(KEY_HOST, null) ?: return null
        val port = prefs.getInt(KEY_PORT, -1)
        if (port <= 0) return null
        return DiscoveredTv(deviceId = deviceId, name = name, host = host, port = port)
    }

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_NAME = "name"
        const val KEY_HOST = "host"
        const val KEY_PORT = "port"
    }
}

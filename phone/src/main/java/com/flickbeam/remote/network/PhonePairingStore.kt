package com.flickbeam.remote.network

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the pairing token this phone received from each TV, keyed by the TV's
 * device id, so it can reconnect later without pairing again.
 */
@Singleton
class PhonePairingStore @Inject constructor(
    @param:ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("flickbeam_tokens", Context.MODE_PRIVATE)

    fun token(tvDeviceId: String): String? = prefs.getString(tvDeviceId, null)

    fun save(tvDeviceId: String, token: String) {
        prefs.edit().putString(tvDeviceId, token).apply()
    }
}

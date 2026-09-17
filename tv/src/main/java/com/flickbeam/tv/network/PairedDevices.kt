package com.flickbeam.tv.network

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers the one phone this TV is paired with (single slot — pairing a new
 * phone always replaces the previous one). Persisted so it survives restarts.
 */
@Singleton
class PairedDevices @Inject constructor(
    @param:ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("flickbeam_paired", Context.MODE_PRIVATE)

    fun isValidToken(token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        return token == prefs.getString(KEY_TOKEN, null)
    }

    /** The currently paired phone's display name, or null if nothing is paired. */
    fun pairedDeviceName(): String? = prefs.getString(KEY_NAME, null)

    /** Pair with a phone, replacing whatever was previously paired. Returns the new token. */
    fun pair(phoneDeviceId: String, phoneName: String): String {
        val token = UUID.randomUUID().toString().replace("-", "")
        prefs.edit()
            .putString(KEY_DEVICE_ID, phoneDeviceId)
            .putString(KEY_NAME, phoneName)
            .putString(KEY_TOKEN, token)
            .apply()
        return token
    }

    /** Forget the currently paired phone, if any. */
    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_NAME = "name"
        const val KEY_TOKEN = "token"
    }
}

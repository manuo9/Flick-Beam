package com.flickbeam.remote.network

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stable identity for this phone on the FlickBeam network. The [deviceId] is a UUID
 * generated once and kept for the life of the install, so pairings survive a
 * rename; [deviceName] is just a human label.
 */
@Singleton
class PhoneIdentityStore @Inject constructor(
    @param:ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("flickbeam_identity", Context.MODE_PRIVATE)

    val deviceId: String by lazy {
        prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE_ID, it).apply()
        }
    }

    val deviceName: String = Build.MODEL ?: "Phone"

    val model: String = Build.MODEL ?: "Phone"

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
    }
}

package com.flickbeam.tv.network

import android.content.Context
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stable identity for this TV on the FlickBeam network.
 *
 * The [deviceId] is a UUID generated once and kept for the life of the install, so
 * pairings survive a rename. The [deviceName] is just a human label and may change.
 */
@Singleton
class DeviceIdentityStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences("flickbeam_identity", Context.MODE_PRIVATE)

    val deviceId: String by lazy {
        prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE_ID, it).apply()
        }
    }

    /** A friendly name for this TV: the user-set device name if any, else the model. */
    val deviceName: String
        get() = Settings.Global.getString(context.contentResolver, "device_name")
            ?.takeIf { it.isNotBlank() }
            ?: Build.MODEL

    /** This app's version name, sent to the phone in the handshake. */
    val appVersion: String
        get() = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0"

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
    }
}

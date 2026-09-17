package com.flickbeam.tv.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.flickbeam.shared.NSD_SERVICE_TYPE
import com.flickbeam.shared.PROTOCOL_VERSION
import com.flickbeam.shared.TxtKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Advertises this TV on the local network via NSD so phones can find it without
 * typing an address. Publishes the service type _flickbeam._tcp. with a TXT record
 * carrying the device id, name, and protocol version.
 */
@Singleton
class NsdRegistrar @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val identity: DeviceIdentityStore,
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var listener: NsdManager.RegistrationListener? = null

    /** Register the service on the given (already-bound) server port. */
    fun register(port: Int) {
        unregister()

        val info = NsdServiceInfo().apply {
            serviceName = "FlickBeam ${identity.deviceName}"
            serviceType = NSD_SERVICE_TYPE
            setPort(port)
            setAttribute(TxtKeys.DEVICE_ID, identity.deviceId)
            setAttribute(TxtKeys.NAME, identity.deviceName)
            setAttribute(TxtKeys.PROTOCOL, PROTOCOL_VERSION.toString())
        }

        val registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        listener = registrationListener
        runCatching {
            nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        }
    }

    /** Stop advertising, if currently registered. */
    fun unregister() {
        listener?.let { runCatching { nsdManager.unregisterService(it) } }
        listener = null
    }
}

package com.flickbeam.remote.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.flickbeam.shared.NSD_SERVICE_TYPE
import com.flickbeam.shared.TxtKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Discovers TVs advertising _flickbeam._tcp. on the local network. Resolves are done
 * one at a time, since NsdManager rejects concurrent resolve calls.
 */
@Singleton
class NsdDiscoveryController @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DiscoveryController {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val _devices = MutableStateFlow<List<DiscoveredTv>>(emptyList())
    override val devices: StateFlow<List<DiscoveredTv>> = _devices.asStateFlow()

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    override fun start() {
        if (discoveryListener != null) return
        acquireMulticastLock()
        _devices.value = emptyList()

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                enqueueResolve(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val name = serviceInfo.serviceName ?: return
                _devices.value = _devices.value.filterNot { it.name == name || it.deviceId == name }
            }
        }
        discoveryListener = listener
        runCatching {
            nsdManager.discoverServices(NSD_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    override fun forget(deviceId: String) {
        _devices.value = _devices.value.filterNot { it.deviceId == deviceId }
    }

    override fun stop() {
        discoveryListener?.let { runCatching { nsdManager.stopServiceDiscovery(it) } }
        discoveryListener = null
        resolveQueue.clear()
        resolving = false
        releaseMulticastLock()
    }

    @Synchronized
    private fun enqueueResolve(serviceInfo: NsdServiceInfo) {
        resolveQueue.addLast(serviceInfo)
        resolveNext()
    }

    @Synchronized
    private fun resolveNext() {
        if (resolving) return
        val next = resolveQueue.removeFirstOrNull() ?: return
        resolving = true
        nsdManager.resolveService(next, object : NsdManager.ResolveListener {
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                addResolved(serviceInfo)
                onResolveDone()
            }

            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                onResolveDone()
            }
        })
    }

    @Synchronized
    private fun onResolveDone() {
        resolving = false
        resolveNext()
    }

    private fun addResolved(info: NsdServiceInfo) {
        val host = info.host?.hostAddress ?: return
        val attributes = info.attributes ?: emptyMap()
        val deviceId = attributes[TxtKeys.DEVICE_ID]?.let { String(it) } ?: info.serviceName ?: host
        val name = attributes[TxtKeys.NAME]?.let { String(it) } ?: info.serviceName ?: "TV"
        val tv = DiscoveredTv(deviceId = deviceId, name = name, host = host, port = info.port)

        // Replace any existing entry for the same device id.
        _devices.value = _devices.value.filterNot { it.deviceId == tv.deviceId } + tv
    }

    private fun acquireMulticastLock() {
        if (multicastLock != null) return
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifi.createMulticastLock("flickbeam-discovery").apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }
    }

    private fun releaseMulticastLock() {
        multicastLock?.let { lock ->
            if (lock.isHeld) runCatching { lock.release() }
        }
        multicastLock = null
    }
}

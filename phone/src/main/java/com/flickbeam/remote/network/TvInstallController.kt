package com.flickbeam.remote.network

import android.content.Context
import com.flickbeam.shared.LocalNetwork
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TvInstallController @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : InstallController {

    private val _state = MutableStateFlow<InstallState>(InstallState.Stopped)
    override val state: StateFlow<InstallState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: TvApkServer? = null

    override fun start() {
        if (server != null) return
        _state.value = InstallState.Starting
        scope.launch {
            try {
                val newServer = TvApkServer(context).also { server = it }
                newServer.start()
                _state.value = InstallState.Listening(
                    host = LocalNetwork.wifiIpv4(),
                    port = newServer.listeningPort,
                )
            } catch (e: Exception) {
                stopInternal()
                _state.value = InstallState.Error(e.message ?: "Could not start the server")
            }
        }
    }

    override fun stop() {
        scope.launch {
            stopInternal()
            _state.value = InstallState.Stopped
        }
    }

    private fun stopInternal() {
        server?.let { runCatching { it.stop() } }
        server = null
    }
}

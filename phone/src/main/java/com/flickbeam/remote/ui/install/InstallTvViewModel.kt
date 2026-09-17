package com.flickbeam.remote.ui.install

import androidx.lifecycle.ViewModel
import com.flickbeam.remote.network.InstallController
import com.flickbeam.remote.network.InstallState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Exposes the install-server state to the Install TV screen and relays start/stop to it. */
@HiltViewModel
class InstallTvViewModel @Inject constructor(
    private val install: InstallController,
) : ViewModel() {

    val state: StateFlow<InstallState> = install.state

    fun start() = install.start()

    fun stop() = install.stop()
}

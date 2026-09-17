package com.flickbeam.tv.ui.receive

import androidx.lifecycle.ViewModel
import com.flickbeam.tv.network.ReceiverController
import com.flickbeam.tv.network.ReceiverState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Exposes the receiver state to the Receive screen and relays start/stop to it. */
@HiltViewModel
class ReceiveViewModel @Inject constructor(
    private val receiver: ReceiverController,
) : ViewModel() {

    val state: StateFlow<ReceiverState> = receiver.state

    fun start() = receiver.start()

    fun stop() = receiver.stop()

    fun startPairing() = receiver.startPairing()

    fun disconnect() = receiver.disconnect()
}

package com.flickbeam.tv.ui.receive

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.flickbeam.tv.network.ReceiverState

/**
 * The Receive screen. Starts listening when shown and stops when you leave, and
 * displays where the TV can be reached so a phone can send to it.
 */
@Composable
fun ReceiveScreen(
    onBack: () -> Unit,
) {
    val viewModel: ReceiveViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    // The receiver runs app-wide (started by the activity) so a phone can stay
    // connected across screens; this screen just shows its status.
    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 48.dp, vertical = 32.dp),
    ) {
        Column {
            Text(
                text = "Receive",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 32.sp,
            )
            ReceiverStatus(
                state = state,
                onPairDevice = viewModel::startPairing,
                onDisconnect = viewModel::disconnect,
            )
            Text(
                text = "Press Back to return to Home",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}

@Composable
private fun ReceiverStatus(
    state: ReceiverState,
    onPairDevice: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val onBackground = MaterialTheme.colorScheme.onBackground

    when (state) {
        ReceiverState.Starting, ReceiverState.Stopped -> {
            Text(
                text = "Starting…",
                color = onBackground.copy(alpha = 0.7f),
                fontSize = 18.sp,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        is ReceiverState.Listening -> {
            val address = if (state.host != null) {
                "${state.host}:${state.port}"
            } else {
                "port ${state.port} (couldn't detect IP)"
            }
            Text(
                text = "Ready to receive on this TV",
                color = onBackground,
                fontSize = 20.sp,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = state.deviceName,
                color = onBackground.copy(alpha = 0.8f),
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = "Listening at $address",
                color = onBackground.copy(alpha = 0.8f),
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            when {
                state.pairingCode != null -> {
                    Text(
                        text = "Enter this code on your phone to pair:",
                        color = onBackground,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Text(
                        text = state.pairingCode,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 44.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    if (state.pairingSecondsLeft != null) {
                        Text(
                            text = "Expires in ${state.pairingSecondsLeft}s",
                            color = onBackground.copy(alpha = 0.6f),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                state.connectedPhone != null -> {
                    Text(
                        text = "Connected: ${state.connectedPhone}",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    PairingActionButton(label = "Disconnect", onClick = onDisconnect)
                }

                state.pairedDeviceName != null -> {
                    Text(
                        text = "Paired: ${state.pairedDeviceName} — not connected",
                        color = onBackground.copy(alpha = 0.8f),
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    PairingActionButton(label = "Pair a device", onClick = onPairDevice)
                }

                else -> {
                    Text(
                        text = "Not paired",
                        color = onBackground.copy(alpha = 0.6f),
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    PairingActionButton(label = "Pair a device", onClick = onPairDevice)
                }
            }
            if (state.transferStatus != null) {
                Text(
                    text = state.transferStatus,
                    color = onBackground,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 12.dp),
                )
                if (state.transferProgress != null) {
                    ProgressBar(
                        percent = state.transferProgress,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        text = "${state.transferProgress}%",
                        color = onBackground.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Text(
                    text = "Saved to Download/FlickBeam",
                    color = onBackground.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        is ReceiverState.Error -> {
            Text(
                text = "Couldn't start: ${state.message}",
                color = onBackground,
                fontSize = 18.sp,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun PairingActionButton(
    label: String,
    onClick: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }
    Button(
        onClick = onClick,
        modifier = Modifier
            .padding(top = 16.dp)
            .focusRequester(focusRequester),
    ) {
        Text(label)
    }
}

/** A simple determinate progress bar (tv-material has no progress indicator). */
@Composable
private fun ProgressBar(
    percent: Int,
    modifier: Modifier = Modifier,
) {
    val fraction = (percent.coerceIn(0, 100)) / 100f
    val onBackground = MaterialTheme.colorScheme.onBackground
    Box(
        modifier = modifier
            .fillMaxWidth(0.5f)
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(onBackground.copy(alpha = 0.2f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

package com.flickbeam.remote.ui.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import com.flickbeam.remote.network.ConnectionState
import com.flickbeam.remote.network.DiscoveredTv
import kotlinx.coroutines.delay
import com.flickbeam.remote.network.SendItem
import com.flickbeam.remote.network.SendItemStatus

/**
 * The phone's main screen: find and connect to a TV, then act as its remote.
 */
@Composable
fun ConnectScreen(
    onInstallTv: () -> Unit,
    onMoreApps: () -> Unit,
) {
    val viewModel: ConnectViewModel = hiltViewModel()
    val devices by viewModel.devices.collectAsState()
    val state by viewModel.connectionState.collectAsState()

    // Discover while this screen is shown; stop when it goes away.
    DisposableEffect(Unit) {
        viewModel.startDiscovery()
        onDispose { viewModel.stopDiscovery() }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            Text(
                text = "FlickBeam Remote",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Connect to your TV",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
            )

            val connected = state as? ConnectionState.Connected
            val pairing = state as? ConnectionState.PairingRequired
            val disconnectedByTv = state as? ConnectionState.Disconnected
            when {
                disconnectedByTv != null -> {
                    DisconnectedNotice(
                        tvName = disconnectedByTv.tvName,
                        onReconnect = { viewModel.reconnect() },
                    )
                    DeviceList(
                        devices = devices,
                        connecting = false,
                        onSelect = { viewModel.connect(it) },
                        onForget = { viewModel.forgetDevice(it.deviceId) },
                        modifier = Modifier.padding(top = 20.dp),
                    )
                }

                connected != null -> {
                    val sendItems by viewModel.sendItems.collectAsState()
                    val castError by viewModel.castError.collectAsState()
                    ConnectedHeader(
                        state = connected,
                        onDisconnect = { viewModel.disconnect() },
                    )
                    SendFiles(
                        items = sendItems,
                        onPick = { viewModel.sendFiles(it) },
                        onCastPick = { viewModel.castMedia(it) },
                        onCancelItem = { viewModel.cancelItem(it) },
                        onRetryFailed = { viewModel.retryFailed() },
                        onClear = { viewModel.clearBatch() },
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    if (castError != null) {
                        LaunchedEffect(castError) {
                            delay(4000)
                            viewModel.dismissCastError()
                        }
                        Text(
                            text = castError!!,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    // onCastPick above receives the multi-select list from the picker.
                    RemotePanel(
                        onUp = viewModel::up,
                        onDown = viewModel::down,
                        onLeft = viewModel::left,
                        onRight = viewModel::right,
                        onOk = viewModel::ok,
                        onBack = viewModel::back,
                        onMenu = viewModel::menu,
                        onSendText = viewModel::sendText,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }

                pairing != null -> {
                    PairingEntry(
                        state = pairing,
                        onSubmit = { viewModel.submitPairingCode(it) },
                        onCancel = { viewModel.disconnect() },
                    )
                }

                else -> {
                    ConnectionStatus(state = state)
                    val lastTv = viewModel.lastTv
                    if (state is ConnectionState.Idle && lastTv != null) {
                        ReconnectCard(
                            tvName = lastTv.name,
                            onReconnect = { viewModel.connect(lastTv) },
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    DeviceList(
                        devices = devices,
                        connecting = state is ConnectionState.Connecting,
                        onSelect = { viewModel.connect(it) },
                        onForget = { viewModel.forgetDevice(it.deviceId) },
                        modifier = Modifier.padding(top = 20.dp),
                    )
                    ManualConnect(
                        connecting = state is ConnectionState.Connecting,
                        onConnect = { host, port -> viewModel.connectManual(host, port) },
                        modifier = Modifier.padding(top = 24.dp),
                    )
                    TextButton(
                        onClick = onInstallTv,
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Text("Don't have FlickBeam on your TV? Install it")
                    }
                    TextButton(onClick = onMoreApps) {
                        Text("More by manuo9")
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectedHeader(
    state: ConnectionState.Connected,
    onDisconnect: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Connected to ${state.tvName}",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "FlickBeam ${state.version} — use the controls below",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            OutlinedButton(
                onClick = onDisconnect,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Text("Disconnect")
            }
        }
    }
}

@Composable
private fun ReconnectCard(
    tvName: String,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Reconnect to $tvName",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "You've connected to this TV before.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            Button(
                onClick = onReconnect,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Text("Reconnect")
            }
        }
    }
}

@Composable
private fun DisconnectedNotice(
    tvName: String,
    onReconnect: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Disconnected from $tvName",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "The TV ended this connection.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            Button(
                onClick = onReconnect,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Text("Connect")
            }
        }
    }
}

@Composable
private fun PairingEntry(
    state: ConnectionState.PairingRequired,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var code by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Pair with ${state.tvName}",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Enter the 6-digit code shown on the TV screen.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        OutlinedTextField(
            value = code,
            onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) code = it },
            singleLine = true,
            label = { Text("Code") },
            modifier = Modifier.padding(top = 12.dp),
        )
        state.error?.let {
            Text(
                text = it,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Row(modifier = Modifier.padding(top = 12.dp)) {
            Button(
                onClick = { if (code.length == 6) onSubmit(code) },
                enabled = code.length == 6,
            ) {
                Text("Pair")
            }
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.padding(start = 12.dp),
            ) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun SendFiles(
    items: List<SendItem>,
    onPick: (List<android.net.Uri>) -> Unit,
    onCastPick: (List<android.net.Uri>) -> Unit,
    onCancelItem: (String) -> Unit,
    onRetryFailed: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
    ) { uris -> onPick(uris) }
    val castPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
    ) { uris -> onCastPick(uris) }

    val active = items.any {
        it.status is SendItemStatus.Waiting || it.status is SendItemStatus.Uploading
    }
    val sent = items.count { it.status is SendItemStatus.Sent }
    val failed = items.count { it.status is SendItemStatus.Failed }
    val canceled = items.count { it.status is SendItemStatus.Canceled }
    val planned = items.size - canceled

    Column(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { filePicker.launch("*/*") },
                enabled = !active,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (active) "Sending…" else "Send files")
            }
            Button(
                onClick = { castPicker.launch("*/*") },
                modifier = Modifier.weight(1f),
            ) {
                Text("Cast to TV")
            }
        }

        if (items.isNotEmpty()) {
            Text(
                text = if (active) {
                    "Sending ${sent + failed + 1} of $planned"
                } else {
                    buildString {
                        append("Sent $sent of $planned")
                        if (failed > 0) append(" · $failed failed")
                        if (canceled > 0) append(" · $canceled cancelled")
                    }
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )

            items.forEach { item ->
                SendRow(item = item, onCancel = { onCancelItem(item.id) })
            }

            if (!active) {
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    if (failed > 0) {
                        Button(onClick = onRetryFailed) { Text("Retry failed") }
                    }
                    TextButton(
                        onClick = onClear,
                        modifier = Modifier.padding(start = if (failed > 0) 8.dp else 0.dp),
                    ) {
                        Text("Clear")
                    }
                }
            }
        }
    }
}

@Composable
private fun SendRow(
    item: SendItem,
    onCancel: () -> Unit,
) {
    val (statusText, statusColor) = when (val s = item.status) {
        SendItemStatus.Waiting -> "Waiting" to MaterialTheme.colorScheme.onSurfaceVariant
        is SendItemStatus.Uploading ->
            (if (s.percent >= 0) "${s.percent}%" else "Sending…") to MaterialTheme.colorScheme.primary
        SendItemStatus.Sent -> "Sent ✓" to MaterialTheme.colorScheme.primary
        is SendItemStatus.Failed -> "Failed" to MaterialTheme.colorScheme.error
        SendItemStatus.Canceled -> "Cancelled" to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Text(
            text = item.name,
            fontSize = 14.sp,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = statusText,
            fontSize = 13.sp,
            color = statusColor,
            modifier = Modifier.padding(start = 8.dp),
        )
        if (item.status is SendItemStatus.Waiting) {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.padding(start = 4.dp),
            ) {
                Text("✕")
            }
        }
    }
}

@Composable
private fun RemotePanel(
    onUp: () -> Unit,
    onDown: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onOk: () -> Unit,
    onBack: () -> Unit,
    onMenu: () -> Unit,
    onSendText: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Button(onClick = onUp) { Text("▲") }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 12.dp),
        ) {
            Button(onClick = onLeft) { Text("◀") }
            Button(onClick = onOk) { Text("OK") }
            Button(onClick = onRight) { Text("▶") }
        }
        Button(onClick = onDown) { Text("▼") }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 16.dp),
        ) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            OutlinedButton(onClick = onMenu) { Text("Options") }
        }

        TextSender(
            onSendText = onSendText,
            modifier = Modifier.padding(top = 24.dp),
        )
    }
}

@Composable
private fun TextSender(
    onSendText: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Type on the TV",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Text to send") },
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    if (text.isNotEmpty()) {
                        onSendText(text)
                        text = ""
                    }
                },
                modifier = Modifier.padding(start = 12.dp),
            ) {
                Text("Send")
            }
        }
    }
}

@Composable
private fun ConnectionStatus(state: ConnectionState) {
    when (state) {
        is ConnectionState.Connecting -> {
            Text(
                text = "Connecting to ${state.tvName}…",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        is ConnectionState.Error -> {
            Text(
                text = "Couldn't connect: ${state.message}",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.error,
            )
        }

        else -> Unit
    }
}

@Composable
private fun ManualConnect(
    connecting: Boolean,
    onConnect: (host: String, port: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var address by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier) {
        Text(
            text = "Can't find your TV? Enter its address",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = address,
            onValueChange = {
                address = it
                error = null
            },
            singleLine = true,
            label = { Text("e.g. 192.168.1.5:45077") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        Button(
            onClick = {
                val parsed = parseHostPort(address)
                if (parsed == null) {
                    error = "Enter as ip:port (see the TV's Receive screen)"
                } else {
                    onConnect(parsed.first, parsed.second)
                }
            },
            enabled = !connecting,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text("Connect")
        }
        error?.let {
            Text(
                text = it,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun DeviceList(
    devices: List<DiscoveredTv>,
    connecting: Boolean,
    onSelect: (DiscoveredTv) -> Unit,
    onForget: (DiscoveredTv) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDelete by remember { mutableStateOf<DiscoveredTv?>(null) }

    Column(modifier = modifier) {
        if (devices.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "Searching for TVs on your WiFi…",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            Text(
                text = "Make sure your phone and TV are on the same WiFi network.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            )
        } else {
            Text(
                text = "Available TVs",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            items(devices) { tv ->
                Card(
                    onClick = { if (!connecting) onSelect(tv) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(16.dp),
                        ) {
                            Text(
                                text = tv.name,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "${tv.host}:${tv.port}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        TextButton(onClick = { pendingDelete = tv }) {
                            Text("✕")
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { tv ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this TV?") },
            text = { Text("\"${tv.name}\" will be removed from this list. It'll reappear if it's still on the network.") },
            confirmButton = {
                TextButton(onClick = {
                    onForget(tv)
                    pendingDelete = null
                }) {
                    Text("Yes")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("No")
                }
            },
        )
    }
}

/** Parse "host:port" into a pair, or null if it isn't valid. */
private fun parseHostPort(input: String): Pair<String, Int>? {
    val trimmed = input.trim()
    val separator = trimmed.lastIndexOf(':')
    if (separator <= 0 || separator == trimmed.length - 1) return null
    val host = trimmed.substring(0, separator).trim()
    val port = trimmed.substring(separator + 1).trim().toIntOrNull() ?: return null
    if (host.isEmpty() || port !in 1..65535) return null
    return host to port
}

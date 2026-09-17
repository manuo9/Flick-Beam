package com.flickbeam.remote.ui.install

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flickbeam.remote.network.InstallState

/**
 * Lets a TV with no FlickBeam installed yet fetch the TV apk from this phone. Starts
 * the local server when shown, stops it when you leave.
 */
@Composable
fun InstallTvScreen(
    onBack: () -> Unit,
) {
    val viewModel: InstallTvViewModel = hiltViewModel()
    val state by viewModel.state.collectAsState()

    DisposableEffect(Unit) {
        viewModel.start()
        onDispose { viewModel.stop() }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            Text(
                text = "Install FlickBeam on your TV",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "On your TV, open a browser (or a sideload app like Downloader) " +
                    "and go to the address below.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
            )

            when (val current = state) {
                InstallState.Stopped, InstallState.Starting -> {
                    Text(
                        text = "Starting…",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is InstallState.Listening -> {
                    val address = if (current.host != null) {
                        "http://${current.host}:${current.port}"
                    } else {
                        "port ${current.port} (couldn't detect IP)"
                    }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Address",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = address,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    Text(
                        text = "1. On the TV, open a browser, or install \"Downloader\" from " +
                            "its app store first\n" +
                            "2. Type the address above\n" +
                            "3. Open the downloaded file and allow installs from this source",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }

                is InstallState.Error -> {
                    Text(
                        text = "Couldn't start: ${current.message}",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.padding(top = 24.dp),
            ) {
                Text("Back")
            }
        }
    }
}

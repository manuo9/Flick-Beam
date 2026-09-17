package com.flickbeam.remote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.flickbeam.remote.ui.connect.ConnectScreen
import com.flickbeam.remote.ui.install.InstallTvScreen
import com.flickbeam.remote.ui.moreapps.MoreAppsScreen
import com.flickbeam.remote.ui.theme.FlickBeamRemoteTheme
import dagger.hilt.android.AndroidEntryPoint

/** The phone app's only activity. @AndroidEntryPoint lets its screens get Hilt ViewModels. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhoneApp()
        }
    }
}

private sealed interface PhoneScreen {
    data object Connect : PhoneScreen
    data object InstallTv : PhoneScreen
    data object MoreApps : PhoneScreen
}

@Composable
private fun PhoneApp() {
    var screen by remember { mutableStateOf<PhoneScreen>(PhoneScreen.Connect) }

    FlickBeamRemoteTheme {
        when (screen) {
            PhoneScreen.Connect -> ConnectScreen(
                onInstallTv = { screen = PhoneScreen.InstallTv },
                onMoreApps = { screen = PhoneScreen.MoreApps },
            )

            PhoneScreen.InstallTv -> InstallTvScreen(
                onBack = { screen = PhoneScreen.Connect },
            )

            PhoneScreen.MoreApps -> MoreAppsScreen(
                onBack = { screen = PhoneScreen.Connect },
            )
        }
    }
}

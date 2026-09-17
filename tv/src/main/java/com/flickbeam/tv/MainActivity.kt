package com.flickbeam.tv

import android.os.Bundle
import android.os.SystemClock
import android.view.KeyCharacterMap
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.flickbeam.tv.network.ReceiverController
import com.flickbeam.tv.ui.filemanager.FileManagerScreen
import com.flickbeam.tv.ui.home.HomeScreen
import com.flickbeam.tv.ui.moreapps.MoreAppsScreen
import com.flickbeam.tv.ui.navigation.Screen
import com.flickbeam.tv.ui.player.CastSlideshowScreen
import com.flickbeam.tv.ui.player.VideoPlayerScreen
import com.flickbeam.tv.ui.receive.ReceiveScreen
import com.flickbeam.tv.ui.theme.FlickBeamTheme
import com.flickbeam.shared.Back
import com.flickbeam.shared.ControlMessage
import com.flickbeam.shared.DpadDown
import com.flickbeam.shared.DpadLeft
import com.flickbeam.shared.DpadRight
import com.flickbeam.shared.DpadUp
import com.flickbeam.shared.Menu
import com.flickbeam.shared.Ok
import com.flickbeam.shared.PlayMedia
import com.flickbeam.shared.PlaySlideshow
import com.flickbeam.shared.Text
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The app's only activity. @AndroidEntryPoint lets its screens get Hilt ViewModels. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var receiverController: ReceiverController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FlickBeamApp(mediaEvents = receiverController.remoteEvents)
        }

        // Turn remote-control messages from a connected phone into real key presses,
        // so the phone can navigate the whole app like a D-pad.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                receiverController.remoteEvents.collect { dispatchRemote(it) }
            }
        }
    }

    // Keep the receiver (server + discovery) running while the app is in the foreground,
    // so a phone can stay connected and control any screen.
    override fun onStart() {
        super.onStart()
        receiverController.start()
    }

    override fun onStop() {
        super.onStop()
        receiverController.stop()
    }

    private fun dispatchRemote(message: ControlMessage) {
        when (message) {
            is DpadUp -> sendKey(KeyEvent.KEYCODE_DPAD_UP)
            is DpadDown -> sendKey(KeyEvent.KEYCODE_DPAD_DOWN)
            is DpadLeft -> sendKey(KeyEvent.KEYCODE_DPAD_LEFT)
            is DpadRight -> sendKey(KeyEvent.KEYCODE_DPAD_RIGHT)
            is Ok -> sendKey(KeyEvent.KEYCODE_DPAD_CENTER)
            is Back -> sendKey(KeyEvent.KEYCODE_BACK)
            is Menu -> sendKey(KeyEvent.KEYCODE_MENU)
            is Text -> typeText(message.value)
            else -> Unit
        }
    }

    private fun sendKey(keyCode: Int) {
        val now = SystemClock.uptimeMillis()
        val decor = window?.decorView ?: return
        decor.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        decor.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
    }

    private fun typeText(text: String) {
        if (text.isEmpty()) return
        val decor = window?.decorView ?: return
        val keyMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
        val events = keyMap.getEvents(text.toCharArray()) ?: return
        events.forEach { decor.dispatchKeyEvent(it) }
    }
}

@Composable
private fun FlickBeamApp(mediaEvents: SharedFlow<ControlMessage>) {
    // Simple navigation: just remember which screen we're on.
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    // Media cast from the phone, shown full-screen over whatever screen is active.
    var castMedia by remember { mutableStateOf<PlayMedia?>(null) }
    var castSlideshow by remember { mutableStateOf<PlaySlideshow?>(null) }

    LaunchedEffect(Unit) {
        mediaEvents.collect { message ->
            when (message) {
                is PlayMedia -> {
                    castSlideshow = null
                    castMedia = message
                }
                is PlaySlideshow -> {
                    castMedia = null
                    castSlideshow = message
                }
                else -> Unit
            }
        }
    }

    FlickBeamTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            when (currentScreen) {
                is Screen.Home -> HomeScreen(
                    onTileSelected = { tile ->
                        currentScreen = when (tile.id) {
                            "file_manager" -> Screen.FileManager
                            "receive" -> Screen.Receive
                            "more_apps" -> Screen.MoreApps
                            else -> Screen.Home
                        }
                    },
                )

                is Screen.FileManager -> FileManagerScreen(
                    onExitToHome = { currentScreen = Screen.Home },
                )

                is Screen.Receive -> ReceiveScreen(
                    onBack = { currentScreen = Screen.Home },
                )

                is Screen.MoreApps -> MoreAppsScreen(
                    onBack = { currentScreen = Screen.Home },
                )
            }

            castMedia?.let { media ->
                VideoPlayerScreen(
                    uri = media.url,
                    title = media.title,
                    subtitleUri = null,
                    onExit = { castMedia = null },
                    isAudio = media.mimeType.orEmpty().startsWith("audio/"),
                )
            }

            castSlideshow?.let { slideshow ->
                CastSlideshowScreen(
                    urls = slideshow.urls,
                    onExit = { castSlideshow = null },
                )
            }
        }
    }
}

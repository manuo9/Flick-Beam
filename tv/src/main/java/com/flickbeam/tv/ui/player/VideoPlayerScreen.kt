package com.flickbeam.tv.ui.player

import android.net.Uri
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

/**
 * Full-screen video player built on ExoPlayer, driven entirely by the D-pad (OK to
 * play/pause, Left/Right to seek, Back to exit). We turn off PlayerView's touch
 * controls and draw our own auto-hiding overlay, so a TV remote — or the phone's
 * Remote panel, which sends the same key events — can control playback.
 */
@OptIn(markerClass = [UnstableApi::class])
@Composable
fun VideoPlayerScreen(
    uri: String,
    title: String,
    subtitleUri: String?,
    onExit: () -> Unit,
    isAudio: Boolean = false,
) {
    val context = LocalContext.current
    BackHandler(onBack = onExit)

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val item = MediaItem.Builder().setUri(uri).setMediaId(title)
            if (subtitleUri != null) {
                val subtitle = MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitleUri))
                    .setMimeType(subtitleMimeType(subtitleUri))
                    .setLanguage("und")
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
                item.setSubtitleConfigurations(listOf(subtitle))
            }
            setMediaItem(item.build())
            prepare()
            playWhenReady = true
        }
    }

    var playing by remember { mutableStateOf(true) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Keep the progress bar and time in sync.
    LaunchedEffect(Unit) {
        while (true) {
            position = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    // Hide the controls a few seconds after they were shown, while playing. For audio
    // there's nothing to watch, so leave them up.
    LaunchedEffect(controlsVisible, playing, isAudio) {
        if (controlsVisible && playing && !isAudio) {
            delay(3000)
            controlsVisible = false
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                controlsVisible = true
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.Spacebar, Key.MediaPlayPause -> {
                        exoPlayer.playWhenReady = !exoPlayer.playWhenReady
                        true
                    }
                    Key.DirectionLeft, Key.MediaRewind -> {
                        exoPlayer.seekTo((exoPlayer.currentPosition - SEEK_STEP_MS).coerceAtLeast(0L))
                        true
                    }
                    Key.DirectionRight, Key.MediaFastForward -> {
                        val end = exoPlayer.duration
                        val target = exoPlayer.currentPosition + SEEK_STEP_MS
                        exoPlayer.seekTo(if (end > 0) target.coerceAtMost(end) else target)
                        true
                    }
                    else -> false
                }
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (isAudio) {
            Text(
                text = "♪",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 96.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (controlsVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 24.dp),
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 16.sp,
                    maxLines = 1,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(
                        text = if (playing) "⏸" else "▶",
                        color = Color.White,
                        fontSize = 20.sp,
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.White.copy(alpha = 0.3f)),
                    ) {
                        val fraction =
                            if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White),
                        )
                    }
                    Text(
                        text = "${formatTime(position)} / ${formatTime(duration)}",
                        color = Color.White,
                        fontSize = 13.sp,
                    )
                }
                Text(
                    text = "OK play/pause  ·  ◀ ▶ seek 10s  ·  Back exit",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

private const val SEEK_STEP_MS = 10_000L

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private fun subtitleMimeType(uri: String): String = when {
    uri.endsWith(".vtt", ignoreCase = true) -> MimeTypes.TEXT_VTT
    uri.endsWith(".ass", ignoreCase = true) || uri.endsWith(".ssa", ignoreCase = true) ->
        MimeTypes.TEXT_SSA
    else -> MimeTypes.APPLICATION_SUBRIP
}

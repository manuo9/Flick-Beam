package com.flickbeam.tv.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Shows a photo slideshow cast from the phone (images streamed by URL). Auto-advances
 * for multiple photos; Left/Right steps manually, OK toggles auto-advance, Back exits.
 * Mirrors the local image gallery so cast and on-TV photos feel the same.
 */
@Composable
fun CastSlideshowScreen(
    urls: List<String>,
    onExit: () -> Unit,
) {
    if (urls.isEmpty()) {
        BackHandler(onBack = onExit)
        return
    }

    val pagerState = rememberPagerState(initialPage = 0) { urls.size }
    val scope = rememberCoroutineScope()
    var slideshowOn by remember { mutableStateOf(urls.size > 1) }

    BackHandler(onBack = onExit)

    LaunchedEffect(slideshowOn, urls.size) {
        if (slideshowOn && urls.size > 1) {
            while (true) {
                delay(SLIDESHOW_INTERVAL_MS)
                pagerState.animateScrollToPage((pagerState.currentPage + 1) % urls.size)
            }
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
                when (event.key) {
                    Key.DirectionLeft -> {
                        if (pagerState.currentPage > 0) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        }
                        true
                    }
                    Key.DirectionRight -> {
                        if (pagerState.currentPage < urls.size - 1) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                        true
                    }
                    Key.DirectionCenter, Key.Enter -> {
                        if (urls.size > 1) slideshowOn = !slideshowOn
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        HorizontalPager(
            state = pagerState,
            key = { urls[it] },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            AsyncImage(
                model = urls[page],
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (urls.size > 1) {
            Text(
                text = buildString {
                    append("${pagerState.currentPage + 1} of ${urls.size}")
                    if (slideshowOn) append("  •  ▶ Slideshow")
                },
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp),
            )
        }
    }
}

private const val SLIDESHOW_INTERVAL_MS = 4000L

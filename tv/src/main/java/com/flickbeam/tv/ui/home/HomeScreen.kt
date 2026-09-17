package com.flickbeam.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** Home screen: a grid of the main features you move through with the D-pad. */
@Composable
fun HomeScreen(
    onTileSelected: (FeatureTile) -> Unit = {},
) {
    // Grab focus onto the first tile when this screen appears, so the D-pad works
    // right away — including when returning here from another screen.
    val firstTileFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // Wait one frame so the first tile is laid out before requesting focus.
        withFrameNanos {}
        runCatching { firstTileFocus.requestFocus() }
    }

    // Extra padding — TVs often cut off the screen edges.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 48.dp, vertical = 32.dp),
    ) {
        Column {
            Text(
                text = "FlickBeam",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(top = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                itemsIndexed(homeFeatureTiles) { index, tile ->
                    FeatureTileCard(
                        tile = tile,
                        onClick = { onTileSelected(tile) },
                        modifier = if (index == 0) {
                            Modifier.focusRequester(firstTileFocus)
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureTileCard(
    tile: FeatureTile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The TV Card highlights itself when focused, so it's clear where you are.
    Card(
        onClick = onClick,
        modifier = modifier.aspectRatio(16f / 9f),
        colors = CardDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                text = tile.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = tile.description,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                fontSize = 14.sp,
            )
        }
    }
}

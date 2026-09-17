package com.flickbeam.remote.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Same purple-on-white palette as the TV app's Theme.kt, so both apps read as one brand.
private val Purple = Color(0xFF6650A4)
private val DeepPurple = Color(0xFF4527A0)
private val Lavender = Color(0xFFEDE7F6)

private val FlickBeamRemoteColorScheme = lightColorScheme(
    primary = Purple,
    onPrimary = Color.White,
    background = Color.White,
    onBackground = Purple,
    surface = Lavender,
    onSurface = DeepPurple,
    surfaceVariant = Lavender,
    onSurfaceVariant = Purple,
)

@Composable
fun FlickBeamRemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FlickBeamRemoteColorScheme,
        content = content,
    )
}

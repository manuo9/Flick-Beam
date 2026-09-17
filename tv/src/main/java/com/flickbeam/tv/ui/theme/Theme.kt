package com.flickbeam.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.lightColorScheme

// Purple-on-white to match the phone companion app.
private val Purple = Color(0xFF6650A4)
private val DeepPurple = Color(0xFF4527A0)
private val Lavender = Color(0xFFEDE7F6)

private val FlickBeamColorScheme = lightColorScheme(
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
fun FlickBeamTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FlickBeamColorScheme,
        content = content,
    )
}

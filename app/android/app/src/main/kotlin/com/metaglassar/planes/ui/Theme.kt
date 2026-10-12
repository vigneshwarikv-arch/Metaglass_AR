package com.metaglassar.planes.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Same palette as the website: clear-sky blue + anti-collision-beacon orange.
val Beacon = Color(0xFFE0561B)
val BeaconDark = Color(0xFFFF7A3D)

private val Light =
    lightColorScheme(
        primary = Color(0xFF1D5FD1),
        onPrimary = Color.White,
        secondary = Beacon,
        background = Color(0xFFF6F7F9),
        surface = Color.White,
        surfaceVariant = Color(0xFFEDF0F4),
        onSurface = Color(0xFF0E1726),
        onSurfaceVariant = Color(0xFF5A6577),
        outline = Color(0xFFE2E6EC),
    )

private val Dark =
    darkColorScheme(
        primary = Color(0xFF6EA2FF),
        onPrimary = Color(0xFF0B1220),
        secondary = BeaconDark,
        background = Color(0xFF0B1220),
        surface = Color(0xFF121B2D),
        surfaceVariant = Color(0xFF1A2540),
        onSurface = Color(0xFFE8EDF5),
        onSurfaceVariant = Color(0xFF9AA6BA),
        outline = Color(0xFF22304A),
    )

@Composable
fun PlanesTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

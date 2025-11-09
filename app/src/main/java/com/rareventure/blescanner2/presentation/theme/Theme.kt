package com.rareventure.blescanner2.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Typography

// --- Custom Dark Theme for Readability ---
private val DarkColorPalette = Colors(
    primary = Color(0xFF58A6FF),      // A muted, readable blue for buttons
    background = Color(0xFF121212),  // A standard dark theme background
    surface = Color(0xFF333333),     // For surfaces like Chips
    error = Color(0xFFCF6679),       // A less vibrant red for the stop button
    onPrimary = Color.Black,
    onBackground = Color(0xFFE0E0E0),// Light grey text on background for high contrast
    onSurface = Color(0xFFE0E0E0),   // Light grey text on surfaces
    onError = Color.Black
)

@Composable
fun BLEScannerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colors = DarkColorPalette,
        typography = Typography(),
        content = content
    )
}

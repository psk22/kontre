package com.reelshield.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SuperlistDarkColorScheme = darkColorScheme(
    primary = CoralEmber,
    background = AubergineCanvas,
    surface = ElevatedPlum,
    surfaceVariant = ElevatedPlumDark,
    onPrimary = PureWhite,
    onBackground = PureWhite,
    onSurface = PureWhite,
    outline = WhisperBorder
)

@Composable
fun KontreTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SuperlistDarkColorScheme,
        content = content
    )
}

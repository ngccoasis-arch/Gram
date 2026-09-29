package com.gram.client.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GramColors = darkColorScheme(
    primary = Color(0xFF6AD5FF),
    onPrimary = Color(0xFF001F2A),
    secondary = Color(0xFF9CB8C5),
    background = Color(0xFF080B10),
    surface = Color(0xFF10151D),
    surfaceVariant = Color(0xFF19222D),
    error = Color(0xFFFF6B6B),
)

@Composable
fun GramTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = GramColors, content = content)
}

package com.niko.spesaradar.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    val Teal = Color(0xFF0F6E56)
    val TealLight = Color(0xFF9FE1CB)
    val Mint = Color(0xFFE1F5EE)
    val Amber = Color(0xFFBA7517)
    val AmberLight = Color(0xFFFAEEDA)
    val Blue = Color(0xFF378ADD)
    val Red = Color(0xFFA32D2D)
    val Gray = Color(0xFF888780)
}

private val Light = lightColorScheme(
    primary = Palette.Teal,
    onPrimary = Color.White,
    primaryContainer = Palette.Mint,
    onPrimaryContainer = Color(0xFF04342C),
    secondary = Palette.Amber,
    secondaryContainer = Palette.AmberLight,
    onSecondaryContainer = Color(0xFF412402),
    background = Color(0xFFFAFAF7),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF1EFE8),
    error = Palette.Red
)

private val Dark = darkColorScheme(
    primary = Palette.TealLight,
    onPrimary = Color(0xFF04342C),
    primaryContainer = Color(0xFF085041),
    onPrimaryContainer = Palette.Mint,
    secondary = Color(0xFFFAC775),
    secondaryContainer = Color(0xFF633806),
    onSecondaryContainer = Palette.AmberLight
)

@Composable
fun SpesaRadarTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

package com.twocents.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Background = AppDesignTokens.Background
val Surface = AppDesignTokens.Surface
val Gold = AppDesignTokens.Gold
val GoldDark = AppDesignTokens.GoldDark
val Emerald = AppDesignTokens.Emerald
val Rose = AppDesignTokens.Rose

private val DarkColors = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF0F0E0A),
    secondary = GoldDark,
    background = Background,
    onBackground = Color.White,
    surface = Surface,
    onSurface = Color.White,
)

@Composable
fun TwoCentsTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}

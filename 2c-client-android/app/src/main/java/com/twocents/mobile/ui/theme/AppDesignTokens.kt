package com.twocents.mobile.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Stable visual tokens. Values mirror the pre-refactor literals exactly. */
internal object AppDesignTokens {
    val Background = Color(0xFF0A0907)
    val Surface = Color(0xFF11100D)
    val RaisedSurface = Color(0xFF141410)
    val Gold = Color(0xFFC8A44D)
    val GoldDark = Color(0xFFB8943D)
    val Emerald = Color(0xFF34D399)
    val Rose = Color(0xFFFB7185)
    val StrongRose = Color(0xFFF43F5E)
    val Hairline = Color.White.copy(alpha = 0.07f)
    val Border = Color.White.copy(alpha = 0.08f)
    val MutedText = Color.White.copy(alpha = 0.52f)

    val HairlineWidth = 1.dp
    val CardRadius = 16.dp
    val LargeCardRadius = 18.dp
}

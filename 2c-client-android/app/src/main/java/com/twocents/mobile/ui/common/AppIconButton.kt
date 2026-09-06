package com.twocents.mobile.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp

/**
 * Shared circular action surface. Callers provide all dimensions and colors so
 * migrating an existing button cannot silently change its appearance.
 */
@Composable
internal fun AppIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp,
    iconSize: Dp,
    tint: Color,
    background: Color,
    borderWidth: Dp,
    borderColor: Color,
    enabled: Boolean = true,
    hapticIntent: HapticIntent? = HapticIntent.Navigate,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val view = LocalView.current
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .border(borderWidth, borderColor, CircleShape)
            .pressScale(rememberPressScale(interaction))
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = { AppHaptics.perform(view, hapticIntent); onClick() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}

package com.twocents.mobile.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twocents.mobile.ui.common.AppHaptics
import com.twocents.mobile.ui.common.HapticIntent
import com.twocents.mobile.ui.common.AppIconButton
import com.twocents.mobile.ui.common.pressScale
import com.twocents.mobile.ui.common.rememberPressScale
import com.twocents.mobile.ui.feed.HeaderActionButton
import com.twocents.mobile.ui.feed.HeaderLogoButton
import com.twocents.mobile.ui.feed.PROFILE_ICON_RES
import com.twocents.mobile.ui.feed.PROFILE_ICON_SELECTED_RES
import com.twocents.mobile.ui.theme.Background

internal val HeaderBorder = Color.White.copy(alpha = 0.08f)
internal val ActionSurface = Color.White.copy(alpha = 0.045f)

@Composable
internal fun StandardPageTopBar(
    title: String,
    onPressLogo: () -> Unit,
    onNavigateProfile: () -> Unit,
    profileSelected: Boolean = false,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(57.dp)
            .background(Background)
            .drawBehind {
                drawLine(HeaderBorder, androidx.compose.ui.geometry.Offset(0f, size.height - 1f), androidx.compose.ui.geometry.Offset(size.width, size.height - 1f), 1f)
            }
            .padding(start = 12.dp, end = 12.dp),
    ) {
        Box(modifier = Modifier.align(Alignment.CenterStart)) {
            HeaderLogoButton(onClick = onPressLogo)
        }
        Text(
            text = title,
            color = Color.White,
            fontSize = 16.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.align(Alignment.Center),
        )
        Row(
            modifier = Modifier.align(Alignment.CenterEnd).offset(x = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions()
            HeaderActionButton(
                imageRes = PROFILE_ICON_RES,
                pressedImageRes = PROFILE_ICON_SELECTED_RES,
                iconSize = 29.dp,
                fallback = Icons.Default.Person,
                contentDescription = "Profile",
                selected = profileSelected,
                hapticIntent = HapticIntent.Open,
                onClick = onNavigateProfile,
            )
        }
    }
}

@Composable
internal fun HeaderActionCard(
    label: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier.fillMaxSize().clip(RoundedCornerShape(15.dp)).background(ActionSurface)
            .border(1.dp, HeaderBorder, RoundedCornerShape(15.dp))
            .pressScale(rememberPressScale(interaction))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 7.dp),
            maxLines = 1,
        )
    }
}

@Composable
internal fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp = 34.dp,
    iconSize: Dp = 16.dp,
    tint: Color = Color.White.copy(alpha = 0.85f),
    background: Color = Color.White.copy(alpha = 0.06f),
    borderColor: Color = Color.White.copy(alpha = 0.1f),
    enabled: Boolean = true,
    hapticIntent: HapticIntent? = HapticIntent.Navigate,
) {
    AppIconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        size = size,
        iconSize = iconSize,
        tint = tint,
        background = background,
        borderWidth = 1.dp,
        borderColor = borderColor,
        enabled = enabled,
        hapticIntent = hapticIntent,
    )
}
